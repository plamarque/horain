package com.horain.chat

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.horain.agent.AgentTurnService
import com.horain.model.AgentTurn
import com.horain.llm.ChatMessage
import com.horain.llm.LlmClient
import com.horain.llm.LlmProperties
import com.horain.llm.LlmResponse
import com.horain.llm.RoutingLlmClient
import com.horain.llm.StreamingLlmClient
import com.horain.llm.ToolCallRequest
import com.horain.llm.ToolCallResult
import com.horain.llm.ToolDefinition
import com.horain.model.Memory
import com.horain.service.MemoryService
import com.horain.time.ServerTemporalContextService
import com.horain.observability.AgentTraceSink
import com.horain.observability.ReasoningPhase
import com.horain.observability.ToolCallTrace
import com.horain.observability.TurnCompletedEvent
import com.horain.tools.ToolExecutorService
import com.horain.tools.ToolRegistry
import io.micrometer.tracing.Tracer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.LinkedHashMap
import java.util.UUID
import java.util.function.Consumer
import kotlin.math.min

/**
 * Orchestrates the chat flow: receives user message, calls LLM with tools,
 * executes requested tools, loops until final response.
 */
@Service
class LlmChatService(
    private val llmClient: LlmClient,
    private val toolRegistry: ToolRegistry,
    private val toolExecutor: ToolExecutorService,
    private val memoryService: MemoryService,
    private val objectMapper: ObjectMapper,
    @Value("\${horain.mass-delete-limit:5}") private val massDeleteLimit: Int,
    private val agentTurnService: AgentTurnService,
    private val llmProperties: LlmProperties,
    private val serverTemporalContextService: ServerTemporalContextService,
    private val agentTraceSink: AgentTraceSink,
    private val tracer: ObjectProvider<Tracer>
) {

    companion object {
        private val log = LoggerFactory.getLogger(LlmChatService::class.java)
        private const val MAX_TOOL_ITERATIONS = 10
        private const val CONTEXT_ENTRIES_MAX = 20
        private const val CONTEXT_PROJECTS_MAX = 10
        private const val MEMORIES_INJECT_MAX = 30

        private val SYSTEM_PROMPT = """

            You are Horain, a personal time logging assistant. You help users log time spent on projects and answer questions about their tracked time.

            ## Data and tools
            - Use the available tools to read and write data. You never guess data.
            - The [Memories] section (when present) contains stored facts about the user (preferences, project disambiguation, typos). Use them to personalize responses and avoid re-asking; e.g. when the user says "HatCast" and a memory says they mean HatCast V2, log on that project directly. You can store new facts with store_memory after a confirmed disambiguation or explicit preference, and forget with forget_memory when the user asks.
            - projectId: sum_time_by_project, get_time_logs_for_period, create_time_log accept EITHER a project UUID OR a project name (e.g. "HatCast"). If you pass a name, the system resolves it automatically.
            - The "## Current server time" block at the **end** of this system message is refreshed every request. Use its values (iso, startOfToday, endOfToday, startOfWeek, endOfWeek, startOfMonth, endOfMonth) for "today", "this week", "this month", and for sum_*_for_period, get_time_logs_for_period, get_time_aggregated_for_chart bounds. Do **not** call get_current_datetime unless that block is missing or the user explicitly needs a fresh server time read.

            ## Logging time (create_time_log, project matching)
            - Extract project name, duration (in minutes), and optional note from the user's message.
            - Duration: "une demi heure" / "demi-heure" / "half hour" = 30 min. "1h30" = 90 min. Support French and English.
            - Multiple entries in one message: process each separately. E.g. "2H sur Horain et une demi heure sur festibask" = two create_time_log calls. Follow-ups like "et une demi heure sur festibask" are additional entries; use conversation history for context.
            - Search for projects by name before creating or logging. If multiple projects match, ask which one (e.g. "I found two similar projects: X and Y. Which one?"). NEVER call create_time_log until the user has chosen. When the user replies with their choice (e.g. V1, V2, or the full project name), interpret it as selection and immediately call create_time_log with that project.
            - If the project doesn't exist: first check if search_project returned close_matches. If yes, propose the first close match: "I don't have a project named X. Did you mean [close_match_name]? Should I log Y minutes on [close_match_name]?" and wait for confirmation. If the user confirms (yes/ok/that one/correct), call create_time_log with that project. Only if there are no close_matches or the user says no (e.g. "no, create a new one"), then propose: "I don't know a project named X yet. Should I create it and log Y minutes?" and wait for explicit confirmation (yes/ok/create/go ahead). When the user replies yes, ok, create, or go ahead to that creation proposal, immediately call create_project then create_time_log with the project name and duration from the prior message.
            - When the user confirms a close_match (e.g. "yes Horain", "oui Horain" after you proposed "Did you mean Horain?"): use that project's id or exact name from close_matches for ALL subsequent tool calls in that turn; never pass the user's original typo (e.g. "Horian") to get_time_logs_for_period or sum_time_by_project.
            - If the user mentions a project but does not specify a duration (e.g. "I worked on X all morning"), do NOT infer a duration. Ask: "Can you estimate the duration?" or "How long did you work on it?"

            ## Projects (update, delete)
            - When the user asks to rename, edit, or change a project (name or description): use update_project with the project id (or name) and the new name and/or description. If ambiguous (multiple matches), ask which project.
            - When the user asks to delete or remove a project: use delete_project. If delete_project returns an error (project has time log entries), inform the user of the entry count and ask explicitly whether they want to delete all entries first, then the project. NEVER automatically chain delete_time_log calls without user confirmation. If ambiguous which project, ask which one.

            ## Time queries and listing entries
            - For time queries ("combien de temps?", "how many hours?", "what did I do?"): use the Current server time block for bounds, then sum_time_for_period or get_time_logs_for_period. For "this week", "today", or "this month", use startOfWeek/endOfWeek, startOfToday/endOfToday, or startOfMonth/endOfMonth from that block.
            - For listing entries ("les entrées", "détails", "qu'est-ce que j'ai logué?", "what did I log?", "show me my entries"): call get_time_logs_for_period or get_recent_logs, then MUST call propose_entries with the full time_logs array (including id, projectId, projectName, and when present activityTypeCode, activityTypeLabel, dailyRateCents for each entry). Do NOT summarize entries in your text; the UI displays them in a table. Keep your text response brief (e.g. "Here are your entries for this week.").
            - When the user asks to find entries by keyword or phrase (e.g. "find logs with backend", "entries mentioning Horain", "recherche pie chart", "entrées qui contiennent X"): call search_time_logs with the query, then propose_entries with the returned time_logs. Do NOT use get_time_logs_for_period or get_recent_logs for keyword search.
            - When listing entries for a specific project (e.g. "list entries for Horain"): if the user did not specify a date range, use the Current server time block and pick a period that includes recent activity (e.g. startOfMonth to endOfMonth, or startOfWeek to endOfWeek). Do NOT use an arbitrary past period (e.g. October); use the current month or week so that recent entries are included.
            - When the user asks to change/update/toggle "toutes les activités" or "all activities" for a project (e.g. "bascule toutes les activités associées à eXo en facturable") WITHOUT specifying a period: use endOfMonth from the Current server time block, then get_time_logs_for_period with start = "2000-01-01T00:00:00Z" and end = that endOfMonth, and projectId = the project. Do NOT assume or use an arbitrary month (e.g. October); if no period was specified, use this all-time range.
            - When the user asks which activities or entries correspond to a date or period ("quelles activités?", "détail", "les entrées du lundi 2", "what entries on March 2?"): call get_time_logs_for_period with that exact day's start and end, then list the entries or call propose_entries. Never say "no activities" or "aucune activité" without having called get_time_logs_for_period for that exact period first.

            ## Mass operations (guards)
            - MASS DELETION GUARD: Before deleting more than 3 entries in one turn, you MUST ask for explicit confirmation: "You are about to delete N entries. Please confirm (yes / delete all)." Do NOT execute the deletions until the user has clearly confirmed. Never suggest or perform mass deletions without confirmation.
            - MASS UPDATE GUARD: When the user asks to apply a change to "all" or "toutes" activities for a project (e.g. set all to billable, "bascule toutes en facturable"), you MUST first fetch the entries (using the all-time range above if no period was specified), then state the ACTION you will perform and ask for confirmation. The confirmation must describe the action requested, not the current state: e.g. "You are about to set all N entries to billable. Confirm? (yes / go ahead)". Never say "set X to billable and Y to non-billable" when the user asked to set all to billable—that describes the current mix, not the action. Do NOT call update_time_log in a loop until the user has confirmed.

            ## Charts and analytics
            - For analytical questions ("sur quoi j'ai travaillé cette semaine?", "what did I work on this week?", "répartition par projet", "hours per project", "un chart"): use the Current server time block for start/end, then get_time_aggregated_for_chart with groupBy "day_and_project" (stacked bar by project per day) or "day_and_billable" (stacked bar billable vs non-billable per day; use for "heures facturables vs non facturables par jour") or "project_only" (pie), then propose_chart with chartType "stackedBar", "pie", or "bar". Include a short text summary. You MUST call propose_chart to show a chart; never output markdown image syntax like ![...](url).
            - For "heures facturables du [date]" or "billable hours on [date]": use sum_billable_time_for_period with start and end of that calendar day in UTC (e.g. 2025-03-02T00:00:00Z to 2025-03-03T00:00:00Z for March 2). The numbers must match the chart if the chart was built with day_and_billable for the same period.
            - Occupancy rate (taux d'occupation): formula is (total hours from sum_time_for_period) / (days in period × base hours per day) × 100. Count calendar days: "2 weeks" = 14 days, "this week" = 7 days. Example: 2 weeks at 7 h/day → denominator = 14 × 7 = 98 hours. Always show the calculation.

            ## Entry edits and deletes
            - When the user asks to edit, change, or correct an entry (e.g. "change duration to 45 min", "update the note", "fix that entry"): ALWAYS use update_time_log with the entry id and the new values. NEVER use create_time_log for modifying an existing entry. NEVER use create_time_log + delete_time_log to simulate an update.
            - When the user asks to edit/change/correct an entry but no context entries are provided: first call get_recent_logs or get_time_logs_for_period (use the Current server time block for "today"/"this week" bounds) to fetch entries, identify which entry to modify, then call update_time_log with its id.
            - When the user asks to delete or remove an entry: use delete_time_log with the entry id. When context entries are provided (user has selected entries), those entries include their ids; use them for edit/delete.

            ## Activity types (natures + TJM)
            - When the user asks to create, update, delete, or list activity types or daily rates (e.g. "add a nature CONSULT at 800 euros per day", "change DEV rate to 450", "list my natures", "delete the MARK nature"), use list_activity_types, create_activity_type, update_activity_type, or delete_activity_type. Rates are in euros; pass dailyRateCents (e.g. 40000 for 400 €).
            - When logging time, if the user mentions an activity nature (e.g. "2h de dev sur Horain", "30 min d'expertise IA sur X"), call list_activity_types to get available codes and labels, then match the user's wording (dev/développement/code → DEV, IA/expertise IA → AI, marketing → MARK, etc.) and pass activityTypeCode in create_time_log. If no match is clear, you may omit activityTypeCode; the user can set it later in the UI.

            ## Response and formatting
            - Once you have the tool results needed to answer, respond with a clear text summary. Do NOT make additional tool calls.
            - When a tool returns an error (e.g. {"error": "..."}), inform the user clearly. Never invent or assume data when tools fail.
            - When tools return empty data (no entries, no logs for a period): say "no entries", "0 hours", "aucun" or equivalent. Never invent or fabricate totals.
            - Be concise and friendly. Confirm actions clearly. When the user makes a correction: they refer to the previous action. Keep the same project; only change what they correct.
            - For calculations and numbers in your reply, use plain text only. Do NOT use LaTeX or math markup (no \\frac, \\times, \\text, etc.). Use Unicode symbols if needed (×, ÷, =) and write formulas like "22,5 / 8 = 2,8125 jours" or "2,75 × 600 = 1650 euros" so the message displays correctly in the chat.
            - When using Markdown **bold**, keep a normal space between closing ** and the next letter, digit, «, or ( (e.g. **vos** 51 entrées, **36 h 15 min**), and between plain text and opening ** when the bold part starts with a letter or digit (e.g. avec **36 h**). Never glue tokens like **vos**51 or **minutes**Vous.
            
        """.trimIndent()
    }

    fun chat(
        userMessage: String,
        history: List<ChatHistoryEntry>?,
        contextEntries: List<Map<String, Any?>>?,
        contextProjects: List<Map<String, Any?>>?,
        /** Same id for all turns in one UI conversation; null starts a new thread. */
        conversationId: UUID? = null
    ): ChatResponse {
        try {
            val startTime = System.currentTimeMillis()
            val messages = mutableListOf<ChatMessage>()
            val systemPrompt = buildFullSystemPrompt(contextEntries, contextProjects)
            messages.add(ChatMessage.system(systemPrompt))
            if (!history.isNullOrEmpty()) {
                for (e in history) {
                    when (e.role.lowercase()) {
                        "user" -> messages.add(ChatMessage.user(e.content))
                        "assistant" -> messages.add(ChatMessage.assistant(e.content))
                    }
                }
            }
            messages.add(ChatMessage.user(userMessage))

            val tools = toolRegistry.getAllTools()
            val toolCallsExecuted = mutableListOf<ToolCallRecord>()
            val reasoningPhases = mutableListOf<ReasoningPhase>()

            for (iterations in 0 until MAX_TOOL_ITERATIONS) {
                val response = llmClient.chat(messages, tools)

                if (!response.reasoningSummary.isNullOrBlank()) {
                    reasoningPhases.add(ReasoningPhase(response.reasoningSummary.trim(), null))
                }

                if (iterations > 0) {
                    log.debug("Tool iteration {} for message: {}", iterations, userMessage)
                }

                if (!response.hasToolCalls()) {
                    val chartData = extractChartDataFromToolCalls(toolCallsExecuted)
                    val timeLogsData = extractTimeLogsFromToolCalls(toolCallsExecuted)
                    val data = hashMapOf<String, Any?>()
                    if (chartData != null) data["chart"] = chartData
                    if (timeLogsData != null) data["timeLogs"] = timeLogsData
                    val assistantMessage =
                        if (!response.content.isNullOrBlank()) {
                            response.content
                        } else {
                            log.warn("LLM returned empty content (check backend logs for API or model errors)")
                            "I'm sorry, I couldn't generate a response."
                        }
                    return persistTurnAndBuildResponse(
                        userMessage,
                        assistantMessage,
                        toolCallsExecuted,
                        if (data.isEmpty()) null else data,
                        history,
                        contextEntries,
                        startTime,
                        false,
                        conversationId,
                        reasoningPhases
                    )
                }

                messages.add(
                    ChatMessage.assistantWithToolCalls(
                        response.content ?: "",
                        response.toolCalls!!
                    )
                )

                for (tc in response.toolCalls!!) {
                    val result =
                        if (tc.name == ToolRegistry.DELETE_TIME_LOG) {
                            val deleteCount = toolCallsExecuted.count { r -> r.name == ToolRegistry.DELETE_TIME_LOG }
                            if (deleteCount >= massDeleteLimit) {
                                ToolCallResult(
                                    tc.id,
                                    "{\"error\":\"Mass deletion guard: max $massDeleteLimit " +
                                        "entries per turn. Ask the user to confirm before deleting more, then proceed in a follow-up message.\"}"
                                )
                            } else {
                                toolExecutor.execute(tc)
                            }
                        } else {
                            toolExecutor.execute(tc)
                        }
                    toolCallsExecuted.add(ToolCallRecord(tc.name, tc.arguments, result.content))
                    messages.add(ChatMessage.tool(contentForLlm(result.content), result.toolCallId))
                }
            }

            log.warn(
                "Max tool iterations reached ({}): {} - tool calls so far: {}",
                MAX_TOOL_ITERATIONS,
                userMessage,
                toolCallsExecuted.map { it.name }
            )
            val chartData = extractChartDataFromToolCalls(toolCallsExecuted)
            val timeLogsData = extractTimeLogsFromToolCalls(toolCallsExecuted)
            val data = hashMapOf<String, Any?>()
            if (chartData != null) data["chart"] = chartData
            if (timeLogsData != null) data["timeLogs"] = timeLogsData
            return persistTurnAndBuildResponse(
                userMessage,
                "I'm sorry, I reached the maximum number of steps. Please try a simpler request.",
                toolCallsExecuted,
                if (data.isEmpty()) null else data,
                history,
                contextEntries,
                startTime,
                true,
                conversationId,
                reasoningPhases
            )
        } finally {
            (llmClient as? RoutingLlmClient)?.clearRequestScope()
        }
    }

    /**
     * Summarizes context entries and projects for the system prompt to reduce context size.
     * Format: "Selected entries: id1 (ProjectA, 30 min), ...; use these ids for update_time_log/delete_time_log."
     * and "Selected projects: name1 (id1), ...". Truncates at CONTEXT_ENTRIES_MAX / CONTEXT_PROJECTS_MAX.
     */
    private fun summarizeContext(contextEntries: List<Map<String, Any?>>?, contextProjects: List<Map<String, Any?>>?): String {
        val sb = StringBuilder()
        if (!contextEntries.isNullOrEmpty()) {
            val max = min(contextEntries.size, CONTEXT_ENTRIES_MAX)
            val parts = mutableListOf<String>()
            for (i in 0 until max) {
                val e = contextEntries[i]
                val id = e["id"]?.toString() ?: "?"
                val projectName = e["projectName"]?.toString() ?: "?"
                val dur = e["durationMinutes"]
                val min = if (dur != null) "$dur min" else "?"
                parts.add("$id ($projectName, $min)")
            }
            sb.append("\n\n[Context] Selected entries: ").append(parts.joinToString(", "))
            if (contextEntries.size > max) {
                sb.append(" ... and ").append(contextEntries.size - max).append(" more")
            }
            sb.append(". Use these ids for update_time_log or delete_time_log when asked.")
        }
        if (!contextProjects.isNullOrEmpty()) {
            val max = min(contextProjects.size, CONTEXT_PROJECTS_MAX)
            val parts = mutableListOf<String>()
            for (i in 0 until max) {
                val p = contextProjects[i]
                val name = p["name"]?.toString() ?: "?"
                val id = p["id"]?.toString() ?: "?"
                parts.add("$name ($id)")
            }
            sb.append("\n\n[Context] Selected projects: ").append(parts.joinToString(", "))
            if (contextProjects.size > max) {
                sb.append(" ... and ").append(contextProjects.size - max).append(" more")
            }
            sb.append(". When the user refers to 'these projects' or by name, use these ids.")
        }
        return sb.toString()
    }

    /**
     * Builds the [Memories] block for the system prompt: active memories for the default user, limited in count.
     */
    /**
     * Static instructions first, then UI context and memories, then server time last (longer shared prefix for caching).
     */
    private fun buildFullSystemPrompt(
        contextEntries: List<Map<String, Any?>>?,
        contextProjects: List<Map<String, Any?>>?
    ): String {
        return SYSTEM_PROMPT + summarizeContext(contextEntries, contextProjects) + buildMemoriesBlock() +
            serverTemporalContextService.buildPromptBlock()
    }

    private fun buildMemoriesBlock(): String {
        val userId = memoryService.getDefaultUserId()
        val memories = memoryService.findActiveByUserId(userId)
        val limit = min(memories.size, MEMORIES_INJECT_MAX)
        if (limit == 0) {
            return "\n\n[Memories]\nNo stored memories."
        }
        val sb = StringBuilder(
            "\n\n[Memories] Stored facts about the user (use these to personalize and avoid re-asking):\n"
        )
        for (i in 0 until limit) {
            val fact = memories[i].factText
            if (!fact.isNullOrBlank()) {
                sb.append("- ").append(fact).append("\n")
            }
        }
        if (memories.size > limit) {
            sb.append("... and ").append(memories.size - limit).append(" more.")
        }
        return sb.toString()
    }

    /**
     * Extracts the LLM-facing content from a tool result. If the result is dual format (has "llm" and "data"),
     * returns only the "llm" string for the model; otherwise returns the full content (backward compat).
     */
    private fun contentForLlm(toolResultContent: String?): String {
        if (toolResultContent.isNullOrBlank()) {
            return ""
        }
        try {
            val root = objectMapper.readTree(toolResultContent)
            if (root.isObject && root.has("llm") && root["llm"].isTextual) {
                return root["llm"].asText()
            }
        } catch (e: Exception) {
            log.trace("Tool result is not dual JSON, using as-is: {}", e.message)
        }
        return toolResultContent
    }

    /**
     * Returns the payload to use when parsing tool result for data (time_logs, time_log, etc.).
     * If the result is dual format, returns the "data" node; otherwise returns the root.
     */
    private fun resultDataNode(toolResultContent: String?): JsonNode {
        if (toolResultContent.isNullOrBlank()) {
            return objectMapper.createObjectNode()
        }
        return try {
            val root = objectMapper.readTree(toolResultContent)
            if (root.isObject && root.has("data")) {
                root["data"]
            } else {
                root
            }
        } catch (_: Exception) {
            objectMapper.createObjectNode()
        }
    }

    private fun deriveStatus(assistantMessage: String?, toolCalls: List<ToolCallRecord>?, maxIterations: Boolean): String {
        if (maxIterations) return "max_iterations"
        toolCalls?.forEach { tc ->
            if (tc.result.contains("\"error\":")) {
                return "tool_error"
            }
        }
        if (assistantMessage.isNullOrBlank()) return "empty_result"
        if (assistantMessage.contains("I couldn't generate a response") ||
            assistantMessage.contains("I'm sorry, I couldn't")
        ) {
            return "empty_result"
        }
        return "success"
    }

    private fun resolveModelName(): String {
        if (llmClient is RoutingLlmClient) {
            val m = llmClient.getLastSelectedModel()
            return if (!m.isNullOrBlank()) m else llmProperties.resolvedModel()
        }
        return llmProperties.resolvedModel()
    }

    private fun persistTurn(
        userMessage: String,
        assistantMessage: String,
        toolCallsExecuted: List<ToolCallRecord>,
        data: Any?,
        history: List<ChatHistoryEntry>?,
        contextEntries: List<Map<String, Any?>>?,
        startTime: Long,
        maxIterations: Boolean,
        modelName: String?,
        conversationId: UUID?,
        reasoningPhases: List<ReasoningPhase> = emptyList()
    ): AgentTurn {
        val cid = conversationId ?: UUID.randomUUID()
        val turnIndex = agentTurnService.countTurnsInConversation(cid).toInt()
        val status = deriveStatus(assistantMessage, toolCallsExecuted, maxIterations)
        val latencyMs = System.currentTimeMillis() - startTime
        val modelToStore = if (!modelName.isNullOrBlank()) modelName else llmProperties.resolvedModel()
        val turn = agentTurnService.saveTurn(
            cid,
            turnIndex,
            userMessage,
            assistantMessage,
            toolCallsExecuted,
            data,
            modelToStore,
            status,
            history,
            contextEntries,
            latencyMs
        )
        val toolNames = toolCallsExecuted.map { it.name }
        val toolCallSteps = toolCallsExecuted.map { tc ->
            ToolCallTrace(name = tc.name, arguments = tc.arguments, result = tc.result)
        }
        agentTraceSink.onTurnCompleted(
            TurnCompletedEvent(
                turnId = turn.id!!,
                conversationId = cid,
                userMessage = userMessage,
                assistantMessage = assistantMessage,
                toolCallNames = toolNames,
                model = modelToStore,
                status = status,
                startTimeEpochMs = startTime,
                latencyMs = latencyMs,
                reasoningPhases = reasoningPhases,
                toolCallSteps = toolCallSteps
            )
        )
        recordAgentTurnOtelSpan(turn, latencyMs)
        return turn
    }

    /**
     * Optional Micrometer span (OTLP export when management.tracing + OTLP are configured).
     */
    private fun recordAgentTurnOtelSpan(turn: AgentTurn, latencyMs: Long?) {
        tracer.ifAvailable { t ->
            try {
                val span = t.nextSpan()
                    .name("horain.agent.turn")
                    .tag("turn.id", turn.id.toString())
                    .tag("conversation.id", turn.conversationId.toString())
                    .tag("horain.model", turn.model ?: "")
                    .tag("horain.status", turn.status ?: "")
                    .tag("latency.ms", (latencyMs ?: 0L).toString())
                val started = span.start()
                started.end()
            } catch (e: Exception) {
                log.trace("OTel span: {}", e.message)
            }
        }
    }

    private fun persistTurnAndBuildResponse(
        userMessage: String,
        assistantMessage: String,
        toolCallsExecuted: List<ToolCallRecord>,
        data: Any?,
        history: List<ChatHistoryEntry>?,
        contextEntries: List<Map<String, Any?>>?,
        startTime: Long,
        maxIterations: Boolean,
        conversationId: UUID?,
        reasoningPhases: List<ReasoningPhase> = emptyList()
    ): ChatResponse {
        val turn = persistTurn(
            userMessage,
            assistantMessage,
            toolCallsExecuted,
            data,
            history,
            contextEntries,
            startTime,
            maxIterations,
            resolveModelName(),
            conversationId,
            reasoningPhases
        )
        return ChatResponse(assistantMessage, toolCallsExecuted, data, turn.id, turn.conversationId!!)
    }

    /**
     * Same as chat() but streams the final assistant text via the writer.
     * Only the last LLM turn (no tool_calls) is streamed; intermediate tool rounds use non-streaming chat().
     */
    fun chatStream(
        userMessage: String,
        history: List<ChatHistoryEntry>?,
        contextEntries: List<Map<String, Any?>>?,
        contextProjects: List<Map<String, Any?>>?,
        conversationId: UUID?,
        writer: StreamEventWriter
    ) {
        val startTime = System.currentTimeMillis()
        val messages = mutableListOf<ChatMessage>()
        val systemPrompt = buildFullSystemPrompt(contextEntries, contextProjects)
        messages.add(ChatMessage.system(systemPrompt))
        if (!history.isNullOrEmpty()) {
            for (e in history) {
                when (e.role.lowercase()) {
                    "user" -> messages.add(ChatMessage.user(e.content))
                    "assistant" -> messages.add(ChatMessage.assistant(e.content))
                }
            }
        }
        messages.add(ChatMessage.user(userMessage))

        val tools = toolRegistry.getAllTools()
        val toolCallsExecuted = mutableListOf<ToolCallRecord>()
        val toolCallIterations = mutableListOf<Int>()
        val streamingClient = llmClient is StreamingLlmClient
        val accumulatedAssistantMessage = StringBuilder()
        val reasoningPhases = mutableListOf<ReasoningPhase>()

        try {
            for (iterations in 0 until MAX_TOOL_ITERATIONS) {
                val reasoningTimeMs = longArrayOf(-1L, -1L)
                val response: LlmResponse =
                    if (streamingClient) {
                        val reasoningConsumer = Consumer<String> { text ->
                            writer.sendReasoningChunk(text)
                            val now = System.currentTimeMillis()
                            if (reasoningTimeMs[0] < 0) reasoningTimeMs[0] = now
                            reasoningTimeMs[1] = now
                        }
                        (llmClient as StreamingLlmClient).chatStream(
                            messages,
                            tools,
                            Consumer { text -> writer.sendChunk(text) },
                            reasoningConsumer
                        )
                    } else {
                        llmClient.chat(messages, tools)
                    }

                if (iterations > 0) {
                    log.debug("Tool iteration {} for message: {}", iterations, userMessage)
                }
                if (iterations == 0) {
                    writer.sendModelName(resolveModelName())
                }
                if (streamingClient && reasoningTimeMs[0] >= 0 && reasoningTimeMs[1] >= 0) {
                    writer.sendReasoningPhaseDone(reasoningTimeMs[1] - reasoningTimeMs[0])
                }

                if (!response.reasoningSummary.isNullOrBlank()) {
                    val phaseDurationMs =
                        if (reasoningTimeMs[0] >= 0 && reasoningTimeMs[1] >= 0) {
                            reasoningTimeMs[1] - reasoningTimeMs[0]
                        } else {
                            null
                        }
                    reasoningPhases.add(ReasoningPhase(response.reasoningSummary.trim(), phaseDurationMs))
                }

                val turnContent = response.content ?: ""
                if (turnContent.isNotBlank()) {
                    if (accumulatedAssistantMessage.isNotEmpty()) {
                        accumulatedAssistantMessage.append("\n\n")
                    }
                    accumulatedAssistantMessage.append(turnContent.trim())
                }

                if (!response.hasToolCalls()) {
                    val chartData = extractChartDataFromToolCalls(toolCallsExecuted)
                    val timeLogsData = extractTimeLogsFromToolCalls(toolCallsExecuted)
                    val data = hashMapOf<String, Any?>()
                    if (chartData != null) data["chart"] = chartData
                    if (timeLogsData != null) data["timeLogs"] = timeLogsData
                    val assistantMessage =
                        if (accumulatedAssistantMessage.isNotEmpty()) {
                            accumulatedAssistantMessage.toString()
                        } else {
                            log.warn("LLM returned empty content (check backend logs for Responses API or model errors)")
                            "I'm sorry, I couldn't generate a response."
                        }
                    val reasoningText = response.reasoningSummary?.takeIf { it.isNotBlank() }
                    val reasoningDurationMs =
                        if (reasoningTimeMs[0] >= 0 && reasoningTimeMs[1] >= 0) {
                            reasoningTimeMs[1] - reasoningTimeMs[0]
                        } else {
                            null
                        }
                    val turn = persistTurn(
                        userMessage,
                        assistantMessage,
                        toolCallsExecuted,
                        if (data.isEmpty()) null else data,
                        history,
                        contextEntries,
                        startTime,
                        false,
                        resolveModelName(),
                        conversationId,
                        reasoningPhases
                    )
                    writer.sendDone(
                        assistantMessage,
                        toolCallsExecuted,
                        toolCallIterations,
                        if (data.isEmpty()) null else data,
                        turn.id!!,
                        reasoningText,
                        reasoningDurationMs,
                        resolveModelName(),
                        turn.conversationId!!
                    )
                    return
                }

                messages.add(
                    ChatMessage.assistantWithToolCalls(
                        response.content ?: "",
                        response.toolCalls!!
                    )
                )

                if (turnContent.isNotBlank()) {
                    writer.sendAssistantSegment(turnContent.trim(), iterations)
                }
                for (tc in response.toolCalls!!) {
                    val result =
                        if (tc.name == ToolRegistry.DELETE_TIME_LOG) {
                            val deleteCount = toolCallsExecuted.count { r -> r.name == ToolRegistry.DELETE_TIME_LOG }
                            if (deleteCount >= massDeleteLimit) {
                                ToolCallResult(
                                    tc.id,
                                    "{\"error\":\"Mass deletion guard: max $massDeleteLimit " +
                                        "entries per turn. Ask the user to confirm before deleting more, then proceed in a follow-up message.\"}"
                                )
                            } else {
                                toolExecutor.execute(tc)
                            }
                        } else {
                            toolExecutor.execute(tc)
                        }
                    val record = ToolCallRecord(tc.name, tc.arguments, result.content)
                    toolCallsExecuted.add(record)
                    toolCallIterations.add(iterations)
                    writer.sendToolCall(record, iterations)
                    messages.add(ChatMessage.tool(contentForLlm(result.content), result.toolCallId))
                }
            }

            log.warn(
                "Max tool iterations reached ({}): {} - tool calls so far: {}",
                MAX_TOOL_ITERATIONS,
                userMessage,
                toolCallsExecuted.map { it.name }
            )
            val chartData = extractChartDataFromToolCalls(toolCallsExecuted)
            val timeLogsData = extractTimeLogsFromToolCalls(toolCallsExecuted)
            val data = hashMapOf<String, Any?>()
            if (chartData != null) data["chart"] = chartData
            if (timeLogsData != null) data["timeLogs"] = timeLogsData
            val maxStepsMessage = "I'm sorry, I reached the maximum number of steps. Please try a simpler request."
            val turn = persistTurn(
                userMessage,
                maxStepsMessage,
                toolCallsExecuted,
                if (data.isEmpty()) null else data,
                history,
                contextEntries,
                startTime,
                true,
                resolveModelName(),
                conversationId,
                reasoningPhases
            )
            writer.sendDone(
                maxStepsMessage,
                toolCallsExecuted,
                toolCallIterations,
                if (data.isEmpty()) null else data,
                turn.id!!,
                null,
                null,
                resolveModelName(),
                turn.conversationId!!
            )
        } catch (e: Exception) {
            log.error("chatStream error: {}", e.message, e)
            writer.sendError(e.message ?: "Unknown error")
        } finally {
            (llmClient as? RoutingLlmClient)?.clearRequestScope()
        }
    }

    private fun extractChartDataFromToolCalls(toolCallsExecuted: List<ToolCallRecord>): Any? {
        var lastProposeChart: ToolCallRecord? = null
        for (i in toolCallsExecuted.indices.reversed()) {
            val tc = toolCallsExecuted[i]
            if (tc.name == ToolRegistry.PROPOSE_CHART) {
                lastProposeChart = tc;
                break;
            }
        }
        if (lastProposeChart == null) {
            return null
        }
        return try {
            val args = objectMapper.readTree(lastProposeChart.arguments)
            val chart = hashMapOf<String, Any?>()
            if (args.has("chartType")) chart["type"] = args["chartType"].asText()
            if (args.has("title")) chart["title"] = args["title"].asText()
            if (args.has("categories")) {
                val cat = mutableListOf<String>()
                for (c in args["categories"]) cat.add(c.asText())
                chart["categories"] = cat
            }
            if (args.has("series")) {
                val series = mutableListOf<Map<String, Any?>>()
                for (s in args["series"]) {
                    val item = hashMapOf<String, Any?>()
                    if (s.has("name")) item["name"] = s["name"].asText()
                    if (s.has("data")) {
                        val data = mutableListOf<Double>()
                        for (d in s["data"]) data.add(if (d.isNumber) d.asDouble() else 0.0)
                        item["data"] = data
                    }
                    series.add(item)
                }
                chart["series"] = series
            }
            chart
        } catch (e: Exception) {
            log.debug("Failed to parse propose_chart arguments: {}", e.message)
            null
        }
    }

    private fun isValidTimeLogId(id: String?): Boolean {
        if (id == null || id.isBlank()) return false;
        try {
            UUID.fromString(id.trim());
            return true;
        } catch (_: IllegalArgumentException) {
            return false
        }
    }

    /**
     * Parses `time_logs` from a fetch tool result (search_time_logs, get_time_logs_for_period, get_recent_logs).
     */
    private fun parseTimeLogsFromFetchTool(tc: ToolCallRecord?): List<Map<String, Any?>>? {
        if (tc == null) {
            return null
        }
        return try {
            val root = resultDataNode(tc.result)
            if (root.has("error")) {
                return null
            }
            val timeLogs = root["time_logs"] ?: return null
            if (!timeLogs.isArray) {
                return null
            }
            val entries = mutableListOf<Map<String, Any?>>()
            for (entry in timeLogs) {
                val map = hashMapOf<String, Any?>()
                if (entry.has("id")) map["id"] = entry["id"].asText()
                if (entry.has("projectId")) map["projectId"] = entry["projectId"].asText()
                if (entry.has("projectName")) map["projectName"] = entry["projectName"].asText()
                if (entry.has("durationMinutes")) map["durationMinutes"] = entry["durationMinutes"].asInt()
                if (entry.has("note")) map["note"] = entry["note"].asText()
                if (entry.has("loggedAt")) map["loggedAt"] = entry["loggedAt"].asText()
                if (entry.has("billable")) map["billable"] = entry["billable"].asBoolean()
                if (entry.has("activityTypeCode")) map["activityTypeCode"] = entry["activityTypeCode"].asText()
                if (entry.has("activityTypeLabel")) map["activityTypeLabel"] = entry["activityTypeLabel"].asText()
                if (entry.has("dailyRateCents")) map["dailyRateCents"] = entry["dailyRateCents"].asInt()
                entries.add(map)
            }
            if (entries.isEmpty()) null else entries
        } catch (e: Exception) {
            log.debug("Failed to parse time_logs from {} result: {}", tc.name, e.message)
            null
        }
    }

    /**
     * Most recent successful time_logs payload from a fetch tool (authoritative ids and fields for the UI).
     */
    private fun findLastAuthoritativeTimeLogs(toolCallsExecuted: List<ToolCallRecord>): List<Map<String, Any?>>? {
        for (i in toolCallsExecuted.indices.reversed()) {
            val tc = toolCallsExecuted[i]
            val name = tc.name
            if (ToolRegistry.SEARCH_TIME_LOGS == name ||
                ToolRegistry.GET_TIME_LOGS_FOR_PERIOD == name ||
                ToolRegistry.GET_RECENT_LOGS == name
            ) {
                val parsed = parseTimeLogsFromFetchTool(tc)
                if (parsed != null) {
                    return parsed
                }
            }
        }
        return null
    }

    private fun authoritativeRowById(authoritative: List<Map<String, Any?>>, id: String): Map<String, Any?>? {
        for (row in authoritative) {
            val oid = row["id"]
            if (oid != null && id == oid.toString()) {
                return row
            }
        }
        return null
    }

    /**
     * Reconcile propose_entries with the last fetch tool result so the UI never shows LLM-hallucinated ids
     * (which break PATCH /time-logs/:id). Preserves propose_entries order; uses server row data when id matches.
     */
    private fun reconcileProposeEntriesWithFetch(
        proposeParsed: List<Map<String, Any?>>,
        authoritative: List<Map<String, Any?>>?
    ): List<Map<String, Any?>> {
        if (authoritative.isNullOrEmpty()) {
            return proposeParsed
        }
        val validIds = hashSetOf<String>()
        for (row in authoritative) {
            val oid = row["id"]
            if (oid != null && isValidTimeLogId(oid.toString())) {
                validIds.add(oid.toString())
            }
        }
        if (validIds.isEmpty()) {
            return proposeParsed
        }
        val out = mutableListOf<Map<String, Any?>>()
        for (e in proposeParsed) {
            val idObj = e["id"] ?: continue
            val idStr = idObj.toString()
            if (idStr !in validIds) {
                continue
            }
            val canon = authoritativeRowById(authoritative, idStr)
            out.add(LinkedHashMap(canon ?: e))
        }
        if (out.isNotEmpty()) {
            return out
        }
        return authoritative.map { LinkedHashMap(it) }.toMutableList()
    }

    private fun extractTimeLogsFromToolCalls(toolCallsExecuted: List<ToolCallRecord>): Any? {
        val patchesById = buildTimeLogPatchesFromToolResults(toolCallsExecuted)
        val authoritative = findLastAuthoritativeTimeLogs(toolCallsExecuted)

        var lastProposeEntries: ToolCallRecord? = null
        for (i in toolCallsExecuted.indices.reversed()) {
            val tc = toolCallsExecuted[i]
            if (tc.name == ToolRegistry.PROPOSE_ENTRIES) {
                lastProposeEntries = tc
                break
            }
        }
        if (lastProposeEntries != null) {
            try {
                val args = objectMapper.readTree(lastProposeEntries.arguments)
                val entriesNode = args["entries"]
                if (entriesNode != null && entriesNode.isArray) {
                    val entries = mutableListOf<MutableMap<String, Any?>>()
                    for (entry in entriesNode) {
                        if (!entry.has("durationMinutes") || !entry.has("loggedAt")) continue
                        val idStr = if (entry.has("id")) entry["id"].asText() else null
                        if (!isValidTimeLogId(idStr)) continue
                        val map = hashMapOf<String, Any?>()
                        map["id"] = idStr
                        map["durationMinutes"] = entry["durationMinutes"].asInt()
                        map["loggedAt"] = entry["loggedAt"].asText()
                        if (entry.has("projectId")) map["projectId"] = entry["projectId"].asText()
                        if (entry.has("projectName")) map["projectName"] = entry["projectName"].asText()
                        if (entry.has("note")) map["note"] = entry["note"].asText()
                        if (entry.has("billable")) map["billable"] = entry["billable"].asBoolean()
                        if (entry.has("activityTypeCode")) map["activityTypeCode"] = entry["activityTypeCode"].asText()
                        if (entry.has("activityTypeLabel")) map["activityTypeLabel"] = entry["activityTypeLabel"].asText()
                        if (entry.has("dailyRateCents")) map["dailyRateCents"] = entry["dailyRateCents"].asInt()
                        entries.add(map)
                    }
                    if (entries.isNotEmpty()) {
                        val reconciled = reconcileProposeEntriesWithFetch(entries, authoritative)
                        val mutable = reconciled.map { LinkedHashMap(it).toMutableMap() }.toMutableList()
                        applyTimeLogPatches(mutable, patchesById)
                        return mutable
                    }
                }
            } catch (e: Exception) {
                log.debug("Failed to parse propose_entries arguments: {}", e.message)
            }
        }
        if (authoritative != null) {
            val copy = authoritative.map { LinkedHashMap(it).toMutableMap() }.toMutableList()
            applyTimeLogPatches(copy, patchesById)
            return copy
        }
        var lastLogsCall: ToolCallRecord? = null
        for (i in toolCallsExecuted.indices.reversed()) {
            val tc = toolCallsExecuted[i]
            if (tc.name == ToolRegistry.SEARCH_TIME_LOGS ||
                tc.name == ToolRegistry.GET_TIME_LOGS_FOR_PERIOD ||
                tc.name == ToolRegistry.GET_RECENT_LOGS
            ) {
                lastLogsCall = tc
                break
            }
        }
        val fromLastFetch = parseTimeLogsFromFetchTool(lastLogsCall)
        if (fromLastFetch != null) {
            val copy = fromLastFetch.map { LinkedHashMap(it).toMutableMap() }.toMutableList()
            applyTimeLogPatches(copy, patchesById)
            return copy
        }
        val createdOrUpdatedEntries = mutableListOf<Map<String, Any?>>()
        for (tc in toolCallsExecuted) {
            if (tc.name != ToolRegistry.CREATE_TIME_LOG && tc.name != ToolRegistry.UPDATE_TIME_LOG) {
                continue
            }
            try {
                val root = resultDataNode(tc.result)
                if (root.has("error")) continue
                val timeLog = root["time_log"] ?: continue
                if (!timeLog.isObject) continue
                if (!timeLog.has("durationMinutes") || !timeLog.has("loggedAt")) continue
                val map = hashMapOf<String, Any?>()
                map["durationMinutes"] = timeLog["durationMinutes"].asInt()
                map["loggedAt"] = timeLog["loggedAt"].asText()
                if (timeLog.has("id")) map["id"] = timeLog["id"].asText()
                if (timeLog.has("projectId")) map["projectId"] = timeLog["projectId"].asText()
                if (timeLog.has("projectName")) map["projectName"] = timeLog["projectName"].asText()
                if (timeLog.has("note")) map["note"] = timeLog["note"].asText()
                if (timeLog.has("billable")) map["billable"] = timeLog["billable"].asBoolean()
                if (timeLog.has("activityTypeCode")) map["activityTypeCode"] = timeLog["activityTypeCode"].asText()
                if (timeLog.has("activityTypeLabel")) map["activityTypeLabel"] = timeLog["activityTypeLabel"].asText()
                if (timeLog.has("dailyRateCents")) map["dailyRateCents"] = timeLog["dailyRateCents"].asInt()
                createdOrUpdatedEntries.add(map)
            } catch (e: Exception) {
                log.debug("Failed to parse time_log from {} result: {}", tc.name, e.message)
            }
        }
        if (createdOrUpdatedEntries.isNotEmpty()) {
            return createdOrUpdatedEntries
        }
        return null
    }

    /**
     * Builds a map of time_log id -> updated fields from create_time_log and update_time_log tool results.
     * Used to overlay actual post-update state onto entries from propose_entries or get_time_logs,
     * so the table reflects the truth after mass updates (e.g. "set all to billable").
     */
    private fun buildTimeLogPatchesFromToolResults(toolCallsExecuted: List<ToolCallRecord>): Map<String, Map<String, Any?>> {
        val byId = hashMapOf<String, Map<String, Any?>>()
        for (tc in toolCallsExecuted) {
            if (tc.name != ToolRegistry.CREATE_TIME_LOG && tc.name != ToolRegistry.UPDATE_TIME_LOG) {
                continue
            }
            try {
                val root = resultDataNode(tc.result)
                if (root.has("error")) continue
                val timeLog = root["time_log"] ?: continue
                if (!timeLog.isObject || !timeLog.has("id")) continue
                val id = timeLog["id"].asText()
                val map = hashMapOf<String, Any?>()
                if (timeLog.has("durationMinutes")) map["durationMinutes"] = timeLog["durationMinutes"].asInt()
                if (timeLog.has("loggedAt")) map["loggedAt"] = timeLog["loggedAt"].asText()
                if (timeLog.has("projectId")) map["projectId"] = timeLog["projectId"].asText()
                if (timeLog.has("projectName")) map["projectName"] = timeLog["projectName"].asText()
                if (timeLog.has("note")) {
                    map["note"] =
                        if (!timeLog["note"].isNull) timeLog["note"].asText() else ""
                }
                if (timeLog.has("billable")) map["billable"] = timeLog["billable"].asBoolean()
                if (timeLog.has("activityTypeCode")) map["activityTypeCode"] = timeLog["activityTypeCode"].asText()
                if (timeLog.has("activityTypeLabel")) map["activityTypeLabel"] = timeLog["activityTypeLabel"].asText()
                if (timeLog.has("dailyRateCents")) map["dailyRateCents"] = timeLog["dailyRateCents"].asInt()
                byId[id] = map
            } catch (e: Exception) {
                log.debug("Failed to parse time_log from {} result: {}", tc.name, e.message)
            }
        }
        return byId
    }

    /**
     * Overwrites entry fields with values from update_time_log/create_time_log results when the entry id matches.
     * Ensures the table shows the post-update state (e.g. billable=true) after mass updates.
     */
    private fun applyTimeLogPatches(entries: MutableList<MutableMap<String, Any?>>, patchesById: Map<String, Map<String, Any?>>) {
        if (patchesById.isEmpty()) return
        for (entry in entries) {
            val idObj = entry["id"] ?: continue
            val patch = patchesById[idObj.toString()] ?: continue
            for ((k, v) in patch) {
                entry[k] = v
            }
        }
    }
}
