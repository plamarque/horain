package com.horain.llm

/**
 * Abstraction for LLM chat completion with tool calling support.
 * Implementations can connect to OpenAI, OpenRouter, or local models.
 */
interface LlmClient {

    /**
     * Send messages to the LLM and get a response.
     *
     * @param messages Conversation history including the latest user message
     * @param tools Tool definitions (name, description, parameters schema)
     * @return Chat completion response with either content or tool calls
     */
    fun chat(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse

    /**
     * Whether the client is configured and ready to make real API calls.
     */
    fun isConfigured(): Boolean
}
