package com.horain.chat

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.horain.llm.LlmProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * One-shot summarization of reasoning text (Cursor-style: one short sentence below "Thought for Xs").
 * Uses a lightweight LLM call to the same API with a small prompt; does not block the main stream.
 */
@Service
class ReasoningSummarizerService(
    private val llmProperties: LlmProperties,
    private val webClient: WebClient,
    private val objectMapper: ObjectMapper
) {

    /**
     * Returns a one-sentence summary of the reasoning text, or null if summarization is not configured or fails.
     */
    fun summarize(reasoningText: String?): String? {
        if (reasoningText.isNullOrBlank()) {
            return null
        }
        if (llmProperties.apiKey.isNullOrBlank()) {
            return null
        }
        val trimmed = reasoningText.trim()
        if (trimmed.length < MIN_TEXT_LENGTH) {
            return null
        }
        val toSend =
            if (trimmed.length > MAX_TEXT_LENGTH) {
                trimmed.substring(0, MAX_TEXT_LENGTH) + "…"
            } else {
                trimmed
            }

        val url = llmProperties.resolvedBaseUrl().replace(Regex("/$"), "") + "/chat/completions"
        val body: ObjectNode = objectMapper.createObjectNode()
        body.put("model", llmProperties.resolvedModelSummary())
        body.put("temperature", 0.2)
        body.put("max_tokens", 120)
        val messages: ArrayNode = objectMapper.createArrayNode()
        messages.add(
            objectMapper.createObjectNode()
                .put("role", "system")
                .put("content", SYSTEM_PROMPT)
        )
        messages.add(
            objectMapper.createObjectNode()
                .put("role", "user")
                .put("content", toSend)
        )
        body.set<JsonNode>("messages", messages)

        return try {
            val responseBody = webClient.post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + llmProperties.apiKey)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
            parseSummary(responseBody)
        } catch (e: WebClientResponseException) {
            log.warn("Reasoning summarizer API error: {} {}", e.statusCode, e.responseBodyAsString)
            null
        } catch (e: Exception) {
            log.warn("Reasoning summarizer failed: {}", e.message)
            null
        }
    }

    private fun parseSummary(json: String?): String? {
        if (json.isNullOrBlank()) return null
        return try {
            val root: JsonNode = objectMapper.readTree(json)
            val choices = root.path("choices")
            if (choices.isEmpty) return null
            val content = choices[0].path("message").path("content")
            if (content.isMissingNode || !content.isTextual) return null
            val s = content.asText().trim()
            if (s.isEmpty()) null else s
        } catch (e: Exception) {
            log.debug("Parse summarizer response: {}", e.message)
            null
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ReasoningSummarizerService::class.java)
        private const val SYSTEM_PROMPT =
            "You summarize the following internal reasoning in one short sentence, in French. Phrase the summary from the assistant's perspective (use \"Je\" / \"I\"): describe what the assistant is thinking or planning to do, not what the user wants. Output only the summary in French, no quotes or prefix."
        private const val MIN_TEXT_LENGTH = 150
        private const val MAX_TEXT_LENGTH = 12_000
    }
}
