package com.horain.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.horain.repository.AgentFeedbackRepository
import com.horain.repository.AgentTurnRepository
import org.springframework.stereotype.Service
import java.util.LinkedHashMap
import java.util.UUID

/**
 * Builds eval candidates (turns with thumbs-down or tool/empty errors) for export.
 * Used by the admin HTTP endpoint and optionally by the CLI export runner.
 */
@Service
class ExportEvalCandidatesService(
    private val turnRepository: AgentTurnRepository,
    private val feedbackRepository: AgentFeedbackRepository,
    val objectMapper: ObjectMapper
) {

    /**
     * Returns one row (map) per eval candidate, sorted by created_at descending.
     */
    fun getCandidates(): List<Map<String, Any?>> {
        val turnIds = linkedSetOf<UUID>()
        val downFeedbacks = feedbackRepository.findByRating("down")
        for (f in downFeedbacks) {
            turnIds.add(f.turnId!!)
        }
        val errorTurns = turnRepository.findByStatusIn(
            listOf("tool_error", "empty_result", "max_iterations")
        )
        for (t in errorTurns) {
            turnIds.add(t.id!!)
        }
        val feedbackByTurn = downFeedbacks.associateBy { it.turnId!! }
        val turns = mutableListOf<com.horain.model.AgentTurn>()
        for (id in turnIds) {
            turnRepository.findById(id).ifPresent { turns.add(it) }
        }
        turns.sortByDescending { it.createdAt }
        val rows = mutableListOf<Map<String, Any?>>()
        for (turn in turns) {
            val row = LinkedHashMap<String, Any?>()
            row["source_turn_id"] = turn.id.toString()
            row["conversation_id"] = turn.conversationId.toString()
            row["user_message"] = turn.userMessage
            row["assistant_message"] = turn.assistantMessage
            row["system_prompt_version"] = turn.systemPromptVersion
            row["model"] = turn.model
            row["status"] = turn.status
            row["created_at"] = turn.createdAt?.toString()
            if (!turn.toolCallsJson.isNullOrBlank()) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val toolCalls = objectMapper.readValue(turn.toolCallsJson, List::class.java) as List<Map<String, String>>
                    row["tool_calls"] = toolCalls
                } catch (_: Exception) {
                    row["tool_calls_raw"] = turn.toolCallsJson
                }
            }
            val fb = feedbackByTurn[turn.id]
            if (fb != null) {
                row["feedback"] = fb.rating
                row["feedback_reason"] = fb.reasonCode
                row["feedback_comment"] = fb.comment
            } else {
                row["feedback"] = null
            }
            row["expected_behavior"] = ""
            row["eval_family"] = ""
            row["assertion_strategy"] = "deterministic"
            rows.add(row)
        }
        return rows
    }
}
