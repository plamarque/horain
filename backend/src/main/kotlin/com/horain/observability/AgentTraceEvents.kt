package com.horain.observability

import java.util.UUID

/**
 * One model reasoning segment within a turn (e.g. one LLM round before tools or final answer).
 */
data class ReasoningPhase(
    val text: String,
    /** Wall-clock duration of this reasoning stream when known (streaming); null otherwise. */
    val durationMs: Long?
)

/**
 * One tool invocation within a turn (for LangSmith child runs).
 */
data class ToolCallTrace(
    val name: String,
    val arguments: String,
    val result: String
)

/**
 * Fired after a turn is persisted to agent_turn. Used for optional external observability.
 */
data class TurnCompletedEvent(
    val turnId: UUID,
    val conversationId: UUID,
    val userMessage: String,
    val assistantMessage: String?,
    val toolCallNames: List<String>,
    val model: String?,
    val status: String?,
    /** Wall-clock start of the turn (System.currentTimeMillis at request start). */
    val startTimeEpochMs: Long,
    val latencyMs: Long?,
    /** Per-LLM-round reasoning text (e.g. Responses API); used for LangSmith child runs. */
    val reasoningPhases: List<ReasoningPhase> = emptyList(),
    /** Tool calls in execution order; used for LangSmith child runs (run_type tool). */
    val toolCallSteps: List<ToolCallTrace> = emptyList()
)

/**
 * User feedback on a turn (after DB save).
 */
data class FeedbackEvent(
    val turnId: UUID,
    val rating: String,
    val reasonCode: String?,
    val comment: String?
)

/**
 * Pluggable sink for agent observability (built-in no-op, LangSmith, future Langfuse).
 */
interface AgentTraceSink {
    fun onTurnCompleted(event: TurnCompletedEvent)
    fun onFeedback(event: FeedbackEvent)
}
