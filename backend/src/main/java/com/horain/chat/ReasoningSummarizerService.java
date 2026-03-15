package com.horain.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horain.llm.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * One-shot summarization of reasoning text (Cursor-style: one short sentence below "Thought for Xs").
 * Uses a lightweight LLM call to the same API with a small prompt; does not block the main stream.
 */
@Service
public class ReasoningSummarizerService {

    private static final Logger log = LoggerFactory.getLogger(ReasoningSummarizerService.class);
    private static final String SYSTEM_PROMPT =
            "You summarize the following internal reasoning in one short sentence, in French. Phrase the summary from the assistant's perspective (use \"Je\" / \"I\"): describe what the assistant is thinking or planning to do, not what the user wants. Output only the summary in French, no quotes or prefix.";
    private static final int MIN_TEXT_LENGTH = 150;
    private static final int MAX_TEXT_LENGTH = 12_000;

    private final LlmProperties llmProperties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ReasoningSummarizerService(LlmProperties llmProperties, WebClient webClient, ObjectMapper objectMapper) {
        this.llmProperties = llmProperties;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns a one-sentence summary of the reasoning text, or null if summarization is not configured or fails.
     */
    public String summarize(String reasoningText) {
        if (reasoningText == null || reasoningText.isBlank()) {
            return null;
        }
        if (llmProperties.apiKey() == null || llmProperties.apiKey().isBlank()) {
            return null;
        }
        String trimmed = reasoningText.trim();
        if (trimmed.length() < MIN_TEXT_LENGTH) {
            return null;
        }
        String toSend = trimmed.length() > MAX_TEXT_LENGTH ? trimmed.substring(0, MAX_TEXT_LENGTH) + "…" : trimmed;

        String url = llmProperties.baseUrl().replaceAll("/$", "") + "/chat/completions";
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", llmProperties.modelSummary());
        body.put("temperature", 0.2);
        body.put("max_tokens", 120);
        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(objectMapper.createObjectNode()
                .put("role", "system")
                .put("content", SYSTEM_PROMPT));
        messages.add(objectMapper.createObjectNode()
                .put("role", "user")
                .put("content", toSend));
        body.set("messages", messages);

        try {
            String responseBody = webClient.post()
                    .uri(url)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + llmProperties.apiKey())
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return parseSummary(responseBody);
        } catch (WebClientResponseException e) {
            log.warn("Reasoning summarizer API error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("Reasoning summarizer failed: {}", e.getMessage());
            return null;
        }
    }

    private String parseSummary(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.path("choices");
            if (choices.isEmpty()) return null;
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isMissingNode() || !content.isTextual()) return null;
            String s = content.asText().trim();
            return s.isEmpty() ? null : s;
        } catch (Exception e) {
            log.debug("Parse summarizer response: {}", e.getMessage());
            return null;
        }
    }
}
