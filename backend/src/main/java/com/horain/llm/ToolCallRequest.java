package com.horain.llm;

/**
 * A tool call requested by the LLM.
 */
public record ToolCallRequest(
        String id,
        String name,
        String arguments
) {
}
