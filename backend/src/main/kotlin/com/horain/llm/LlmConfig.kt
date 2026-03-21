package com.horain.llm

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.ai.chat.model.ChatModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient
import java.util.Optional

/**
 * Configuration for LLM client.
 * Uses Spring AI ChatModel when available and configured, otherwise falls back to
 * OpenAiCompatibleLlmClient or PlaceholderLlmClient.
 * When llm.client is not set explicitly, reasoning-capable models (o1, o3, o4-mini, gpt-5, etc.)
 * try the Responses API first; on 400/404/422 we switch to Chat Completions once and keep it.
 */
@Configuration
class LlmConfig {

    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()

    @Bean
    fun webClient(): WebClient = WebClient.builder().build()

    @Bean
    fun llmClient(
        llmProperties: LlmProperties,
        complexityClassifier: ComplexityClassifier,
        restTemplate: RestTemplate,
        webClient: WebClient,
        objectMapper: ObjectMapper,
        @Autowired(required = false) chatModel: ChatModel?,
        @org.springframework.beans.factory.annotation.Value("\${llm.client:}") clientChoice: String
    ): LlmClient {
        val chatModelOpt = Optional.ofNullable(chatModel)
        if (llmProperties.apiKey.isNullOrBlank()) {
            return PlaceholderLlmClient()
        }
        if (llmProperties.isMultiModelEnabled()) {
            val baseUrl = llmProperties.resolvedBaseUrl()
            val apiKey = llmProperties.apiKey!!
            val clientSimple = OpenAiCompatibleLlmClient(
                baseUrl, apiKey, llmProperties.modelSimple!!,
                restTemplate, webClient, objectMapper
            )
            val clientComplex = OpenAiResponsesLlmClient(
                baseUrl, apiKey, llmProperties.modelComplex!!,
                llmProperties.resolvedReasoningEffortComplex(),
                webClient, objectMapper
            )
            val clientVeryComplex = OpenAiResponsesLlmClient(
                baseUrl, apiKey, llmProperties.modelVeryComplex!!,
                llmProperties.resolvedReasoningEffortVeryComplex(),
                webClient, objectMapper
            )
            return RoutingLlmClient(
                complexityClassifier,
                clientSimple, clientComplex, clientVeryComplex,
                llmProperties.modelSimple, llmProperties.modelComplex, llmProperties.modelVeryComplex
            )
        }
        val choice = clientChoice.trim()
        if (choice.equals("openai-responses", ignoreCase = true)) {
            return OpenAiResponsesLlmClient(llmProperties, webClient, objectMapper)
        }
        if (choice.equals("openai-compatible", ignoreCase = true)) {
            return OpenAiCompatibleLlmClient(llmProperties, restTemplate, webClient, objectMapper)
        }
        if (choice.equals("spring-ai", ignoreCase = true) && chatModelOpt.isPresent) {
            return SpringAiLlmClient(chatModelOpt.get(), llmProperties)
        }
        if (chatModelOpt.isPresent && (choice.isEmpty() || choice.equals("spring-ai", ignoreCase = true))) {
            return SpringAiLlmClient(chatModelOpt.get(), llmProperties)
        }
        val completions = OpenAiCompatibleLlmClient(llmProperties, restTemplate, webClient, objectMapper)
        return if (isReasoningModel(llmProperties.resolvedModel())) {
            val responses = OpenAiResponsesLlmClient(llmProperties, webClient, objectMapper)
            FallbackLlmClient(responses, completions)
        } else {
            completions
        }
    }

    companion object {
        /** Model name prefixes that typically support the Responses API (reasoning). */
        private val REASONING_MODEL_PREFIXES = listOf(
            "o1", "o3", "o4-mini", "o4-", "gpt-5-mini", "gpt-5-nano", "gpt-5"
        )

        private fun isReasoningModel(model: String?): Boolean {
            if (model.isNullOrBlank()) return false
            val m = model.lowercase().trim()
            return REASONING_MODEL_PREFIXES.any { prefix -> m.startsWith(prefix) }
        }
    }
}
