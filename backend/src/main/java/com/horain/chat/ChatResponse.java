package com.horain.chat;

import java.util.List;
import java.util.UUID;

/**
 * Response from the chat endpoint.
 */
public record ChatResponse(
        String assistantMessage,
        List<ToolCallRecord> toolCalls,
        Object data,
        UUID turnId
) {
}
