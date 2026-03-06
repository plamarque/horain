package com.horain.llm;

import java.util.List;

/**
 * A message in the chat conversation.
 * For tool-role messages, toolCallId must be set.
 */
public record ChatMessage(
        String role,
        String content,
        List<ToolCallRequest> toolCalls,
        String toolCallId
) {

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null);
    }

    public static ChatMessage assistantWithToolCalls(String content, List<ToolCallRequest> toolCalls) {
        return new ChatMessage("assistant", content, toolCalls, null);
    }

    public static ChatMessage tool(String content, String toolCallId) {
        return new ChatMessage("tool", content, null, toolCallId);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null);
    }
}
