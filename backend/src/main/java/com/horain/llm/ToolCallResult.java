package com.horain.llm;

/**
 * Result of executing a tool call.
 */
public record ToolCallResult(
        String toolCallId,
        String content
) {
}
