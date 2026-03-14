package com.horain.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * Extension of LlmClient for streaming text responses.
 * Used when we need to stream content deltas to the client.
 */
public interface StreamingLlmClient extends LlmClient {

    /**
     * Stream a chat completion with optional reasoning stream.
     * For each content delta, invokes textConsumer. When the model supports reasoning (e.g. Responses API),
     * invokes reasoningConsumer for each reasoning delta; when null, no reasoning is streamed.
     *
     * @param messages          Conversation history
     * @param tools             Tool definitions
     * @param textConsumer      Receives each content delta as it arrives
     * @param reasoningConsumer Receives each reasoning delta, or null if reasoning is not supported/needed
     * @return Full LlmResponse when stream completes (content, tool_calls, optional reasoningSummary)
     */
    LlmResponse chatStream(
            List<ChatMessage> messages,
            List<ToolDefinition> tools,
            Consumer<String> textConsumer,
            Consumer<String> reasoningConsumer
    );

    /**
     * Stream a chat completion (no reasoning consumer). Delegates to
     * {@link #chatStream(List, List, Consumer, Consumer)} with null reasoningConsumer.
     */
    default LlmResponse chatStream(
            List<ChatMessage> messages,
            List<ToolDefinition> tools,
            Consumer<String> textConsumer
    ) {
        return chatStream(messages, tools, textConsumer, null);
    }
}
