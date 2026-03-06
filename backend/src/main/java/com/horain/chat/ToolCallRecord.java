package com.horain.chat;

/**
 * Record of a tool call for the response.
 */
public record ToolCallRecord(String name, String arguments, String result) {
}
