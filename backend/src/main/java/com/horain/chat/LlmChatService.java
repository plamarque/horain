package com.horain.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horain.agent.AgentTurnService;
import com.horain.llm.*;
import com.horain.model.AgentTurn;
import com.horain.model.Memory;
import com.horain.service.MemoryService;
import com.horain.tools.ToolExecutorService;
import com.horain.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Orchestrates the chat flow: receives user message, calls LLM with tools,
 * executes requested tools, loops until final response.
 */
@Service
public class LlmChatService {

    private static final Logger log = LoggerFactory.getLogger(LlmChatService.class);
    private static final int MAX_TOOL_ITERATIONS = 10;
    private static final int CONTEXT_ENTRIES_MAX = 20;
    private static final int CONTEXT_PROJECTS_MAX = 10;
    private static final int MEMORIES_INJECT_MAX = 30;

    private static final String SYSTEM_PROMPT = """
            You are Horain, a personal time logging assistant. You help users log time spent on projects and answer questions about their tracked time.

            ## Data and tools
            - Use the available tools to read and write data. You never guess data.
            - The [Memories] section (when present) contains stored facts about the user (preferences, project disambiguation, typos). Use them to personalize responses and avoid re-asking; e.g. when the user says "HatCast" and a memory says they mean HatCast V2, log on that project directly. You can store new facts with store_memory after a confirmed disambiguation or explicit preference, and forget with forget_memory when the user asks.
            - projectId: sum_time_by_project, get_time_logs_for_period, create_time_log accept EITHER a project UUID OR a project name (e.g. "HatCast"). If you pass a name, the system resolves it automatically.

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
            - For time queries ("combien de temps?", "how many hours?", "what did I do?"): use get_current_datetime first, then sum_time_for_period or get_time_logs_for_period. When you need "this week" or "today" or "this month", call get_current_datetime to get the correct start/end timestamps.
            - For listing entries ("les entrées", "détails", "qu'est-ce que j'ai logué?", "what did I log?", "show me my entries"): call get_time_logs_for_period or get_recent_logs, then MUST call propose_entries with the full time_logs array (including id, projectId, projectName, and when present activityTypeCode, activityTypeLabel, dailyRateCents for each entry). Do NOT summarize entries in your text; the UI displays them in a table. Keep your text response brief (e.g. "Here are your entries for this week.").
            - When the user asks to find entries by keyword or phrase (e.g. "find logs with backend", "entries mentioning Horain", "recherche pie chart", "entrées qui contiennent X"): call search_time_logs with the query, then propose_entries with the returned time_logs. Do NOT use get_time_logs_for_period or get_recent_logs for keyword search.
            - When listing entries for a specific project (e.g. "list entries for Horain"): if the user did not specify a date range, call get_current_datetime first and use a period that includes recent activity (e.g. startOfMonth to endOfMonth, or startOfWeek to endOfWeek). Do NOT use an arbitrary past period (e.g. October); use the current month or week so that recent entries are included.
            - When the user asks to change/update/toggle "toutes les activités" or "all activities" for a project (e.g. "bascule toutes les activités associées à eXo en facturable") WITHOUT specifying a period: call get_current_datetime, then get_time_logs_for_period with start = "2000-01-01T00:00:00Z" and end = the endOfMonth value returned by get_current_datetime, and projectId = the project. Do NOT assume or use an arbitrary month (e.g. October); if no period was specified, use this all-time range.
            - When the user asks which activities or entries correspond to a date or period ("quelles activités?", "détail", "les entrées du lundi 2", "what entries on March 2?"): call get_time_logs_for_period with that exact day's start and end, then list the entries or call propose_entries. Never say "no activities" or "aucune activité" without having called get_time_logs_for_period for that exact period first.

            ## Mass operations (guards)
            - MASS DELETION GUARD: Before deleting more than 3 entries in one turn, you MUST ask for explicit confirmation: "You are about to delete N entries. Please confirm (yes / delete all)." Do NOT execute the deletions until the user has clearly confirmed. Never suggest or perform mass deletions without confirmation.
            - MASS UPDATE GUARD: When the user asks to apply a change to "all" or "toutes" activities for a project (e.g. set all to billable, "bascule toutes en facturable"), you MUST first fetch the entries (using the all-time range above if no period was specified), then state the ACTION you will perform and ask for confirmation. The confirmation must describe the action requested, not the current state: e.g. "You are about to set all N entries to billable. Confirm? (yes / go ahead)". Never say "set X to billable and Y to non-billable" when the user asked to set all to billable—that describes the current mix, not the action. Do NOT call update_time_log in a loop until the user has confirmed.

            ## Charts and analytics
            - For analytical questions ("sur quoi j'ai travaillé cette semaine?", "what did I work on this week?", "répartition par projet", "hours per project", "un chart"): call get_current_datetime, then get_time_aggregated_for_chart with groupBy "day_and_project" (stacked bar by project per day) or "day_and_billable" (stacked bar billable vs non-billable per day; use for "heures facturables vs non facturables par jour") or "project_only" (pie), then propose_chart with chartType "stackedBar", "pie", or "bar". Include a short text summary. You MUST call propose_chart to show a chart; never output markdown image syntax like ![...](url).
            - For "heures facturables du [date]" or "billable hours on [date]": use sum_billable_time_for_period with start and end of that calendar day in UTC (e.g. 2025-03-02T00:00:00Z to 2025-03-03T00:00:00Z for March 2). The numbers must match the chart if the chart was built with day_and_billable for the same period.
            - Occupancy rate (taux d'occupation): formula is (total hours from sum_time_for_period) / (days in period × base hours per day) × 100. Count calendar days: "2 weeks" = 14 days, "this week" = 7 days. Example: 2 weeks at 7 h/day → denominator = 14 × 7 = 98 hours. Always show the calculation.

            ## Entry edits and deletes
            - When the user asks to edit, change, or correct an entry (e.g. "change duration to 45 min", "update the note", "fix that entry"): ALWAYS use update_time_log with the entry id and the new values. NEVER use create_time_log for modifying an existing entry. NEVER use create_time_log + delete_time_log to simulate an update.
            - When the user asks to edit/change/correct an entry but no context entries are provided: first call get_recent_logs or get_time_logs_for_period (use get_current_datetime for "today"/"this week") to fetch entries, identify which entry to modify, then call update_time_log with its id.
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
            """;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ToolExecutorService toolExecutor;
    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;
    private final int massDeleteLimit;
    private final AgentTurnService agentTurnService;
    private final LlmProperties llmProperties;

    public LlmChatService(LlmClient llmClient, ToolRegistry toolRegistry, ToolExecutorService toolExecutor,
                          MemoryService memoryService,
                          ObjectMapper objectMapper,
                          @Value("${horain.mass-delete-limit:5}") int massDeleteLimit,
                          AgentTurnService agentTurnService,
                          LlmProperties llmProperties) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
        this.massDeleteLimit = massDeleteLimit;
        this.agentTurnService = agentTurnService;
        this.llmProperties = llmProperties;
    }

    public ChatResponse chat(String userMessage, List<ChatHistoryEntry> history,
                             List<Map<String, Object>> contextEntries,
                             List<Map<String, Object>> contextProjects) {
        long startTime = System.currentTimeMillis();
        List<ChatMessage> messages = new ArrayList<>();
        String systemPrompt = SYSTEM_PROMPT + summarizeContext(contextEntries, contextProjects) + buildMemoriesBlock();
        messages.add(ChatMessage.system(systemPrompt));
        if (history != null && !history.isEmpty()) {
            for (ChatHistoryEntry e : history) {
                if (e != null && e.role() != null && e.content() != null) {
                    String role = e.role().toLowerCase();
                    if ("user".equals(role)) {
                        messages.add(ChatMessage.user(e.content()));
                    } else if ("assistant".equals(role)) {
                        messages.add(ChatMessage.assistant(e.content()));
                    }
                }
            }
        }
        messages.add(ChatMessage.user(userMessage));

        List<ToolDefinition> tools = toolRegistry.getAllTools();
        List<ToolCallRecord> toolCallsExecuted = new ArrayList<>();

        for (int iterations = 0; iterations < MAX_TOOL_ITERATIONS; iterations++) {
            LlmResponse response = llmClient.chat(messages, tools);

            if (iterations > 0) {
                log.debug("Tool iteration {} for message: {}", iterations, userMessage);
            }

            if (!response.hasToolCalls()) {
                Object chartData = extractChartDataFromToolCalls(toolCallsExecuted);
                Object timeLogsData = extractTimeLogsFromToolCalls(toolCallsExecuted);
                Map<String, Object> data = new HashMap<>();
                if (chartData != null) data.put("chart", chartData);
                if (timeLogsData != null) data.put("timeLogs", timeLogsData);
                String assistantMessage = response.content() != null && !response.content().isBlank()
                        ? response.content()
                        : "I'm sorry, I couldn't generate a response.";
                return persistTurnAndBuildResponse(userMessage, assistantMessage, toolCallsExecuted,
                        data.isEmpty() ? null : data, history, contextEntries, startTime, false);
            }

            // Append assistant message with tool_calls
            messages.add(ChatMessage.assistantWithToolCalls(
                    response.content() != null ? response.content() : "",
                    response.toolCalls()));

            // Execute each tool and append tool result messages
            for (ToolCallRequest tc : response.toolCalls()) {
                ToolCallResult result;
                if (ToolRegistry.DELETE_TIME_LOG.equals(tc.name())) {
                    long deleteCount = toolCallsExecuted.stream()
                            .filter(r -> ToolRegistry.DELETE_TIME_LOG.equals(r.name()))
                            .count();
                    if (deleteCount >= massDeleteLimit) {
                        result = new ToolCallResult(tc.id(),
                                "{\"error\":\"Mass deletion guard: max " + massDeleteLimit
                                        + " entries per turn. Ask the user to confirm before deleting more, then proceed in a follow-up message.\"}");
                    } else {
                        result = toolExecutor.execute(tc);
                    }
                } else {
                    result = toolExecutor.execute(tc);
                }
                toolCallsExecuted.add(new ToolCallRecord(tc.name(), tc.arguments(), result.content()));
                messages.add(ChatMessage.tool(contentForLlm(result.content()), result.toolCallId()));
            }
        }

        log.warn("Max tool iterations reached ({}): {} - tool calls so far: {}",
                MAX_TOOL_ITERATIONS, userMessage, toolCallsExecuted.stream().map(ToolCallRecord::name).toList());
        Object chartData = extractChartDataFromToolCalls(toolCallsExecuted);
        Object timeLogsData = extractTimeLogsFromToolCalls(toolCallsExecuted);
        Map<String, Object> data = new HashMap<>();
        if (chartData != null) data.put("chart", chartData);
        if (timeLogsData != null) data.put("timeLogs", timeLogsData);
        return persistTurnAndBuildResponse(userMessage,
                "I'm sorry, I reached the maximum number of steps. Please try a simpler request.",
                toolCallsExecuted, data.isEmpty() ? null : data, history, contextEntries, startTime, true);
    }

    /**
     * Summarizes context entries and projects for the system prompt to reduce context size.
     * Format: "Selected entries: id1 (ProjectA, 30 min), ...; use these ids for update_time_log/delete_time_log."
     * and "Selected projects: name1 (id1), ...". Truncates at CONTEXT_ENTRIES_MAX / CONTEXT_PROJECTS_MAX.
     */
    private String summarizeContext(List<Map<String, Object>> contextEntries, List<Map<String, Object>> contextProjects) {
        StringBuilder sb = new StringBuilder();
        if (contextEntries != null && !contextEntries.isEmpty()) {
            int max = Math.min(contextEntries.size(), CONTEXT_ENTRIES_MAX);
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < max; i++) {
                Map<String, Object> e = contextEntries.get(i);
                String id = e.get("id") != null ? e.get("id").toString() : "?";
                String projectName = e.get("projectName") != null ? e.get("projectName").toString() : "?";
                Object dur = e.get("durationMinutes");
                String min = dur != null ? dur + " min" : "?";
                parts.add(id + " (" + projectName + ", " + min + ")");
            }
            sb.append("\n\n[Context] Selected entries: ").append(String.join(", ", parts));
            if (contextEntries.size() > max) {
                sb.append(" ... and ").append(contextEntries.size() - max).append(" more");
            }
            sb.append(". Use these ids for update_time_log or delete_time_log when asked.");
        }
        if (contextProjects != null && !contextProjects.isEmpty()) {
            int max = Math.min(contextProjects.size(), CONTEXT_PROJECTS_MAX);
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < max; i++) {
                Map<String, Object> p = contextProjects.get(i);
                String name = p.get("name") != null ? p.get("name").toString() : "?";
                String id = p.get("id") != null ? p.get("id").toString() : "?";
                parts.add(name + " (" + id + ")");
            }
            sb.append("\n\n[Context] Selected projects: ").append(String.join(", ", parts));
            if (contextProjects.size() > max) {
                sb.append(" ... and ").append(contextProjects.size() - max).append(" more");
            }
            sb.append(". When the user refers to 'these projects' or by name, use these ids.");
        }
        return sb.toString();
    }

    /**
     * Builds the [Memories] block for the system prompt: active memories for the default user, limited in count.
     */
    private String buildMemoriesBlock() {
        String userId = memoryService.getDefaultUserId();
        List<Memory> memories = memoryService.findActiveByUserId(userId);
        int limit = Math.min(memories.size(), MEMORIES_INJECT_MAX);
        if (limit == 0) {
            return "\n\n[Memories]\nNo stored memories.";
        }
        StringBuilder sb = new StringBuilder("\n\n[Memories] Stored facts about the user (use these to personalize and avoid re-asking):\n");
        for (int i = 0; i < limit; i++) {
            String fact = memories.get(i).getFactText();
            if (fact != null && !fact.isBlank()) {
                sb.append("- ").append(fact).append("\n");
            }
        }
        if (memories.size() > limit) {
            sb.append("... and ").append(memories.size() - limit).append(" more.");
        }
        return sb.toString();
    }

    /**
     * Extracts the LLM-facing content from a tool result. If the result is dual format (has "llm" and "data"),
     * returns only the "llm" string for the model; otherwise returns the full content (backward compat).
     */
    private String contentForLlm(String toolResultContent) {
        if (toolResultContent == null || toolResultContent.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(toolResultContent);
            if (root.isObject() && root.has("llm") && root.get("llm").isTextual()) {
                return root.get("llm").asText();
            }
        } catch (Exception e) {
            log.trace("Tool result is not dual JSON, using as-is: {}", e.getMessage());
        }
        return toolResultContent;
    }

    /**
     * Returns the payload to use when parsing tool result for data (time_logs, time_log, etc.).
     * If the result is dual format, returns the "data" node; otherwise returns the root.
     */
    private JsonNode resultDataNode(String toolResultContent) {
        if (toolResultContent == null || toolResultContent.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode root = objectMapper.readTree(toolResultContent);
            if (root.isObject() && root.has("data")) {
                return root.get("data");
            }
            return root;
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static String deriveStatus(String assistantMessage, List<ToolCallRecord> toolCalls, boolean maxIterations) {
        if (maxIterations) return "max_iterations";
        if (toolCalls != null) {
            for (ToolCallRecord tc : toolCalls) {
                String r = tc.result();
                if (r != null && r.contains("\"error\":")) {
                    return "tool_error";
                }
            }
        }
        if (assistantMessage == null || assistantMessage.isBlank()) return "empty_result";
        if (assistantMessage.contains("I couldn't generate a response") || assistantMessage.contains("I'm sorry, I couldn't")) {
            return "empty_result";
        }
        return "success";
    }

    private UUID persistTurn(String userMessage, String assistantMessage, List<ToolCallRecord> toolCallsExecuted,
                              Object data, List<ChatHistoryEntry> history, List<Map<String, Object>> contextEntries,
                              long startTime, boolean maxIterations) {
        UUID conversationId = UUID.randomUUID();
        String status = deriveStatus(assistantMessage, toolCallsExecuted, maxIterations);
        long latencyMs = System.currentTimeMillis() - startTime;
        AgentTurn turn = agentTurnService.saveTurn(conversationId, 0, userMessage, assistantMessage,
                toolCallsExecuted, data, llmProperties.model(), status, history, contextEntries, latencyMs);
        return turn.getId();
    }

    private ChatResponse persistTurnAndBuildResponse(String userMessage, String assistantMessage,
                                                     List<ToolCallRecord> toolCallsExecuted, Object data,
                                                     List<ChatHistoryEntry> history, List<Map<String, Object>> contextEntries,
                                                     long startTime, boolean maxIterations) {
        UUID turnId = persistTurn(userMessage, assistantMessage, toolCallsExecuted, data, history, contextEntries, startTime, maxIterations);
        return new ChatResponse(assistantMessage, toolCallsExecuted, data, turnId);
    }

    /**
     * Same as chat() but streams the final assistant text via the writer.
     * Only the last LLM turn (no tool_calls) is streamed; intermediate tool rounds use non-streaming chat().
     */
    public void chatStream(String userMessage, List<ChatHistoryEntry> history,
                          List<Map<String, Object>> contextEntries,
                          List<Map<String, Object>> contextProjects,
                          StreamEventWriter writer) {
        long startTime = System.currentTimeMillis();
        List<ChatMessage> messages = new ArrayList<>();
        String systemPrompt = SYSTEM_PROMPT + summarizeContext(contextEntries, contextProjects) + buildMemoriesBlock();
        messages.add(ChatMessage.system(systemPrompt));
        if (history != null && !history.isEmpty()) {
            for (ChatHistoryEntry e : history) {
                if (e != null && e.role() != null && e.content() != null) {
                    String role = e.role().toLowerCase();
                    if ("user".equals(role)) {
                        messages.add(ChatMessage.user(e.content()));
                    } else if ("assistant".equals(role)) {
                        messages.add(ChatMessage.assistant(e.content()));
                    }
                }
            }
        }
        messages.add(ChatMessage.user(userMessage));

        List<ToolDefinition> tools = toolRegistry.getAllTools();
        List<ToolCallRecord> toolCallsExecuted = new ArrayList<>();
        List<Integer> toolCallIterations = new ArrayList<>();
        boolean streamingClient = llmClient instanceof StreamingLlmClient;
        // Track reasoning phase duration for "Thought for Xs" (only for streaming clients that emit reasoning)
        final long[] reasoningTimeMs = { -1L, -1L };
        // Accumulate assistant text from every turn so the client gets the full interleaved story, not just the last turn
        StringBuilder accumulatedAssistantMessage = new StringBuilder();

        try {
            for (int iterations = 0; iterations < MAX_TOOL_ITERATIONS; iterations++) {
                LlmResponse response;
                if (streamingClient) {
                    Consumer<String> reasoningConsumer = (String text) -> {
                        writer.sendReasoningChunk(text);
                        long now = System.currentTimeMillis();
                        if (reasoningTimeMs[0] < 0) reasoningTimeMs[0] = now;
                        reasoningTimeMs[1] = now;
                    };
                    response = ((StreamingLlmClient) llmClient).chatStream(messages, tools, writer::sendChunk, reasoningConsumer);
                } else {
                    response = llmClient.chat(messages, tools);
                }

                if (iterations > 0) {
                    log.debug("Tool iteration {} for message: {}", iterations, userMessage);
                }

                String turnContent = response.content() != null ? response.content() : "";
                if (!turnContent.isBlank()) {
                    if (accumulatedAssistantMessage.length() > 0) {
                        accumulatedAssistantMessage.append("\n\n");
                    }
                    accumulatedAssistantMessage.append(turnContent.trim());
                }

                if (!response.hasToolCalls()) {
                    Object chartData = extractChartDataFromToolCalls(toolCallsExecuted);
                    Object timeLogsData = extractTimeLogsFromToolCalls(toolCallsExecuted);
                    Map<String, Object> data = new HashMap<>();
                    if (chartData != null) data.put("chart", chartData);
                    if (timeLogsData != null) data.put("timeLogs", timeLogsData);
                    String assistantMessage = accumulatedAssistantMessage.length() > 0
                            ? accumulatedAssistantMessage.toString()
                            : "I'm sorry, I couldn't generate a response.";
                    if (!turnContent.isBlank()) {
                        writer.sendAssistantSegment(turnContent.trim(), iterations);
                    }
                    String reasoningText = response.reasoningSummary() != null && !response.reasoningSummary().isBlank()
                            ? response.reasoningSummary() : null;
                    Long reasoningDurationMs = (reasoningTimeMs[0] >= 0 && reasoningTimeMs[1] >= 0)
                            ? Long.valueOf(reasoningTimeMs[1] - reasoningTimeMs[0]) : null;
                    UUID turnId = persistTurn(userMessage, assistantMessage, toolCallsExecuted,
                            data.isEmpty() ? null : data, history, contextEntries, startTime, false);
                    writer.sendDone(assistantMessage, toolCallsExecuted, toolCallIterations, data.isEmpty() ? null : data, turnId, reasoningText, reasoningDurationMs);
                    return;
                }

                messages.add(ChatMessage.assistantWithToolCalls(
                        response.content() != null ? response.content() : "",
                        response.toolCalls()));

                if (!turnContent.isBlank()) {
                    writer.sendAssistantSegment(turnContent.trim(), iterations);
                }
                for (ToolCallRequest tc : response.toolCalls()) {
                    ToolCallResult result;
                    if (ToolRegistry.DELETE_TIME_LOG.equals(tc.name())) {
                        long deleteCount = toolCallsExecuted.stream()
                                .filter(r -> ToolRegistry.DELETE_TIME_LOG.equals(r.name()))
                                .count();
                        if (deleteCount >= massDeleteLimit) {
                            result = new ToolCallResult(tc.id(),
                                    "{\"error\":\"Mass deletion guard: max " + massDeleteLimit
                                            + " entries per turn. Ask the user to confirm before deleting more, then proceed in a follow-up message.\"}");
                        } else {
                            result = toolExecutor.execute(tc);
                        }
                    } else {
                        result = toolExecutor.execute(tc);
                    }
                    ToolCallRecord record = new ToolCallRecord(tc.name(), tc.arguments(), result.content());
                    toolCallsExecuted.add(record);
                    toolCallIterations.add(iterations);
                    writer.sendToolCall(record, iterations);
                    messages.add(ChatMessage.tool(contentForLlm(result.content()), result.toolCallId()));
                }
            }

            log.warn("Max tool iterations reached ({}): {} - tool calls so far: {}",
                    MAX_TOOL_ITERATIONS, userMessage, toolCallsExecuted.stream().map(ToolCallRecord::name).toList());
            Object chartData = extractChartDataFromToolCalls(toolCallsExecuted);
            Object timeLogsData = extractTimeLogsFromToolCalls(toolCallsExecuted);
            Map<String, Object> data = new HashMap<>();
            if (chartData != null) data.put("chart", chartData);
            if (timeLogsData != null) data.put("timeLogs", timeLogsData);
            String maxStepsMessage = "I'm sorry, I reached the maximum number of steps. Please try a simpler request.";
            UUID turnId = persistTurn(userMessage, maxStepsMessage, toolCallsExecuted, data.isEmpty() ? null : data,
                    history, contextEntries, startTime, true);
            writer.sendDone(maxStepsMessage, toolCallsExecuted, toolCallIterations, data.isEmpty() ? null : data, turnId, null, null);
        } catch (Exception e) {
            log.error("chatStream error: {}", e.getMessage(), e);
            writer.sendError(e.getMessage());
        }
    }

    private Object extractChartDataFromToolCalls(List<ToolCallRecord> toolCallsExecuted) {
        ToolCallRecord lastProposeChart = null;
        for (int i = toolCallsExecuted.size() - 1; i >= 0; i--) {
            ToolCallRecord tc = toolCallsExecuted.get(i);
            if (ToolRegistry.PROPOSE_CHART.equals(tc.name())) {
                lastProposeChart = tc;
                break;
            }
        }
        if (lastProposeChart == null || lastProposeChart.arguments() == null) {
            return null;
        }
        try {
            JsonNode args = objectMapper.readTree(lastProposeChart.arguments());
            Map<String, Object> chart = new HashMap<>();
            if (args.has("chartType")) chart.put("type", args.get("chartType").asText());
            if (args.has("title")) chart.put("title", args.get("title").asText());
            if (args.has("categories")) {
                List<String> cat = new ArrayList<>();
                for (JsonNode c : args.get("categories")) cat.add(c.asText());
                chart.put("categories", cat);
            }
            if (args.has("series")) {
                List<Map<String, Object>> series = new ArrayList<>();
                for (JsonNode s : args.get("series")) {
                    Map<String, Object> item = new HashMap<>();
                    if (s.has("name")) item.put("name", s.get("name").asText());
                    if (s.has("data")) {
                        List<Double> data = new ArrayList<>();
                        for (JsonNode d : s.get("data")) data.add(d.isNumber() ? d.asDouble() : 0);
                        item.put("data", data);
                    }
                    series.add(item);
                }
                chart.put("series", series);
            }
            return chart;
        } catch (Exception e) {
            log.debug("Failed to parse propose_chart arguments: {}", e.getMessage());
            return null;
        }
    }

    private Object extractTimeLogsFromToolCalls(List<ToolCallRecord> toolCallsExecuted) {
        Map<String, Map<String, Object>> patchesById = buildTimeLogPatchesFromToolResults(toolCallsExecuted);

        // Prefer propose_entries (structured display like propose_chart); fall back to raw tool result
        ToolCallRecord lastProposeEntries = null;
        for (int i = toolCallsExecuted.size() - 1; i >= 0; i--) {
            ToolCallRecord tc = toolCallsExecuted.get(i);
            if (ToolRegistry.PROPOSE_ENTRIES.equals(tc.name())) {
                lastProposeEntries = tc;
                break;
            }
        }
        if (lastProposeEntries != null && lastProposeEntries.arguments() != null) {
            try {
                JsonNode args = objectMapper.readTree(lastProposeEntries.arguments());
                JsonNode entriesNode = args.get("entries");
                if (entriesNode != null && entriesNode.isArray()) {
                    List<Map<String, Object>> entries = new ArrayList<>();
                    for (JsonNode entry : entriesNode) {
                        if (!entry.has("durationMinutes") || !entry.has("loggedAt")) continue;
                        Map<String, Object> map = new HashMap<>();
                        map.put("durationMinutes", entry.get("durationMinutes").asInt());
                        map.put("loggedAt", entry.get("loggedAt").asText());
                        if (entry.has("id")) map.put("id", entry.get("id").asText());
                        if (entry.has("projectId")) map.put("projectId", entry.get("projectId").asText());
                        if (entry.has("projectName")) map.put("projectName", entry.get("projectName").asText());
                        if (entry.has("note")) map.put("note", entry.get("note").asText());
                        if (entry.has("billable")) map.put("billable", entry.get("billable").asBoolean());
                        if (entry.has("activityTypeCode")) map.put("activityTypeCode", entry.get("activityTypeCode").asText());
                        if (entry.has("activityTypeLabel")) map.put("activityTypeLabel", entry.get("activityTypeLabel").asText());
                        if (entry.has("dailyRateCents")) map.put("dailyRateCents", entry.get("dailyRateCents").asInt());
                        entries.add(map);
                    }
                    if (!entries.isEmpty()) {
                        applyTimeLogPatches(entries, patchesById);
                        return entries;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse propose_entries arguments: {}", e.getMessage());
            }
        }
        // Fallback: extract from get_time_logs_for_period or get_recent_logs result
        ToolCallRecord lastLogsCall = null;
        for (int i = toolCallsExecuted.size() - 1; i >= 0; i--) {
            ToolCallRecord tc = toolCallsExecuted.get(i);
            if (ToolRegistry.GET_TIME_LOGS_FOR_PERIOD.equals(tc.name()) || ToolRegistry.GET_RECENT_LOGS.equals(tc.name())) {
                lastLogsCall = tc;
                break;
            }
        }
        if (lastLogsCall != null && lastLogsCall.result() != null) {
            try {
                JsonNode root = resultDataNode(lastLogsCall.result());
                if (!root.has("error")) {
                    JsonNode timeLogs = root.get("time_logs");
                    if (timeLogs != null && timeLogs.isArray()) {
                        List<Map<String, Object>> entries = new ArrayList<>();
                        for (JsonNode entry : timeLogs) {
                            Map<String, Object> map = new HashMap<>();
                            if (entry.has("id")) map.put("id", entry.get("id").asText());
                            if (entry.has("projectId")) map.put("projectId", entry.get("projectId").asText());
                            if (entry.has("projectName")) map.put("projectName", entry.get("projectName").asText());
                            if (entry.has("durationMinutes")) map.put("durationMinutes", entry.get("durationMinutes").asInt());
                            if (entry.has("note")) map.put("note", entry.get("note").asText());
                            if (entry.has("loggedAt")) map.put("loggedAt", entry.get("loggedAt").asText());
                            if (entry.has("billable")) map.put("billable", entry.get("billable").asBoolean());
                            if (entry.has("activityTypeCode")) map.put("activityTypeCode", entry.get("activityTypeCode").asText());
                            if (entry.has("activityTypeLabel")) map.put("activityTypeLabel", entry.get("activityTypeLabel").asText());
                            if (entry.has("dailyRateCents")) map.put("dailyRateCents", entry.get("dailyRateCents").asInt());
                            entries.add(map);
                        }
                        if (!entries.isEmpty()) {
                            applyTimeLogPatches(entries, patchesById);
                            return entries;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse time_logs from tool result: {}", e.getMessage());
            }
        }
        // Fallback: extract from create_time_log and update_time_log results (for create/update confirmations)
        List<Map<String, Object>> createdOrUpdatedEntries = new ArrayList<>();
        for (ToolCallRecord tc : toolCallsExecuted) {
            if (!ToolRegistry.CREATE_TIME_LOG.equals(tc.name()) && !ToolRegistry.UPDATE_TIME_LOG.equals(tc.name())) {
                continue;
            }
            if (tc.result() == null) continue;
            try {
                JsonNode root = resultDataNode(tc.result());
                if (root.has("error")) continue;
                JsonNode timeLog = root.get("time_log");
                if (timeLog == null || !timeLog.isObject()) continue;
                if (!timeLog.has("durationMinutes") || !timeLog.has("loggedAt")) continue;
                Map<String, Object> map = new HashMap<>();
                map.put("durationMinutes", timeLog.get("durationMinutes").asInt());
                map.put("loggedAt", timeLog.get("loggedAt").asText());
                if (timeLog.has("id")) map.put("id", timeLog.get("id").asText());
                if (timeLog.has("projectId")) map.put("projectId", timeLog.get("projectId").asText());
                if (timeLog.has("projectName")) map.put("projectName", timeLog.get("projectName").asText());
                if (timeLog.has("note")) map.put("note", timeLog.get("note").asText());
                if (timeLog.has("billable")) map.put("billable", timeLog.get("billable").asBoolean());
                if (timeLog.has("activityTypeCode")) map.put("activityTypeCode", timeLog.get("activityTypeCode").asText());
                if (timeLog.has("activityTypeLabel")) map.put("activityTypeLabel", timeLog.get("activityTypeLabel").asText());
                if (timeLog.has("dailyRateCents")) map.put("dailyRateCents", timeLog.get("dailyRateCents").asInt());
                createdOrUpdatedEntries.add(map);
            } catch (Exception e) {
                log.debug("Failed to parse time_log from {} result: {}", tc.name(), e.getMessage());
            }
        }
        if (!createdOrUpdatedEntries.isEmpty()) {
            return createdOrUpdatedEntries;
        }
        return null;
    }

    /**
     * Builds a map of time_log id -> updated fields from create_time_log and update_time_log tool results.
     * Used to overlay actual post-update state onto entries from propose_entries or get_time_logs,
     * so the table reflects the truth after mass updates (e.g. "set all to billable").
     */
    private Map<String, Map<String, Object>> buildTimeLogPatchesFromToolResults(List<ToolCallRecord> toolCallsExecuted) {
        Map<String, Map<String, Object>> byId = new HashMap<>();
        for (ToolCallRecord tc : toolCallsExecuted) {
            if (!ToolRegistry.CREATE_TIME_LOG.equals(tc.name()) && !ToolRegistry.UPDATE_TIME_LOG.equals(tc.name())) {
                continue;
            }
            if (tc.result() == null) continue;
            try {
                JsonNode root = resultDataNode(tc.result());
                if (root.has("error")) continue;
                JsonNode timeLog = root.get("time_log");
                if (timeLog == null || !timeLog.isObject() || !timeLog.has("id")) continue;
                String id = timeLog.get("id").asText();
                Map<String, Object> map = new HashMap<>();
                if (timeLog.has("durationMinutes")) map.put("durationMinutes", timeLog.get("durationMinutes").asInt());
                if (timeLog.has("loggedAt")) map.put("loggedAt", timeLog.get("loggedAt").asText());
                if (timeLog.has("projectId")) map.put("projectId", timeLog.get("projectId").asText());
                if (timeLog.has("projectName")) map.put("projectName", timeLog.get("projectName").asText());
                if (timeLog.has("note")) map.put("note", timeLog.has("note") && !timeLog.get("note").isNull() ? timeLog.get("note").asText() : "");
                if (timeLog.has("billable")) map.put("billable", timeLog.get("billable").asBoolean());
                if (timeLog.has("activityTypeCode")) map.put("activityTypeCode", timeLog.get("activityTypeCode").asText());
                if (timeLog.has("activityTypeLabel")) map.put("activityTypeLabel", timeLog.get("activityTypeLabel").asText());
                if (timeLog.has("dailyRateCents")) map.put("dailyRateCents", timeLog.get("dailyRateCents").asInt());
                byId.put(id, map);
            } catch (Exception e) {
                log.debug("Failed to parse time_log from {} result: {}", tc.name(), e.getMessage());
            }
        }
        return byId;
    }

    /**
     * Overwrites entry fields with values from update_time_log/create_time_log results when the entry id matches.
     * Ensures the table shows the post-update state (e.g. billable=true) after mass updates.
     */
    private void applyTimeLogPatches(List<Map<String, Object>> entries, Map<String, Map<String, Object>> patchesById) {
        if (patchesById.isEmpty()) return;
        for (Map<String, Object> entry : entries) {
            Object idObj = entry.get("id");
            if (idObj == null) continue;
            Map<String, Object> patch = patchesById.get(idObj.toString());
            if (patch == null) continue;
            for (Map.Entry<String, Object> e : patch.entrySet()) {
                entry.put(e.getKey(), e.getValue());
            }
        }
    }
}
