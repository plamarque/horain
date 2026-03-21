package com.horain.llm

/**
 * Placeholder LLM client when no real provider is configured.
 * Returns canned responses for testing without an API key.
 */
class PlaceholderLlmClient : LlmClient {

    override fun isConfigured(): Boolean = false

    override fun chat(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse {
        var lastUserMessage = ""
        for (i in messages.indices.reversed()) {
            val m = messages[i]
            if (m.role.equals("user", ignoreCase = true) && !m.content.isNullOrBlank()) {
                lastUserMessage = m.content!!
                break
            }
        }
        return LlmResponse(
            "LLM is not configured. Set LLM_API_KEY (and optionally LLM_BASE_URL, LLM_MODEL) to enable the assistant. " +
                "Your message was: \"$lastUserMessage\"",
            null,
            "stop"
        )
    }
}
