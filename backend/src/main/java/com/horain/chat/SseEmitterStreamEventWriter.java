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
    public void sendDone(String assistantMessage, List<ToolCallRecord> toolCalls, Object data, UUID turnId) {
        if (completed) return;
        try {
            List<Map<String, String>> toolCallsDto = toolCalls != null
                    ? toolCalls.stream()
                    .map(tc -> Map.<String, String>of(
                            "name", tc.name(),
                            "arguments", tc.arguments() != null ? tc.arguments() : "",
                            "result", tc.result() != null ? tc.result() : ""))
                    .collect(Collectors.toList())
                    : List.of();
            Map<String, Object> payload = new java.util.HashMap<>(Map.of(
                    "assistantMessage", assistantMessage != null ? assistantMessage : "",
                    "toolCalls", toolCallsDto,
                    "data", data != null ? data : Map.of()));
            if (turnId != null) {
                payload.put("turnId", turnId.toString());
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
    public void sendError(String message) {
        completeWithError(message);
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
