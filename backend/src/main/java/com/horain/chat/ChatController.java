package com.horain.chat;

import com.horain.llm.LlmClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Chat API controller.
 * POST /chat/message - send a user message and receive assistant response.
 * GET /chat/status - returns whether LLM is configured (for debugging).
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final LlmChatService chatService;
    private final LlmClient llmClient;

    public ChatController(LlmChatService chatService, LlmClient llmClient) {
        this.chatService = chatService;
        this.llmClient = llmClient;
    }

    @PostMapping("/message")
    public ResponseEntity<ChatMessageResponse> message(@RequestBody ChatMessageRequest request) {
        String userMessage = request != null && request.message() != null ? request.message().trim() : "";
        if (userMessage.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ChatMessageResponse("Please provide a message.", null, null));
        }

        List<ChatHistoryEntry> history = request != null && request.history() != null
                ? request.history()
                : List.of();
        List<Map<String, Object>> contextEntries = request != null && request.contextEntries() != null
                ? request.contextEntries()
                : List.of();
        ChatResponse response = chatService.chat(userMessage, history, contextEntries);
        return ResponseEntity.ok(new ChatMessageResponse(
                response.assistantMessage(),
                response.toolCalls().stream()
                        .map(tc -> new ToolCallDto(tc.name(), tc.arguments(), tc.result()))
                        .toList(),
                response.data()));
    }

    public record ChatMessageRequest(
            String message,
            List<ChatHistoryEntry> history,
            List<Map<String, Object>> contextEntries) {
    }

    public record ChatMessageResponse(String assistantMessage, java.util.List<ToolCallDto> toolCalls, Object data) {
    }

    public record ToolCallDto(String name, String arguments, String result) {
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("llmConfigured", llmClient.isConfigured());
    }
}
