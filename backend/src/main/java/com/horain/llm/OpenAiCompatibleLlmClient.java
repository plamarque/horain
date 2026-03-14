package com.horain.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM client for OpenAI-compatible APIs (OpenAI, OpenRouter, LiteLLM, etc.).
 * Configure via LLM_BASE_URL, LLM_API_KEY, LLM_MODEL.
 */
public class OpenAiCompatibleLlmClient implements StreamingLlmClient {

    private static final int STREAM_TIMEOUT_SECONDS = 120;
    /** Max retries when OpenAI returns 429 (rate limit). */
    private static final int MAX_RETRIES_429 = 5;
    /** Default delay in ms when 429 body does not specify retry-after. */
    private static final long DEFAULT_RETRY_DELAY_MS = 2000L;
    /** Cap retry delay to avoid excessive wait. */
    private static final long MAX_RETRY_DELAY_MS = 60_000L;
    /** OpenAI error message pattern: "Please try again in 744ms". */
    private static final Pattern RETRY_AFTER_MS = Pattern.compile("try again in (\\d+)ms", Pattern.CASE_INSENSITIVE);

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmClient(
            LlmProperties properties,
            RestTemplate restTemplate,
            WebClient webClient,
            ObjectMapper objectMapper) {
        this.baseUrl = properties.baseUrl() != null ? properties.baseUrl().trim() : "https://api.openai.com/v1";
        this.apiKey = properties.apiKey() != null ? properties.apiKey().trim() : "";
        this.model = properties.model() != null && !properties.model().isBlank() ? properties.model() : "gpt-4o-mini";
        this.restTemplate = restTemplate;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public LlmResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        String url = baseUrl.replaceAll("/$", "") + "/chat/completions";
        ObjectNode body = buildRequestBody(messages, tools);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        HttpClientErrorException last429 = null;
        for (int attempt = 0; attempt <= MAX_RETRIES_429; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                return parseResponse(response.getBody());
            } catch (HttpClientErrorException.TooManyRequests e) {
                last429 = e;
                if (attempt == MAX_RETRIES_429) {
                    break;
                }
                long delayMs = parseRetryAfterMs(e.getResponseBodyAsString());
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for rate limit", ie);
                }
            }
        }
        throw last429 != null ? last429 : new RuntimeException("Unexpected error in chat");
    }

    @Override
    public LlmResponse chatStream(
            List<ChatMessage> messages,
            List<ToolDefinition> tools,
            Consumer<String> textConsumer,
            Consumer<String> reasoningConsumer
    ) {
        // Chat Completions API does not expose reasoning; reasoningConsumer is ignored.
        String url = baseUrl.replaceAll("/$", "") + "/chat/completions";
        ObjectNode body = buildRequestBody(messages, tools);
        body.put("stream", true);

        StringBuilder contentAccumulator = new StringBuilder();
        List<ToolCallRequest> toolCallsAccumulator = new ArrayList<>();
        AtomicReference<LlmResponse> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder lineBuffer = new StringBuilder();

        Flux<DataBuffer> bodyFlux = webClient.post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToFlux(DataBuffer.class);

        bodyFlux.subscribe(
                dataBuffer -> {
                    String chunk = dataBuffer.toString(StandardCharsets.UTF_8);
                    lineBuffer.append(chunk);
                    int idx;
                    while ((idx = lineBuffer.indexOf("\n")) >= 0) {
                        String line = lineBuffer.substring(0, idx).trim();
                        lineBuffer.delete(0, idx + 1);
                        if (!line.startsWith("data: ")) continue;
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) continue;
                        try {
                            JsonNode root = objectMapper.readTree(data);
                            JsonNode choices = root.path("choices");
                            if (choices.isEmpty() || !choices.isArray()) continue;
                            JsonNode choice = choices.get(0);
                            JsonNode delta = choice.path("delta");
                            if (delta.has("content") && !delta.get("content").isNull()) {
                                String text = delta.get("content").asText("");
                                if (!text.isEmpty() && textConsumer != null) {
                                    textConsumer.accept(text);
                                }
                                contentAccumulator.append(text);
                            }
                            if (delta.has("tool_calls") && delta.get("tool_calls").isArray()) {
                                for (JsonNode tc : delta.get("tool_calls")) {
                                    int toolIndex = tc.path("index").asInt(0);
                                    while (toolCallsAccumulator.size() <= toolIndex) {
                                        toolCallsAccumulator.add(new ToolCallRequest("", "", ""));
                                    }
                                    ToolCallRequest existing = toolCallsAccumulator.get(toolIndex);
                                    String id = tc.has("id") && !tc.get("id").isNull()
                                            ? tc.get("id").asText() : existing.id();
                                    String name = existing.name();
                                    String args = existing.arguments();
                                    if (tc.has("function") && tc.get("function").isObject()) {
                                        JsonNode fn = tc.get("function");
                                        if (fn.has("name") && !fn.get("name").isNull()) {
                                            name = fn.get("name").asText();
                                        }
                                        if (fn.has("arguments") && !fn.get("arguments").isNull()) {
                                            args = args + fn.get("arguments").asText();
                                        }
                                    }
                                    toolCallsAccumulator.set(toolIndex, new ToolCallRequest(id, name, args));
                                }
                            }
                        } catch (Exception e) {
                            // Ignore malformed SSE lines
                        }
                    }
                },
                err -> {
                    resultRef.set(new LlmResponse("", null, "error"));
                    latch.countDown();
                },
                () -> {
                    List<ToolCallRequest> tc = toolCallsAccumulator.isEmpty() ? null : toolCallsAccumulator;
                    resultRef.set(new LlmResponse(contentAccumulator.toString(), tc, "stop"));
                    latch.countDown();
                }
        );

        try {
            if (!latch.await(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return new LlmResponse(contentAccumulator.toString(),
                        toolCallsAccumulator.isEmpty() ? null : toolCallsAccumulator, "stop");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new LlmResponse(contentAccumulator.toString(),
                    toolCallsAccumulator.isEmpty() ? null : toolCallsAccumulator, "stop");
        }
        LlmResponse r = resultRef.get();
        return r != null ? r : new LlmResponse(contentAccumulator.toString(),
                toolCallsAccumulator.isEmpty() ? null : toolCallsAccumulator, "stop");
    }

    private ObjectNode buildRequestBody(List<ChatMessage> messages, List<ToolDefinition> tools) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.2);
        ArrayNode messagesArray = objectMapper.createArrayNode();
        for (ChatMessage msg : messages) {
            ObjectNode m = objectMapper.createObjectNode();
            m.put("role", msg.role());
            if (msg.content() != null && !msg.content().isBlank()) {
                m.put("content", msg.content());
            } else if ("assistant".equals(msg.role()) && msg.toolCalls() != null) {
                m.put("content", "");
            }
            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                ArrayNode toolCallsArray = objectMapper.createArrayNode();
                for (ToolCallRequest tc : msg.toolCalls()) {
                    ObjectNode tcNode = objectMapper.createObjectNode();
                    tcNode.put("id", tc.id());
                    tcNode.put("type", "function");
                    ObjectNode fn = objectMapper.createObjectNode();
                    fn.put("name", tc.name());
                    fn.put("arguments", tc.arguments());
                    tcNode.set("function", fn);
                    toolCallsArray.add(tcNode);
                }
                m.set("tool_calls", toolCallsArray);
            }
            if (msg.toolCallId() != null) {
                m.put("tool_call_id", msg.toolCallId());
            }
            messagesArray.add(m);
        }
        body.set("messages", messagesArray);
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = objectMapper.createArrayNode();
            for (ToolDefinition t : tools) {
                ObjectNode toolNode = objectMapper.createObjectNode();
                toolNode.put("type", "function");
                ObjectNode fn = objectMapper.createObjectNode();
                fn.put("name", t.name());
                fn.put("description", t.description());
                if (t.parameters() != null && !t.parameters().isEmpty()) {
                    fn.set("parameters", objectMapper.valueToTree(t.parameters()));
                } else {
                    fn.putObject("parameters");
                }
                toolNode.set("function", fn);
                toolsArray.add(toolNode);
            }
            body.set("tools", toolsArray);
        }
        return body;
    }

    /**
     * Parse delay in ms from OpenAI 429 response body (e.g. "Please try again in 744ms").
     * Returns DEFAULT_RETRY_DELAY_MS if not found, capped by MAX_RETRY_DELAY_MS.
     */
    private static long parseRetryAfterMs(String body) {
        if (body == null || body.isBlank()) {
            return DEFAULT_RETRY_DELAY_MS;
        }
        Matcher m = RETRY_AFTER_MS.matcher(body);
        if (m.find()) {
            long ms = Long.parseLong(m.group(1));
            return Math.min(Math.max(ms, 100L), MAX_RETRY_DELAY_MS);
        }
        return DEFAULT_RETRY_DELAY_MS;
    }

    private LlmResponse parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return new LlmResponse("", null, "stop");
            }
            JsonNode choice = choices.get(0);
            String finishReason = choice.has("finish_reason")
                    ? choice.get("finish_reason").asText()
                    : "stop";
            JsonNode message = choice.get("message");
            if (message == null) {
                return new LlmResponse("", null, finishReason);
            }

            String content = message.has("content") && message.get("content") != null
                    ? message.get("content").asText("")
                    : "";

            List<ToolCallRequest> toolCalls = null;
            if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
                toolCalls = new ArrayList<>();
                for (JsonNode tc : message.get("tool_calls")) {
                    String id = tc.has("id") ? tc.get("id").asText() : "";
                    JsonNode fn = tc.has("function") ? tc.get("function") : null;
                    String name = fn != null && fn.has("name") ? fn.get("name").asText() : "";
                    String args = fn != null && fn.has("arguments") ? fn.get("arguments").asText() : "{}";
                    toolCalls.add(new ToolCallRequest(id, name, args));
                }
            }

            return new LlmResponse(content, toolCalls, finishReason);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM response", e);
        }
    }
}
