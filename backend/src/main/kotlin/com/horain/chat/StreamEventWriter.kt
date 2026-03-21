package com.horain.chat

import java.util.UUID

/**
 * Callback to send SSE events to the client during streaming chat.
 * Implementations write to an SseEmitter or similar stream.
 */
interface StreamEventWriter {

    /**
     * Send a text delta to the client.
     */
    fun sendChunk(text: String)

    /**
     * Send the final payload and close the stream.
     *
     * @param assistantMessage Full assistant message text
     * @param toolCalls Tool calls executed (for debugging/display)
     * @param toolCallIterations Optional 0-based iteration index per tool (same order as toolCalls)
     * @param data Optional chart/timeLogs payload
     * @param turnId Optional turn id for feedback API
     * @param reasoningText Optional full reasoning text (when model exposes it)
     * @param reasoningDurationMs Optional duration of reasoning phase in ms (for "Thought for Xs" header)
     * @param modelName Optional model name used (for display in trace)
     */
    fun sendDone(
        assistantMessage: String,
        toolCalls: List<ToolCallRecord>,
        toolCallIterations: List<Int>?,
        data: Any?,
        turnId: UUID?,
        reasoningText: String?,
        reasoningDurationMs: Long?,
        modelName: String?
    )

    /**
     * Send a reasoning text delta (optional; only when the LLM client supports reasoning, e.g. Responses API).
     */
    fun sendReasoningChunk(text: String)

    /**
     * Signal that the current reasoning phase has ended (e.g. before tool calls or before done).
     * The client should push the accumulated reasoning text so far as a completed phase and start a new one.
     *
     * @param reasoningDurationMs Duration of this phase in ms (for "Thought for Xs" display), or null
     */
    fun sendReasoningPhaseDone(reasoningDurationMs: Long?)

    /**
     * Send the model name used for this request (so the client can display it in the trace).
     * Called once the model is known (e.g. after first LLM invocation when using RoutingLlmClient).
     */
    fun sendModelName(modelName: String)

    /**
     * Send an error event and close the stream.
     */
    fun sendError(message: String)

    /**
     * Send a tool call result event (for live trace during streaming).
     * iterationIndex is the 0-based loop index (which "turn" of LLM → tools).
     */
    fun sendToolCall(record: ToolCallRecord, iterationIndex: Int)

    /**
     * Send an assistant text segment for a given turn (before the tool calls of that turn).
     * Allows the client to interleave "text then tools" per turn instead of stacking all tools above one blob.
     */
    fun sendAssistantSegment(text: String, iterationIndex: Int)
}
