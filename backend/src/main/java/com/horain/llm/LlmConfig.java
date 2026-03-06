package com.horain.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for LLM client. Uses OpenAI-compatible client when LLM_API_KEY is set
 * and non-empty, otherwise falls back to placeholder.
 */
@Configuration
public class LlmConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public LlmClient llmClient(LlmProperties llmProperties, RestTemplate restTemplate, ObjectMapper objectMapper) {
        if (llmProperties.apiKey() != null && !llmProperties.apiKey().isBlank()) {
            return new OpenAiCompatibleLlmClient(llmProperties, restTemplate, objectMapper);
        }
        return new PlaceholderLlmClient();
    }
}
