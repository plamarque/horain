package com.horain.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.horain.agent.AgentFeedbackService;
import com.horain.llm.LlmClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Chat API controller.
 * POST /chat/message - send a user message and receive assistant response.
 * GET /chat/status - returns whether LLM is configured (for debugging).
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final long SSE_EMITTER_TIMEOUT_MS = 300_000L;

    private final LlmChatService chatService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final AgentFeedbackService agentFeedbackService;

    public ChatController(LlmChatService chatService, LlmClient llmClient, ObjectMapper objectMapper,
                          AgentFeedbackService agentFeedbackService) {
        this.chatService = chatService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.agentFeedbackService = agentFeedbackService;
    }

    @PostMapping(value = "/message/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestBody ChatMessageRequest request) {
        String userMessage = request != null && request.message() != null ? request.message().trim() : "";
        if (userMessage.isBlank()) {
            SseEmitter emitter = new SseEmitter(SSE_EMITTER_TIMEOUT_MS);
            emitter.completeWithError(new IllegalArgumentException("Please provide a message."));
            return emitter;
        }
        List<ChatHistoryEntry> history = request != null && request.history() != null
                ? request.history()
                : List.of();
        List<Map<String, Object>> contextEntries = request != null && request.contextEntries() != null
                ? request.contextEntries()
                : List.of();
        List<Map<String, Object>> contextProjects = request != null && request.contextProjects() != null
                ? request.contextProjects()
                : List.of();
        SseEmitter emitter = new SseEmitter(SSE_EMITTER_TIMEOUT_MS);
        StreamEventWriter writer = new SseEmitterStreamEventWriter(emitter, objectMapper);
        CompletableFuture.runAsync(() -> {
            try {
                chatService.chatStream(userMessage, history, contextEntries, contextProjects, writer);
            } catch (Exception e) {
                writer.sendError(e.getMessage());
            }
        });
        return emitter;
    }

    @PostMapping("/message")
    public ResponseEntity<ChatMessageResponse> message(@RequestBody ChatMessageRequest request) {
        String userMessage = request != null && request.message() != null ? request.message().trim() : "";
        if (userMessage.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ChatMessageResponse("Please provide a message.", null, null, null));
        }

        List<ChatHistoryEntry> history = request != null && request.history() != null
                ? request.history()
                : List.of();
        List<Map<String, Object>> contextEntries = request != null && request.contextEntries() != null
                ? request.contextEntries()
                : List.of();
        List<Map<String, Object>> contextProjects = request != null && request.contextProjects() != null
                ? request.contextProjects()
                : List.of();
        ChatResponse response = chatService.chat(userMessage, history, contextEntries, contextProjects);
        return ResponseEntity.ok(new ChatMessageResponse(
                response.assistantMessage(),
                response.toolCalls().stream()
                        .map(tc -> new ToolCallDto(tc.name(), tc.arguments(), tc.result()))
                        .toList(),
                response.data(),
                response.turnId()));
    }

    public record ChatMessageRequest(
            String message,
            List<ChatHistoryEntry> history,
            List<Map<String, Object>> contextEntries,
            List<Map<String, Object>> contextProjects) {
    }

    public record ChatMessageResponse(String assistantMessage, java.util.List<ToolCallDto> toolCalls, Object data, UUID turnId) {
    }

    public record ToolCallDto(String name, String arguments, String result) {
    }

    @PostMapping("/feedback")
    public ResponseEntity<Map<String, Object>> feedback(@RequestBody FeedbackRequest request) {
        if (request == null || request.turnId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "turnId is required"));
        }
        try {
            UUID turnId = UUID.fromString(request.turnId());
            agentFeedbackService.saveFeedback(turnId, request.rating(), request.reasonCode(), request.comment());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record FeedbackRequest(String turnId, String rating, String reasonCode, String comment) {
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("llmConfigured", llmClient.isConfigured());
    }
}
