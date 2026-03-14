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
     */
    void sendDone(String assistantMessage, List<ToolCallRecord> toolCalls, List<Integer> toolCallIterations,
                  Object data, UUID turnId, String reasoningText, Long reasoningDurationMs);

    /**
     * Send a reasoning text delta (optional; only when the LLM client supports reasoning, e.g. Responses API).
     */
    void sendReasoningChunk(String text);

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
