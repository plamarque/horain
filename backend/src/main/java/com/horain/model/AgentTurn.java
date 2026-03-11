package com.horain.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One turn of conversation: user message + assistant response and metadata.
 * Used for trace and later extraction into eval candidates.
 */
@Entity
@Table(name = "agent_turn")
public class AgentTurn {

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "turn_index", nullable = false)
    private Integer turnIndex;

    @Column(name = "user_message", nullable = false, columnDefinition = "TEXT")
    private String userMessage;

    @Column(name = "assistant_message", columnDefinition = "TEXT")
    private String assistantMessage;

    @Column(name = "tool_calls_json", columnDefinition = "TEXT")
    private String toolCallsJson;

    @Column(name = "tool_results_json", columnDefinition = "TEXT")
    private String toolResultsJson;

    @Column(name = "ui_payload_json", columnDefinition = "TEXT")
    private String uiPayloadJson;

    @Column(name = "system_prompt_version", length = 50)
    private String systemPromptVersion;

    @Column(name = "model")
    private String model;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "history_snapshot_json", columnDefinition = "TEXT")
    private String historySnapshotJson;

    @Column(name = "context_entries_json", columnDefinition = "TEXT")
    private String contextEntriesJson;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }
    public Integer getTurnIndex() { return turnIndex; }
    public void setTurnIndex(Integer turnIndex) { this.turnIndex = turnIndex; }
    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }
    public String getAssistantMessage() { return assistantMessage; }
    public void setAssistantMessage(String assistantMessage) { this.assistantMessage = assistantMessage; }
    public String getToolCallsJson() { return toolCallsJson; }
    public void setToolCallsJson(String toolCallsJson) { this.toolCallsJson = toolCallsJson; }
    public String getToolResultsJson() { return toolResultsJson; }
    public void setToolResultsJson(String toolResultsJson) { this.toolResultsJson = toolResultsJson; }
    public String getUiPayloadJson() { return uiPayloadJson; }
    public void setUiPayloadJson(String uiPayloadJson) { this.uiPayloadJson = uiPayloadJson; }
    public String getSystemPromptVersion() { return systemPromptVersion; }
    public void setSystemPromptVersion(String systemPromptVersion) { this.systemPromptVersion = systemPromptVersion; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHistorySnapshotJson() { return historySnapshotJson; }
    public void setHistorySnapshotJson(String historySnapshotJson) { this.historySnapshotJson = historySnapshotJson; }
    public String getContextEntriesJson() { return contextEntriesJson; }
    public void setContextEntriesJson(String contextEntriesJson) { this.contextEntriesJson = contextEntriesJson; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
