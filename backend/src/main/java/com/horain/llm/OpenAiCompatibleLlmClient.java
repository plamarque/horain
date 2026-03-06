package com.horain.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM client for OpenAI-compatible APIs (OpenAI, OpenRouter, LiteLLM, etc.).
 * Configure via LLM_BASE_URL, LLM_API_KEY, LLM_MODEL.
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmClient(
            LlmProperties properties,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.baseUrl = properties.baseUrl() != null ? properties.baseUrl().trim() : "https://api.openai.com/v1";
        this.apiKey = properties.apiKey() != null ? properties.apiKey().trim() : "";
        this.model = properties.model() != null && !properties.model().isBlank() ? properties.model() : "gpt-4o-mini";
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public LlmResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        String url = baseUrl.replaceAll("/$", "") + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

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

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        return parseResponse(response.getBody());
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
