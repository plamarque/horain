package com.horain.llm;

import java.util.Map;

/**
 * Definition of a tool for the LLM (OpenAI function-calling format).
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters
) {
}
