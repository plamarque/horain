package com.horain.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.horain.service.ProjectService
import com.horain.service.TimeLogService
import com.horain.tools.ToolRegistry
import org.slf4j.LoggerFactory
import java.util.function.Consumer

/**
 * Maps Playwright e2e user messages to scripted [LlmResponse] sequences so the real tool loop runs
 * without calling an external LLM. See [E2eStubStreamingLlmClient].
 */
class E2eChatStubScenarioResolver(
    private val objectMapper: ObjectMapper,
    private val timeLogService: TimeLogService,
    private val projectService: ProjectService
) {

    fun resolve(lastUserMessage: String, toolRoundIndex: Int, messages: List<ChatMessage>): LlmResponse {
        val n = normalizeUserText(lastUserMessage)
        return resolveNormalized(n, toolRoundIndex, messages)
    }

    fun streamContentToConsumer(text: String?, textConsumer: Consumer<String>) {
        if (text.isNullOrBlank()) return
        val chunkSize = 24
        var i = 0
        while (i < text.length) {
            val end = minOf(i + chunkSize, text.length)
            textConsumer.accept(text.substring(i, end))
            i = end
        }
    }

    private fun resolveNormalized(n: String, toolRoundIndex: Int, messages: List<ChatMessage>): LlmResponse {
        if (n.equals("yes, delete the entry first", ignoreCase = true)) {
            return when (toolRoundIndex) {
                0 -> deleteEntryAndProjectRound(messages)
                1 -> LlmResponse(
                    "Deleted the time log entry and removed the project.",
                    null,
                    "stop"
                )
                else -> LlmResponse("Done.", null, "stop")
            }
        }

        deleteProjectRegex.find(n)?.let { m ->
            val projectName = m.groupValues[1].trim()
            if (toolRoundIndex == 0) {
                return LlmResponse(
                    "Project **$projectName** has time log entries. I cannot delete it until those " +
                        "entries are removed. Should I delete the entries first? Reply **yes** to confirm.",
                    null,
                    "stop"
                )
            }
        }

        renameRegex.find(n)?.let { m ->
            val oldName = m.groupValues[1].trim()
            val newName = m.groupValues[2].trim()
            return when (toolRoundIndex) {
                0 -> toolRound(
                    tc(
                        "e2e-up",
                        ToolRegistry.UPDATE_PROJECT,
                        jsonArgs(mapOf("id" to oldName, "name" to newName))
                    )
                )
                1 -> LlmResponse(
                    "Renamed the project to **$newName**. The change is saved.",
                    null,
                    "stop"
                )
                else -> LlmResponse("Done.", null, "stop")
            }
        }

        if (n.contains("remember that when i say hatcast i mean hatcast v2", ignoreCase = true)) {
            return when (toolRoundIndex) {
                0 -> toolRound(
                    tc(
                        "e2e-sm",
                        ToolRegistry.STORE_MEMORY,
                        jsonArgs(
                            mapOf(
                                "kind" to "project_disambiguation",
                                "memoryKey" to "HatCast",
                                "factText" to "When the user says HatCast without specifying, they mean HatCast V2."
                            )
                        )
                    )
                )
                1 -> LlmResponse(
                    "Got it — I'll remember that **HatCast** means **HatCast V2** for you.",
                    null,
                    "stop"
                )
                else -> LlmResponse("Okay.", null, "stop")
            }
        }

        if (n.contains("forget my default project", ignoreCase = true)) {
            return when (toolRoundIndex) {
                0 -> toolRound(
                    tc(
                        "e2e-fm",
                        ToolRegistry.FORGET_MEMORY,
                        jsonArgs(mapOf("kind" to "default_project"))
                    )
                )
                1 -> LlmResponse(
                    "I removed your default project preference. I will not use that default anymore.",
                    null,
                    "stop"
                )
                else -> LlmResponse("Okay.", null, "stop")
            }
        }

        if (n.contains("list all my projects", ignoreCase = true)) {
            return when (toolRoundIndex) {
                0 -> toolRound(tc("e2e-lp", ToolRegistry.LIST_PROJECTS, "{}"))
                1 -> LlmResponse(
                    "Here are your projects (see the tool result above).",
                    null,
                    "stop"
                )
                else -> LlmResponse("Done.", null, "stop")
            }
        }

        if (n.contains("what's the time", ignoreCase = true) || n.contains("whats the time", ignoreCase = true)) {
            return when (toolRoundIndex) {
                0 -> toolRound(tc("e2e-gt", ToolRegistry.GET_CURRENT_DATETIME, "{}"))
                1 -> LlmResponse(
                    "Here is the current server time from the tool above.",
                    null,
                    "stop"
                )
                else -> LlmResponse("Done.", null, "stop")
            }
        }

        if (Regex("(?i)^30 minutes on HatCast\\s*$").matches(n)) {
            if (toolRoundIndex == 0) {
                return LlmResponse(
                    "I found two similar projects: **HatCast V1** and **HatCast V2**. Which one should I use?",
                    null,
                    "stop"
                )
            }
        }

        if (Regex("(?i)^40 minutes on (ZzzUnknown\\d+)$").matches(n)) {
            val unknown = Regex("(?i)^40 minutes on (ZzzUnknown\\d+)$").find(n)!!.groupValues[1]
            if (toolRoundIndex == 0) {
                return LlmResponse(
                    "I don't know a project named **$unknown** yet. Should I create it and log 40 minutes?",
                    null,
                    "stop"
                )
            }
        }

        val zzzDur = Regex("(?i)^15 minutes on (ZzzDurEst\\d+)$").find(n)
        if (zzzDur != null) {
            val pname = zzzDur.groupValues[1]
            return when (toolRoundIndex) {
                0 -> toolRound(
                    tc(
                        "e2e-cp",
                        ToolRegistry.CREATE_PROJECT,
                        jsonArgs(
                            mapOf(
                                "name" to pname,
                                "description" to "e2e duration estimate",
                                "billable" to true
                            )
                        )
                    ),
                    tc(
                        "e2e-ctl",
                        ToolRegistry.CREATE_TIME_LOG,
                        jsonArgs(
                            mapOf(
                                "projectId" to pname,
                                "durationMinutes" to 15,
                                "note" to "e2e"
                            )
                        )
                    )
                )
                1 -> LlmResponse(
                    "Logged **15 minutes** on **$pname** and created the project.",
                    null,
                    "stop"
                )
                else -> LlmResponse("Done.", null, "stop")
            }
        }

        val workedOn = Regex("(?i)^I worked on (ZzzDurEst\\d+) all morning$").find(n)
        if (workedOn != null) {
            val pname = workedOn.groupValues[1]
            if (toolRoundIndex == 0) {
                return LlmResponse(
                    "How long did you work on **$pname**? Please give an estimate in minutes or hours.",
                    null,
                    "stop"
                )
            }
        }

        if (n.contains("please log exactly 15 minutes on HatCast V2", ignoreCase = true)) {
            return hatCastTimeLogRound("HatCast V2", 15, "e2e spec", toolRoundIndex)
        }

        if (Regex("(?i)^15 minutes on HatCast V1\\s*$").matches(n) ||
            Regex("(?i)^15 minutes on HatCast V1\\b").matches(n)
        ) {
            return hatCastTimeLogRound("HatCast V1", 15, "e2e", toolRoundIndex)
        }

        if (isThirtyMinutesHatCastV1(n)) {
            return hatCastTimeLogRound("HatCast V1", 30, noteFromHatCastMessage(n), toolRoundIndex)
        }

        if (n.equals("hello", ignoreCase = true) || n.startsWith("hello ", ignoreCase = true)) {
            if (toolRoundIndex == 0) {
                return LlmResponse(
                    "Hello! How can I help you today?",
                    null,
                    "stop"
                )
            }
        }

        if (n.equals("yes", ignoreCase = true)) {
            if (toolRoundIndex == 0) {
                return LlmResponse("Okay.", null, "stop")
            }
        }

        log.warn("E2E chat stub: no scenario for message (round={}): {}", toolRoundIndex, n.take(120))
        return LlmResponse(
            "E2E stub: add a scenario in E2eChatStubScenarioResolver for this message.",
            null,
            "stop"
        )
    }

    private fun hatCastTimeLogRound(
        projectName: String,
        minutes: Int,
        note: String,
        toolRoundIndex: Int
    ): LlmResponse {
        return when (toolRoundIndex) {
            0 -> toolRound(
                tc(
                    "e2e-ctl",
                    ToolRegistry.CREATE_TIME_LOG,
                    jsonArgs(
                        mapOf(
                            "projectId" to projectName,
                            "durationMinutes" to minutes,
                            "note" to note
                        )
                    )
                )
            )
            1 -> LlmResponse(
                "Logged **$minutes minutes** on **$projectName**.",
                null,
                "stop"
            )
            else -> LlmResponse("Done.", null, "stop")
        }
    }

    private fun deleteEntryAndProjectRound(messages: List<ChatMessage>): LlmResponse {
        val projectName = extractDeleteProjectName(messages) ?: return LlmResponse(
            "Could not determine which project to delete from the conversation.",
            null,
            "stop"
        )
        val projectId = projectService.findAll().firstOrNull { it.name == projectName }?.id
            ?: return LlmResponse(
                "Project **$projectName** was not found.",
                null,
                "stop"
            )
        val recent = timeLogService.findRecentLogs(50)
        val log = recent.firstOrNull {
            it.projectId == projectId && it.note?.contains("e2e delete flow", ignoreCase = true) == true
        } ?: recent.firstOrNull { it.projectId == projectId }
        val entryId = log?.id?.toString()
            ?: return LlmResponse(
                "No time log entry found for project **$projectName**.",
                null,
                "stop"
            )
        return toolRound(
            tc("e2e-dtl", ToolRegistry.DELETE_TIME_LOG, jsonArgs(mapOf("id" to entryId))),
            tc("e2e-dp", ToolRegistry.DELETE_PROJECT, jsonArgs(mapOf("id" to projectName)))
        )
    }

    private fun extractDeleteProjectName(messages: List<ChatMessage>): String? {
        for (m in messages) {
            if (m.role.equals("user", ignoreCase = true)) {
                val t = m.content?.trim() ?: continue
                deleteProjectRegex.find(t)?.let { return it.groupValues[1].trim() }
            }
        }
        return null
    }

    private fun jsonArgs(map: Map<String, Any?>): String = objectMapper.writeValueAsString(map)

    private fun toolRound(vararg calls: ToolCallRequest): LlmResponse =
        LlmResponse("", calls.toList(), "tool_calls")

    private fun tc(id: String, name: String, args: String): ToolCallRequest =
        ToolCallRequest(id, name, args)

    private fun isThirtyMinutesHatCastV1(n: String): Boolean {
        val lower = n.lowercase()
        if (!lower.contains("hatcast v1")) return false
        if (Regex("(?i)\\b15\\s*minutes\\b").containsMatchIn(lower)) return false
        return lower.contains("30 minutes") ||
            lower.contains("30 min") ||
            lower.contains("30 minutes sur") ||
            lower.contains("30 min sur") ||
            (lower.contains("30") && lower.contains("minute") && lower.contains("hatcast v1"))
    }

    private fun noteFromHatCastMessage(n: String): String {
        val trimmed = n.trim()
        return if (trimmed.length > 80) trimmed.take(80) + "…" else trimmed
    }

    private fun normalizeUserText(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ")

    companion object {
        private val log = LoggerFactory.getLogger(E2eChatStubScenarioResolver::class.java)
        private val deleteProjectRegex = Regex("(?i)^delete project\\s+(.+)$")
        private val renameRegex = Regex("(?i)^rename\\s+(.+?)\\s+to\\s+(.+)$")

        /**
         * Tool rounds completed after the last user message (each assistant message with tool calls counts once).
         */
        fun countToolRoundsAfterLastUser(messages: List<ChatMessage>): Int {
            val idx = messages.indexOfLast { it.role.equals("user", ignoreCase = true) }
            if (idx < 0) return 0
            return messages.drop(idx + 1).count { m ->
                m.role.equals("assistant", ignoreCase = true) && !m.toolCalls.isNullOrEmpty()
            }
        }

        fun lastUserContent(messages: List<ChatMessage>): String {
            val last = messages.lastOrNull { it.role.equals("user", ignoreCase = true) }
            return last?.content?.trim().orEmpty()
        }
    }
}
