package com.horain.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

/**
 * Configuration for LLM client.
 * Uses Spring AI ChatModel when available and configured, otherwise falls back to
 * OpenAiCompatibleLlmClient or PlaceholderLlmClient.
 */
@Configuration
public class LlmConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }

    @Bean
    public LlmClient llmClient(
            LlmProperties llmProperties,
            RestTemplate restTemplate,
            WebClient webClient,
            ObjectMapper objectMapper,
            @Autowired(required = false) Optional<ChatModel> chatModel,
            @org.springframework.beans.factory.annotation.Value("${llm.client:spring-ai}") String clientChoice) {
        if (llmProperties.apiKey() == null || llmProperties.apiKey().isBlank()) {
            return new PlaceholderLlmClient();
        }
        // Use RestTemplate client when llm.client=openai-compatible (diagnostic: bypass Spring AI)
        if ("openai-compatible".equalsIgnoreCase(clientChoice)) {
            return new OpenAiCompatibleLlmClient(llmProperties, restTemplate, webClient, objectMapper);
        }
        if (chatModel.isPresent()) {
            return new SpringAiLlmClient(chatModel.get(), llmProperties);
        }
        return new OpenAiCompatibleLlmClient(llmProperties, restTemplate, webClient, objectMapper);
    }
}
