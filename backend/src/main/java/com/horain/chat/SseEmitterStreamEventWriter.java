package com.horain.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * StreamEventWriter implementation that writes SSE events to an SseEmitter.
 * Event format: "chunk" (data = JSON {"text": "delta"}), "done" (data = JSON payload), "error" (data = JSON {"message": "..."}).
 */
public class SseEmitterStreamEventWriter implements StreamEventWriter {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterStreamEventWriter.class);
    private static final long EMITTER_TIMEOUT_MS = 300_000L;

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private boolean completed;

    public SseEmitterStreamEventWriter(SseEmitter emitter, ObjectMapper objectMapper) {
        this.emitter = emitter;
        this.emitter.onTimeout(() -> {
            if (!completed) {
                completed = true;
                log.debug("SSE emitter timed out");
            }
        });
        this.emitter.onCompletion(() -> completed = true);
        this.objectMapper = objectMapper;
        this.completed = false;
    }

    @Override
    public void sendChunk(String text) {
        if (completed) return;
        try {
            String data = objectMapper.writeValueAsString(Map.of("text", text != null ? text : ""));
            emitter.send(SseEmitter.event().name("chunk").data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("Failed to send chunk: {}", e.getMessage());
            completeWithError(e.getMessage());
        }
    }

    @Override
    public void sendDone(String assistantMessage, List<ToolCallRecord> toolCalls, List<Integer> toolCallIterations,
                         Object data, UUID turnId, String reasoningText, Long reasoningDurationMs) {
        if (completed) return;
        try {
            List<Map<String, Object>> toolCallsDto = toolCalls != null
                    ? IntStream.range(0, toolCalls.size())
                    .mapToObj(i -> {
                        ToolCallRecord tc = toolCalls.get(i);
                        Map<String, Object> m = new java.util.HashMap<>(Map.of(
                                "name", tc.name() != null ? tc.name() : "",
                                "arguments", tc.arguments() != null ? tc.arguments() : "",
                                "result", tc.result() != null ? tc.result() : ""));
                        if (toolCallIterations != null && i < toolCallIterations.size()) {
                            m.put("iterationIndex", toolCallIterations.get(i));
                        }
                        return m;
                    })
                    .collect(Collectors.toList())
                    : List.of();
            Map<String, Object> payload = new java.util.HashMap<>(Map.of(
                    "assistantMessage", assistantMessage != null ? assistantMessage : "",
                    "toolCalls", toolCallsDto,
                    "data", data != null ? data : Map.of()));
            if (turnId != null) {
                payload.put("turnId", turnId.toString());
            }
            if (reasoningText != null && !reasoningText.isBlank()) {
                payload.put("reasoningText", reasoningText);
            }
            if (reasoningDurationMs != null && reasoningDurationMs >= 0) {
                payload.put("reasoningDurationMs", reasoningDurationMs);
            }
            String json = objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name("done").data(json, MediaType.APPLICATION_JSON));
            emitter.complete();
            completed = true;
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize done payload: {}", e.getMessage());
            completeWithError(e.getMessage());
        } catch (IOException e) {
            log.warn("Failed to send done: {}", e.getMessage());
            completeWithError(e.getMessage());
        }
    }

    @Override
    public void sendReasoningChunk(String text) {
        if (completed) return;
        try {
            String data = objectMapper.writeValueAsString(Map.of("text", text != null ? text : ""));
            emitter.send(SseEmitter.event().name("reasoning_chunk").data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("Failed to send reasoning_chunk: {}", e.getMessage());
            completeWithError(e.getMessage());
        }
    }

    @Override
    public void sendError(String message) {
        completeWithError(message);
    }

    @Override
    public void sendToolCall(ToolCallRecord record, int iterationIndex) {
        if (completed || record == null) return;
        try {
            Map<String, Object> payload = new java.util.HashMap<>(Map.of(
                    "name", record.name() != null ? record.name() : "",
                    "arguments", record.arguments() != null ? record.arguments() : "",
                    "result", record.result() != null ? record.result() : "",
                    "iterationIndex", iterationIndex));
            String json = objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name("tool_call").data(json, MediaType.APPLICATION_JSON));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize tool_call payload: {}", e.getMessage());
        } catch (IOException e) {
            log.warn("Failed to send tool_call: {}", e.getMessage());
        }
    }

    @Override
    public void sendAssistantSegment(String text, int iterationIndex) {
        if (completed) return;
        try {
            Map<String, Object> payload = new java.util.HashMap<>(Map.of(
                    "text", text != null ? text : "",
                    "iterationIndex", iterationIndex));
            String json = objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name("assistant_segment").data(json, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("Failed to send assistant_segment: {}", e.getMessage());
            completeWithError(e.getMessage());
        }
    }

    private void completeWithError(String message) {
        if (completed) return;
        try {
            String data = objectMapper.writeValueAsString(Map.of("message", message != null ? message : "Unknown error"));
            emitter.send(SseEmitter.event().name("error").data(data, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.warn("Failed to send error event: {}", e.getMessage());
        }
        try {
            emitter.completeWithError(new RuntimeException(message));
        } catch (Exception e) {
            log.debug("Emitter already completed: {}", e.getMessage());
        }
        completed = true;
    }
}
