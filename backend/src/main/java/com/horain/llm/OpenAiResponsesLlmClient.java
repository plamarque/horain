package com.horain.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * LLM client for OpenAI Responses API (/v1/responses).
 * Supports reasoning models (o1, o3, o4-mini, etc.) with streaming reasoning summary
 * via response.reasoning_summary_text.delta events.
 */
public class OpenAiResponsesLlmClient implements StreamingLlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesLlmClient.class);
    private static final int STREAM_TIMEOUT_SECONDS = 120;

    private static final String DEFAULT_REASONING_EFFORT = "medium";

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String reasoningEffort;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAiResponsesLlmClient(
            LlmProperties properties,
            WebClient webClient,
            ObjectMapper objectMapper) {
        this(properties.baseUrl() != null ? properties.baseUrl().trim() : "https://api.openai.com/v1",
                properties.apiKey() != null ? properties.apiKey().trim() : "",
                properties.model() != null && !properties.model().isBlank() ? properties.model() : "gpt-4o-mini",
                null,
                webClient,
                objectMapper);
    }

    /**
     * Constructor for multi-model routing: specify model and reasoning effort per level.
     */
    public OpenAiResponsesLlmClient(
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            WebClient webClient,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.openai.com/v1";
        this.apiKey = apiKey != null ? apiKey : "";
        this.model = model != null && !model.isBlank() ? model : "gpt-4o-mini";
        this.reasoningEffort = reasoningEffort != null && !reasoningEffort.isBlank() ? reasoningEffort : DEFAULT_REASONING_EFFORT;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public LlmResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        String url = baseUrl.replaceAll("/$", "") + "/responses";
        ObjectNode body = buildRequestBody(messages, tools, false);
        String responseBody = webClient.post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parseNonStreamResponse(responseBody);
    }

    @Override
    public LlmResponse chatStream(
            List<ChatMessage> messages,
            List<ToolDefinition> tools,
            Consumer<String> textConsumer,
            Consumer<String> reasoningConsumer) {
        String url = baseUrl.replaceAll("/$", "") + "/responses";
        ObjectNode body = buildRequestBody(messages, tools, true);

        StringBuilder contentAccumulator = new StringBuilder();
        StringBuilder reasoningAccumulator = new StringBuilder();
        List<ToolCallRequest> toolCallsAccumulator = new ArrayList<>();
        Map<String, String> itemIdToName = new java.util.HashMap<>();
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
                        if (line.isEmpty()) continue;
                        // SSE format: "data: {...}" or "data: [DONE]"
                        if (line.startsWith("data:")) {
                            line = line.substring(5).trim();
                            if (line.isEmpty() || "[DONE]".equals(line)) continue;
                        }
                        try {
                            JsonNode event = objectMapper.readTree(line);
                            String type = event.has("type") ? event.get("type").asText("") : "";
                            switch (type) {
                                case "response.reasoning_summary_text.delta" -> {
                                    if (event.has("delta")) {
                                        String delta = event.get("delta").asText("");
                                        if (!delta.isEmpty()) {
                                            reasoningAccumulator.append(delta);
                                            if (reasoningConsumer != null) {
                                                reasoningConsumer.accept(delta);
                                            }
                                        }
                                    }
                                }
                                case "response.reasoning_summary_text.done" -> {
                                    if (event.has("text")) {
                                        String full = event.get("text").asText("");
                                        if (!full.isEmpty() && reasoningAccumulator.isEmpty()) {
                                            reasoningAccumulator.append(full);
                                        }
                                    }
                                }
                                case "response.output_text.delta" -> {
                                    if (event.has("delta")) {
                                        String delta = event.get("delta").asText("");
                                        if (!delta.isEmpty()) {
                                            contentAccumulator.append(delta);
                                            if (textConsumer != null) {
                                                textConsumer.accept(delta);
                                            }
                                        }
                                    }
                                }
                                case "response.output_text.done" -> {
                                    if (event.has("text")) {
                                        String full = event.get("text").asText("");
                                        if (!full.isEmpty() && contentAccumulator.isEmpty()) {
                                            contentAccumulator.append(full);
                                        }
                                    }
                                }
                                case "response.output_item.added" -> {
                                    JsonNode item = event.has("item") ? event.get("item") : null;
                                    if (item != null && "function_call".equals(item.has("type") ? item.get("type").asText("") : "")) {
                                        String itemId = item.has("id") ? item.get("id").asText("") : (item.has("call_id") ? item.get("call_id").asText("") : "");
                                        String itemName = item.has("name") ? item.get("name").asText("") : "";
                                        if (!itemId.isEmpty()) itemIdToName.put(itemId, itemName != null ? itemName : "");
                                    }
                                }
                                case "response.function_call_arguments.delta" -> {
                                    // Accumulate per item_id; we only need final args from .done
                                }
                                case "response.function_call_arguments.done" -> {
                                    String id = event.has("item_id") ? event.get("item_id").asText("") : "";
                                    String name = event.has("name") ? event.get("name").asText("") : "";
                                    if (name == null || name.isBlank()) name = itemIdToName.getOrDefault(id, "");
                                    String args = event.has("arguments") ? event.get("arguments").asText("{}") : "{}";
                                    toolCallsAccumulator.add(new ToolCallRequest(id, name != null ? name : "", args));
                                }
                                default -> { /* ignore other events */ }
                            }
                        } catch (Exception e) {
                            // Ignore malformed lines
                        }
                    }
                },
                err -> {
                    // #region agent log
                    try {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("model", model);
                        if (err instanceof WebClientResponseException e) {
                            String errorBody = e.getResponseBodyAsString();
                            data.put("status", e.getStatusCode().value());
                            data.put("bodyPreview", errorBody != null && errorBody.length() > 300 ? errorBody.substring(0, 300) + "..." : errorBody);
                            log.warn("Responses API stream failed: model={} status={} body={}",
                                    model, e.getStatusCode(), errorBody != null && errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody);
                        } else {
                            data.put("errorMessage", err.getMessage());
                            log.warn("Responses API stream failed: model={} error={}", model, err.getMessage(), err);
                        }
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("sessionId", "57e58b");
                        entry.put("location", "OpenAiResponsesLlmClient.java:err");
                        entry.put("message", "Responses API stream failed");
                        entry.put("data", data);
                        entry.put("hypothesisId", "H1");
                        entry.put("timestamp", System.currentTimeMillis());
                        String line = objectMapper.writeValueAsString(entry) + "\n";
                        Files.write(Path.of("/Users/patrice/GitHub/horain/.cursor/debug-57e58b.log"), line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (Exception ignored) { }
                    // #endregion
                    resultRef.set(new LlmResponse(contentAccumulator.toString(),
                            toolCallsAccumulator.isEmpty() ? null : toolCallsAccumulator,
                            "error",
                            reasoningAccumulator.isEmpty() ? null : reasoningAccumulator.toString()));
                    latch.countDown();
                },
                () -> {
                    // #region agent log
                    if (contentAccumulator.isEmpty()) {
                        try {
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("model", model);
                            data.put("contentEmpty", true);
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("sessionId", "57e58b");
                            entry.put("location", "OpenAiResponsesLlmClient.java:complete");
                            entry.put("message", "Stream completed with empty content");
                            entry.put("data", data);
                            entry.put("hypothesisId", "H2");
                            entry.put("timestamp", System.currentTimeMillis());
                            String line = objectMapper.writeValueAsString(entry) + "\n";
                            Files.write(Path.of("/Users/patrice/GitHub/horain/.cursor/debug-57e58b.log"), line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        } catch (Exception ignored) { }
                    }
                    // #endregion
                    resultRef.set(new LlmResponse(contentAccumulator.toString(),
                            toolCallsAccumulator.isEmpty() ? null : toolCallsAccumulator,
                            "stop",
                            reasoningAccumulator.isEmpty() ? null : reasoningAccumulator.toString()));
                    latch.countDown();
                });

        try {
            if (!latch.await(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return new LlmResponse(contentAccumulator.toString(),
                        toolCallsAccumulator.isEmpty() ? null : toolCallsAccumulator,
                        "stop",
                        reasoningAccumulator.isEmpty() ? null : reasoningAccumulator.toString());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new LlmResponse(contentAccumulator.toString(),
                    toolCallsAccumulator.isEmpty() ? null : toolCallsAccumulator,
                    "stop",
                    reasoningAccumulator.isEmpty() ? null : reasoningAccumulator.toString());
        }
        LlmResponse r = resultRef.get();
        return r != null ? r : new LlmResponse(contentAccumulator.toString(),
                toolCallsAccumulator.isEmpty() ? null : toolCallsAccumulator,
                "stop",
                reasoningAccumulator.isEmpty() ? null : reasoningAccumulator.toString());
    }

    /**
     * Build request body for Responses API.
     * Input format: array of items. Each message as { role, content } with content as array of { type, text } or string.
     */
    private ObjectNode buildRequestBody(List<ChatMessage> messages, List<ToolDefinition> tools, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", stream);
        // Do not send temperature: reasoning models (o4-mini, gpt-5.4, etc.) do not support it (400 invalid_request_error).

        ObjectNode reasoning = objectMapper.createObjectNode();
        reasoning.put("effort", reasoningEffort);
        reasoning.put("summary", "auto");
        body.set("reasoning", reasoning);

        ArrayNode inputArray = objectMapper.createArrayNode();
        for (ChatMessage msg : messages) {
            if (msg == null) continue;
            String role = msg.role();
            if ("system".equals(role)) {
                ObjectNode sysMsg = objectMapper.createObjectNode();
                sysMsg.put("role", "system");
                sysMsg.put("content", msg.content() != null ? msg.content() : "");
                inputArray.add(sysMsg);
            } else if ("user".equals(role)) {
                ObjectNode userMsg = objectMapper.createObjectNode();
                userMsg.put("role", "user");
                if (msg.content() != null && !msg.content().isBlank()) {
                    ArrayNode contentParts = objectMapper.createArrayNode();
                    ObjectNode textPart = objectMapper.createObjectNode();
                    textPart.put("type", "input_text");
                    textPart.put("text", msg.content());
                    contentParts.add(textPart);
                    userMsg.set("content", contentParts);
                } else {
                    userMsg.put("content", "");
                }
                inputArray.add(userMsg);
            } else if ("assistant".equals(role)) {
                // Responses API does not accept "tool_calls" on the assistant message object (400 unknown_parameter).
                // Send assistant content only, then each tool call as a separate input item (type "function_call")
                // so that subsequent function_call_output items have a matching call_id.
                ObjectNode asstMsg = objectMapper.createObjectNode();
                asstMsg.put("role", "assistant");
                if (msg.content() != null && !msg.content().isBlank()) {
                    ArrayNode contentParts = objectMapper.createArrayNode();
                    ObjectNode textPart = objectMapper.createObjectNode();
                    textPart.put("type", "output_text");
                    textPart.put("text", msg.content());
                    contentParts.add(textPart);
                    asstMsg.set("content", contentParts);
                } else {
                    asstMsg.putArray("content");
                }
                inputArray.add(asstMsg);
                if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                    for (ToolCallRequest tc : msg.toolCalls()) {
                        ObjectNode fcNode = objectMapper.createObjectNode();
                        fcNode.put("type", "function_call");
                        fcNode.put("call_id", tc.id());
                        fcNode.put("name", tc.name());
                        fcNode.put("arguments", tc.arguments() != null ? tc.arguments() : "{}");
                        inputArray.add(fcNode);
                    }
                }
            } else if ("tool".equals(role) && msg.toolCallId() != null) {
                ObjectNode toolOutput = objectMapper.createObjectNode();
                toolOutput.put("type", "function_call_output");
                toolOutput.put("call_id", msg.toolCallId());
                toolOutput.put("output", msg.content() != null ? msg.content() : "");
                inputArray.add(toolOutput);
            }
        }
        body.set("input", inputArray);

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = objectMapper.createArrayNode();
            for (ToolDefinition t : tools) {
                ObjectNode toolNode = objectMapper.createObjectNode();
                toolNode.put("type", "function");
                toolNode.put("name", t.name());
                toolNode.put("description", t.description() != null ? t.description() : "");
                if (t.parameters() != null && !t.parameters().isEmpty()) {
                    toolNode.set("parameters", objectMapper.valueToTree(t.parameters()));
                } else {
                    toolNode.putObject("parameters");
                }
                toolsArray.add(toolNode);
            }
            body.set("tools", toolsArray);
        }
        return body;
    }

    private LlmResponse parseNonStreamResponse(String json) {
        if (json == null || json.isBlank()) {
            return new LlmResponse("", null, "stop", null);
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode output = root.path("output");
            String content = "";
            String reasoningSummary = null;
            List<ToolCallRequest> toolCalls = new ArrayList<>();
            if (output.isArray()) {
                for (JsonNode item : output) {
                    String type = item.has("type") ? item.get("type").asText("") : "";
                    if ("message".equals(type) && "assistant".equals(item.path("role").asText(""))) {
                        JsonNode contentArr = item.path("content");
                        if (contentArr.isArray()) {
                            for (JsonNode part : contentArr) {
                                if (part.has("type") && "output_text".equals(part.get("type").asText(""))
                                        && part.has("text")) {
                                    content = content + part.get("text").asText("");
                                }
                            }
                        }
                    } else if ("reasoning".equals(type) && item.has("summary") && item.get("summary").isArray()) {
                        for (JsonNode sumPart : item.get("summary")) {
                            if (sumPart.has("type") && "summary_text".equals(sumPart.get("type").asText(""))
                                    && sumPart.has("text")) {
                                reasoningSummary = (reasoningSummary != null ? reasoningSummary : "")
                                        + sumPart.get("text").asText("");
                            }
                        }
                    } else if ("function_call".equals(type)) {
                        String id = item.has("id") ? item.get("id").asText("") : "";
                        String name = item.has("name") ? item.get("name").asText("") : "";
                        String args = item.has("arguments") ? item.get("arguments").asText("{}") : "{}";
                        toolCalls.add(new ToolCallRequest(id, name, args));
                    }
                }
            }
            String finishReason = root.has("status") ? root.get("status").asText("completed") : "completed";
            return new LlmResponse(content, toolCalls.isEmpty() ? null : toolCalls, finishReason, reasoningSummary);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Responses API response", e);
        }
    }
}
