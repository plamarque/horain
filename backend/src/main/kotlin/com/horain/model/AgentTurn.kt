package com.horain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One turn of conversation: user message + assistant response and metadata.
 * Used for trace and later extraction into eval candidates.
 */
@Entity
@Table(name = "agent_turn")
class AgentTurn {
    @Id
    var id: UUID? = null

    @Column(name = "conversation_id", nullable = false)
    var conversationId: UUID? = null

    @Column(name = "turn_index", nullable = false)
    var turnIndex: Int? = null

    @Column(name = "user_message", nullable = false, columnDefinition = "TEXT")
    var userMessage: String? = null

    @Column(name = "assistant_message", columnDefinition = "TEXT")
    var assistantMessage: String? = null

    @Column(name = "tool_calls_json", columnDefinition = "TEXT")
    var toolCallsJson: String? = null

    @Column(name = "tool_results_json", columnDefinition = "TEXT")
    var toolResultsJson: String? = null

    @Column(name = "ui_payload_json", columnDefinition = "TEXT")
    var uiPayloadJson: String? = null

    @Column(name = "system_prompt_version", length = 50)
    var systemPromptVersion: String? = null

    @Column(name = "model")
    var model: String? = null

    @Column(name = "status", length = 50)
    var status: String? = null

    @Column(name = "history_snapshot_json", columnDefinition = "TEXT")
    var historySnapshotJson: String? = null

    @Column(name = "context_entries_json", columnDefinition = "TEXT")
    var contextEntriesJson: String? = null

    @Column(name = "latency_ms")
    var latencyMs: Long? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null

    @PrePersist
    protected fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}
