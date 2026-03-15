package com.horain.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Optional;

/**
 * Configuration for LLM client.
 * Uses Spring AI ChatModel when available and configured, otherwise falls back to
 * OpenAiCompatibleLlmClient or PlaceholderLlmClient.
 * When llm.client is not set explicitly, reasoning-capable models (o1, o3, o4-mini, gpt-5, etc.)
 * try the Responses API first; on 400/404/422 we switch to Chat Completions once and keep it.
 */
@Configuration
public class LlmConfig {

    /** Model name prefixes that typically support the Responses API (reasoning). */
    private static final List<String> REASONING_MODEL_PREFIXES = List.of(
            "o1", "o3", "o4-mini", "o4-", "gpt-5-mini", "gpt-5-nano", "gpt-5");

    private static boolean isReasoningModel(String model) {
        if (model == null || model.isBlank()) return false;
        String m = model.toLowerCase().trim();
        for (String prefix : REASONING_MODEL_PREFIXES) {
            if (m.startsWith(prefix)) return true;
        }
        return false;
    }

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
            ComplexityClassifier complexityClassifier,
            RestTemplate restTemplate,
            WebClient webClient,
            ObjectMapper objectMapper,
            @Autowired(required = false) Optional<ChatModel> chatModel,
            @org.springframework.beans.factory.annotation.Value("${llm.client:}") String clientChoice) {
        if (llmProperties.apiKey() == null || llmProperties.apiKey().isBlank()) {
            return new PlaceholderLlmClient();
        }
        // Multi-model routing: three distinct models for simple / complex / very complex
        if (llmProperties.isMultiModelEnabled()) {
            String baseUrl = llmProperties.baseUrl();
            String apiKey = llmProperties.apiKey();
            LlmClient clientSimple = new OpenAiCompatibleLlmClient(
                    baseUrl, apiKey, llmProperties.modelSimple(),
                    restTemplate, webClient, objectMapper);
            LlmClient clientComplex = new OpenAiResponsesLlmClient(
                    baseUrl, apiKey, llmProperties.modelComplex(),
                    llmProperties.reasoningEffortComplex(),
                    webClient, objectMapper);
            LlmClient clientVeryComplex = new OpenAiResponsesLlmClient(
                    baseUrl, apiKey, llmProperties.modelVeryComplex(),
                    llmProperties.reasoningEffortVeryComplex(),
                    webClient, objectMapper);
            return new RoutingLlmClient(
                    complexityClassifier,
                    clientSimple, clientComplex, clientVeryComplex,
                    llmProperties.modelSimple(), llmProperties.modelComplex(), llmProperties.modelVeryComplex());
        }
        String choice = (clientChoice != null) ? clientChoice.trim() : "";
        // Explicit: Responses API only (no fallback)
        if ("openai-responses".equalsIgnoreCase(choice)) {
            return new OpenAiResponsesLlmClient(llmProperties, webClient, objectMapper);
        }
        // Explicit: Chat Completions only (diagnostic)
        if ("openai-compatible".equalsIgnoreCase(choice)) {
            return new OpenAiCompatibleLlmClient(llmProperties, restTemplate, webClient, objectMapper);
        }
        // Spring AI when requested and available
        if ("spring-ai".equalsIgnoreCase(choice) && chatModel.isPresent()) {
            return new SpringAiLlmClient(chatModel.get(), llmProperties);
        }
        // Auto / default: prefer Spring AI if present, else detect by model or fallback
        if (chatModel.isPresent() && (choice.isEmpty() || "spring-ai".equalsIgnoreCase(choice))) {
            return new SpringAiLlmClient(chatModel.get(), llmProperties);
        }
        OpenAiCompatibleLlmClient completions = new OpenAiCompatibleLlmClient(llmProperties, restTemplate, webClient, objectMapper);
        if (isReasoningModel(llmProperties.model())) {
            OpenAiResponsesLlmClient responses = new OpenAiResponsesLlmClient(llmProperties, webClient, objectMapper);
            return new FallbackLlmClient(responses, completions);
        }
        return completions;
    }
}
