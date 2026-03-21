package com.horain.llm

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for LLM integration.
 * Maps to LLM_BASE_URL, LLM_API_KEY, LLM_MODEL (or llm.base-url, llm.api-key, llm.model).
 * When LLM_MODEL_SIMPLE, LLM_MODEL_COMPLEX, LLM_MODEL_VERY_COMPLEX are all set, multi-model
 * routing is enabled (RoutingLlmClient with three distinct clients).
 */
@ConfigurationProperties(prefix = "llm")
class LlmProperties {
    var baseUrl: String? = null
    var apiKey: String? = null
    var model: String? = null
    var modelSimple: String? = null
    var modelComplex: String? = null
    var modelVeryComplex: String? = null
    var modelSummary: String? = null
    var reasoningEffortComplex: String? = null
    var reasoningEffortVeryComplex: String? = null

    fun resolvedBaseUrl(): String =
        baseUrl?.takeIf { it.isNotBlank() } ?: "https://api.openai.com/v1"

    fun resolvedModel(): String =
        model?.takeIf { it.isNotBlank() } ?: "gpt-4o-mini"

    /** Model used for one-shot reasoning summaries (Cursor-style). When empty, uses resolvedModel(). */
    fun resolvedModelSummary(): String =
        modelSummary?.takeIf { it.isNotBlank() } ?: resolvedModel()

    fun resolvedReasoningEffortComplex(): String =
        reasoningEffortComplex?.takeIf { it.isNotBlank() } ?: "medium"

    fun resolvedReasoningEffortVeryComplex(): String =
        reasoningEffortVeryComplex?.takeIf { it.isNotBlank() } ?: "high"

    /** True when multi-model routing is enabled (all three level models are set). */
    fun isMultiModelEnabled(): Boolean =
        !modelSimple.isNullOrBlank() &&
            !modelComplex.isNullOrBlank() &&
            !modelVeryComplex.isNullOrBlank()
}
