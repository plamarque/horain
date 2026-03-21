package com.horain.llm

import java.util.function.Consumer

/**
 * Extension of LlmClient for streaming text responses.
 * Used when we need to stream content deltas to the client.
 */
interface StreamingLlmClient : LlmClient {

    /**
     * Stream a chat completion with optional reasoning stream.
     * For each content delta, invokes textConsumer. When the model supports reasoning (e.g. Responses API),
     * invokes reasoningConsumer for each reasoning delta; when null, no reasoning is streamed.
     *
     * @param messages Conversation history
     * @param tools Tool definitions
     * @param textConsumer Receives each content delta as it arrives
     * @param reasoningConsumer Receives each reasoning delta, or null if reasoning is not supported/needed
     * @return Full LlmResponse when stream completes (content, tool_calls, optional reasoningSummary)
     */
    fun chatStream(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        textConsumer: Consumer<String>,
        reasoningConsumer: Consumer<String>?
    ): LlmResponse

    /**
     * Stream a chat completion (no reasoning consumer). Delegates to
     * [chatStream] with null reasoningConsumer.
     */
    fun chatStream(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        textConsumer: Consumer<String>
    ): LlmResponse =
        chatStream(messages, tools, textConsumer, null)
}
