package com.horain.llm;

import java.util.List;

/**
 * Response from the LLM chat completion.
 *
 * @param content         Assistant text content
 * @param toolCalls       Tool calls if any (e.g. for tool-use models)
 * @param finishReason    Finish reason from API (stop, length, etc.)
 * @param reasoningSummary Optional reasoning text when the model exposes it (e.g. Responses API)
 */
public record LlmResponse(
        String content,
        List<ToolCallRequest> toolCalls,
        String finishReason,
        String reasoningSummary
) {

    /** Constructor for callers that do not have reasoning (backward compatible). */
    public LlmResponse(String content, List<ToolCallRequest> toolCalls, String finishReason) {
        this(content, toolCalls, finishReason, null);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean isFinished() {
        return "stop".equals(finishReason) || "stop".equalsIgnoreCase(finishReason);
    }
}
