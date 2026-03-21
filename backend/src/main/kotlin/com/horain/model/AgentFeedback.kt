package com.horain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * User feedback (thumb up/down) on a single agent turn.
 */
@Entity
@Table(name = "agent_feedback")
class AgentFeedback {
    @Id
    var id: UUID? = null

    @Column(name = "turn_id", nullable = false, unique = true)
    var turnId: UUID? = null

    @Column(name = "rating", nullable = false, length = 20)
    var rating: String? = null

    @Column(name = "reason_code", length = 100)
    var reasonCode: String? = null

    @Column(name = "comment", columnDefinition = "TEXT")
    var comment: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null

    @PrePersist
    protected fun onCreate() {
        if (createdAt == null) createdAt = Instant.now()
    }
}
