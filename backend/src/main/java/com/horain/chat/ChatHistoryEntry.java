package com.horain.chat;

/**
 * A single message from conversation history.
 */
public record ChatHistoryEntry(String role, String content) {
}
