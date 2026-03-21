package com.horain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * Long-term agent memory: one fact per (user_id, kind, memory_key).
 * Consolidation is upsert on that triple; optional TTL via expiresAt.
 */
@Entity
@Table(
    name = "memories",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "kind", "memory_key"])]
)
class Memory {
    @Id
    var id: UUID? = null

    @Column(name = "user_id", nullable = false, length = 255)
    var userId: String? = null

    @Column(name = "kind", nullable = false, length = 50)
    var kind: String? = null

    @Column(name = "memory_key", nullable = false, length = 255)
    var memoryKey: String? = null

    @Column(name = "memory_value", columnDefinition = "TEXT")
    var value: String? = null

    @Column(name = "fact_text", nullable = false, columnDefinition = "TEXT")
    var factText: String? = null

    @Column(name = "expires_at")
    var expiresAt: Instant? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null

    @PrePersist
    protected fun onCreate() {
        val now = Instant.now()
        if (createdAt == null) createdAt = now
        if (updatedAt == null) updatedAt = now
    }

    @PreUpdate
    protected fun onUpdate() {
        updatedAt = Instant.now()
    }
}
