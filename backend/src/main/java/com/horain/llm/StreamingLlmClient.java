package com.horain.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * Extension of LlmClient for streaming text responses.
 * Used when we need to stream content deltas to the client.
 */
public interface StreamingLlmClient extends LlmClient {

    /**
     * Stream a chat completion. Calls the LLM with streaming enabled.
     * For each content delta, invokes textConsumer.
     * When the response includes tool_calls, they are accumulated and returned (no streaming of tool args).
     *
     * @param messages   Conversation history
     * @param tools      Tool definitions
     * @param textConsumer Receives each content delta as it arrives
     * @return Full LlmResponse when stream completes (with content accumulated, and tool_calls if any)
     */
    LlmResponse chatStream(
            List<ChatMessage> messages,
            List<ToolDefinition> tools,
            Consumer<String> textConsumer
    );
}
