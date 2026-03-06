package com.horain.llm;

import java.util.List;

/**
 * Placeholder LLM client when no real provider is configured.
 * Returns canned responses for testing without an API key.
 */
public class PlaceholderLlmClient implements LlmClient {

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public LlmResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        String lastUserMessage = "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if ("user".equals(m.role()) && m.content() != null && !m.content().isBlank()) {
                lastUserMessage = m.content();
                break;
            }
        }

        return new LlmResponse(
                "LLM is not configured. Set LLM_API_KEY (and optionally LLM_BASE_URL, LLM_MODEL) to enable the assistant. " +
                "Your message was: \"" + lastUserMessage + "\"",
                null,
                "stop"
        );
    }
}
