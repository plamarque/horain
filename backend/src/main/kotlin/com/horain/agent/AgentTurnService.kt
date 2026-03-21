package com.horain.agent

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.horain.chat.ChatHistoryEntry
import com.horain.chat.ToolCallRecord
import com.horain.model.AgentTurn
import com.horain.repository.AgentTurnRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Persists agent conversation turns for trace and eval pipeline.
 */
@Service
class AgentTurnService(
    private val repository: AgentTurnRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${horain.system-prompt-version:v1}") private val systemPromptVersion: String
) {

    /**
     * Saves one conversation turn and returns the entity (with id) for client feedback.
     */
    @Transactional
    fun saveTurn(
        conversationId: UUID,
        turnIndex: Int,
        userMessage: String?,
        assistantMessage: String?,
        toolCalls: List<ToolCallRecord>?,
        uiPayload: Any?,
        model: String?,
        status: String?,
        historySnapshot: List<ChatHistoryEntry>?,
        contextEntries: List<Map<String, Any?>>?,
        latencyMs: Long?
    ): AgentTurn {
        val turn = AgentTurn()
        turn.id = UUID.randomUUID()
        turn.conversationId = conversationId
        turn.turnIndex = turnIndex
        turn.userMessage = userMessage ?: ""
        turn.assistantMessage = assistantMessage
        turn.toolCallsJson = serializeToolCalls(toolCalls)
        turn.toolResultsJson = null
        turn.uiPayloadJson = serialize(uiPayload)
        turn.systemPromptVersion = systemPromptVersion
        turn.model = model
        turn.status = status
        turn.historySnapshotJson = serializeHistory(historySnapshot)
        turn.contextEntriesJson = serialize(contextEntries)
        turn.latencyMs = latencyMs
        turn.createdAt = Instant.now()
        val saved = repository.save(turn)
        log.debug("Saved agent turn {} for conversation {}", saved.id, conversationId)
        return saved
    }

    /**
     * Stores the external observability platform run id (e.g. LangSmith) after async export completes.
     */
    @Transactional
    fun updateExternalTraceId(turnId: UUID, externalTraceId: String) {
        val turn = repository.findById(turnId).orElseThrow {
            IllegalArgumentException("Turn not found: $turnId")
        }
        turn.externalTraceId = externalTraceId
        repository.save(turn)
        log.debug("Updated external_trace_id for turn {}", turnId)
    }

    fun findById(turnId: UUID): AgentTurn? =
        repository.findById(turnId).orElse(null)

    fun countTurnsInConversation(conversationId: UUID): Long =
        repository.countByConversationId(conversationId)

    private fun serializeToolCalls(toolCalls: List<ToolCallRecord>?): String? {
        if (toolCalls.isNullOrEmpty()) return null
        return try {
            val list = toolCalls.map { tc ->
                mapOf(
                    "name" to tc.name,
                    "arguments" to (tc.arguments ?: ""),
                    "result" to (tc.result ?: "")
                )
            }
            objectMapper.writeValueAsString(list)
        } catch (e: JsonProcessingException) {
            log.warn("Failed to serialize tool calls: {}", e.message)
            null
        }
    }

    private fun serializeHistory(history: List<ChatHistoryEntry>?): String? {
        if (history.isNullOrEmpty()) return null
        val from = maxOf(0, history.size - MAX_HISTORY_SNAPSHOT_MESSAGES)
        val slice = history.subList(from, history.size)
        return try {
            objectMapper.writeValueAsString(slice)
        } catch (e: JsonProcessingException) {
            log.warn("Failed to serialize history snapshot: {}", e.message)
            null
        }
    }

    private fun serialize(o: Any?): String? {
        if (o == null) return null
        return try {
            objectMapper.writeValueAsString(o)
        } catch (e: JsonProcessingException) {
            log.warn("Failed to serialize payload: {}", e.message)
            null
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AgentTurnService::class.java)
        private const val MAX_HISTORY_SNAPSHOT_MESSAGES = 10
    }
}
