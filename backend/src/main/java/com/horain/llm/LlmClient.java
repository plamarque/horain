package com.horain.llm;

import java.util.List;

/**
 * Abstraction for LLM chat completion with tool calling support.
 * Implementations can connect to OpenAI, OpenRouter, or local models.
 */
public interface LlmClient {

    /**
     * Send messages to the LLM and get a response.
     *
     * @param messages   Conversation history including the latest user message
     * @param tools      Tool definitions (name, description, parameters schema)
     * @return Chat completion response with either content or tool calls
     */
    LlmResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools);

    /**
     * Whether the client is configured and ready to make real API calls.
     */
    boolean isConfigured();
}
