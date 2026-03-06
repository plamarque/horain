package com.horain.llm;

import java.util.List;

/**
 * Response from the LLM chat completion.
 */
public record LlmResponse(
        String content,
        List<ToolCallRequest> toolCalls,
        String finishReason
) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean isFinished() {
        return "stop".equals(finishReason) || "stop".equalsIgnoreCase(finishReason);
    }
}
