package com.horain.chat;

import java.util.List;
import java.util.UUID;

/**
 * Callback to send SSE events to the client during streaming chat.
 * Implementations write to an SseEmitter or similar stream.
 */
public interface StreamEventWriter {

    /**
     * Send a text delta to the client.
     */
    void sendChunk(String text);

    /**
     * Send the final payload and close the stream.
     *
     * @param assistantMessage      Full assistant message text
     * @param toolCalls             Tool calls executed (for debugging/display)
     * @param toolCallIterations    Optional 0-based iteration index per tool (same order as toolCalls)
     * @param data                  Optional chart/timeLogs payload
     * @param turnId                Optional turn id for feedback API
     * @param reasoningText         Optional full reasoning text (when model exposes it)
     * @param reasoningDurationMs   Optional duration of reasoning phase in ms (for "Thought for Xs" header)
     * @param modelName             Optional model name used (for display in trace)
     */
    void sendDone(String assistantMessage, List<ToolCallRecord> toolCalls, List<Integer> toolCallIterations,
                  Object data, UUID turnId, String reasoningText, Long reasoningDurationMs, String modelName);

    /**
     * Send a reasoning text delta (optional; only when the LLM client supports reasoning, e.g. Responses API).
     */
    void sendReasoningChunk(String text);

    /**
     * Signal that the current reasoning phase has ended (e.g. before tool calls or before done).
     * The client should push the accumulated reasoning text so far as a completed phase and start a new one.
     *
     * @param reasoningDurationMs Duration of this phase in ms (for "Thought for Xs" display), or null
     */
    void sendReasoningPhaseDone(Long reasoningDurationMs);

    /**
     * Send the model name used for this request (so the client can display it in the trace).
     * Called once the model is known (e.g. after first LLM invocation when using RoutingLlmClient).
     */
    void sendModelName(String modelName);

    /**
     * Send an error event and close the stream.
     */
    void sendError(String message);

    /**
     * Send a tool call result event (for live trace during streaming).
     * iterationIndex is the 0-based loop index (which "turn" of LLM → tools).
     */
    void sendToolCall(ToolCallRecord record, int iterationIndex);

    /**
     * Send an assistant text segment for a given turn (before the tool calls of that turn).
     * Allows the client to interleave "text then tools" per turn instead of stacking all tools above one blob.
     */
    void sendAssistantSegment(String text, int iterationIndex);
}
