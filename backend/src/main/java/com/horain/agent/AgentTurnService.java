package com.horain.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horain.chat.ChatHistoryEntry;
import com.horain.chat.ToolCallRecord;
import com.horain.model.AgentTurn;
import com.horain.repository.AgentTurnRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists agent conversation turns for trace and eval pipeline.
 */
@Service
public class AgentTurnService {

    private static final Logger log = LoggerFactory.getLogger(AgentTurnService.class);
    private static final int MAX_HISTORY_SNAPSHOT_MESSAGES = 10;

    private final AgentTurnRepository repository;
    private final ObjectMapper objectMapper;
    private final String systemPromptVersion;

    public AgentTurnService(AgentTurnRepository repository, ObjectMapper objectMapper,
                            @Value("${horain.system-prompt-version:v1}") String systemPromptVersion) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.systemPromptVersion = systemPromptVersion;
    }

    /**
     * Saves one conversation turn and returns the entity (with id) for client feedback.
     */
    @Transactional
    public AgentTurn saveTurn(
            UUID conversationId,
            int turnIndex,
            String userMessage,
            String assistantMessage,
            List<ToolCallRecord> toolCalls,
            Object uiPayload,
            String model,
            String status,
            List<ChatHistoryEntry> historySnapshot,
            List<Map<String, Object>> contextEntries,
            Long latencyMs) {
        AgentTurn turn = new AgentTurn();
        turn.setId(UUID.randomUUID());
        turn.setConversationId(conversationId);
        turn.setTurnIndex(turnIndex);
        turn.setUserMessage(userMessage != null ? userMessage : "");
        turn.setAssistantMessage(assistantMessage);
        turn.setToolCallsJson(serializeToolCalls(toolCalls));
        turn.setToolResultsJson(null);
        turn.setUiPayloadJson(serialize(uiPayload));
        turn.setSystemPromptVersion(systemPromptVersion);
        turn.setModel(model);
        turn.setStatus(status);
        turn.setHistorySnapshotJson(serializeHistory(historySnapshot));
        turn.setContextEntriesJson(serialize(contextEntries));
        turn.setLatencyMs(latencyMs);
        turn.setCreatedAt(java.time.Instant.now());
        AgentTurn saved = repository.save(turn);
        log.debug("Saved agent turn {} for conversation {}", saved.getId(), conversationId);
        return saved;
    }

    private String serializeToolCalls(List<ToolCallRecord> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) return null;
        try {
            List<Map<String, String>> list = toolCalls.stream()
                    .map(tc -> Map.<String, String>of(
                            "name", tc.name(),
                            "arguments", tc.arguments() != null ? tc.arguments() : "",
                            "result", tc.result() != null ? tc.result() : ""))
                    .toList();
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize tool calls: {}", e.getMessage());
            return null;
        }
    }

    private String serializeHistory(List<ChatHistoryEntry> history) {
        if (history == null || history.isEmpty()) return null;
        int from = Math.max(0, history.size() - MAX_HISTORY_SNAPSHOT_MESSAGES);
        List<ChatHistoryEntry> slice = history.subList(from, history.size());
        try {
            return objectMapper.writeValueAsString(slice);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize history snapshot: {}", e.getMessage());
            return null;
        }
    }

    private String serialize(Object o) {
        if (o == null) return null;
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize payload: {}", e.getMessage());
            return null;
        }
    }
}
