package com.horain.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for LLM integration.
 * Maps to LLM_BASE_URL, LLM_API_KEY, LLM_MODEL (or llm.base-url, llm.api-key, llm.model).
 * When LLM_MODEL_SIMPLE, LLM_MODEL_COMPLEX, LLM_MODEL_VERY_COMPLEX are all set, multi-model
 * routing is enabled (RoutingLlmClient with three distinct clients).
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String baseUrl,
        String apiKey,
        String model,
        String modelSimple,
        String modelComplex,
        String modelVeryComplex,
        String modelSummary,
        String reasoningEffortComplex,
        String reasoningEffortVeryComplex) {

    public String baseUrl() {
        return baseUrl != null && !baseUrl.isBlank() ? baseUrl : "https://api.openai.com/v1";
    }

    public String model() {
        return model != null && !model.isBlank() ? model : "gpt-4o-mini";
    }

    /** Model used for one-shot reasoning summaries (Cursor-style). When empty, uses model(). */
    public String modelSummary() {
        return modelSummary != null && !modelSummary.isBlank() ? modelSummary : model();
    }

    public String reasoningEffortComplex() {
        return reasoningEffortComplex != null && !reasoningEffortComplex.isBlank()
                ? reasoningEffortComplex : "medium";
    }

    public String reasoningEffortVeryComplex() {
        return reasoningEffortVeryComplex != null && !reasoningEffortVeryComplex.isBlank()
                ? reasoningEffortVeryComplex : "high";
    }

    /**
     * True when multi-model routing is enabled (all three level models are set).
     */
    public boolean isMultiModelEnabled() {
        return modelSimple != null && !modelSimple.isBlank()
                && modelComplex != null && !modelComplex.isBlank()
                && modelVeryComplex != null && !modelVeryComplex.isBlank();
    }
}
