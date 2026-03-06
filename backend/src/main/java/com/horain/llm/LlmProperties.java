package com.horain.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for LLM integration.
 * Maps to LLM_BASE_URL, LLM_API_KEY, LLM_MODEL (or llm.base-url, llm.api-key, llm.model).
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(String baseUrl, String apiKey, String model) {

    public String baseUrl() {
        return baseUrl != null && !baseUrl.isBlank() ? baseUrl : "https://api.openai.com/v1";
    }

    public String model() {
        return model != null && !model.isBlank() ? model : "gpt-4o-mini";
    }
}
