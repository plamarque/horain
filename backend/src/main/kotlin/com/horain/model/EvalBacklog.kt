package com.horain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Eval candidate derived from a turn (and optionally feedback).
 * Triage status and metadata for promotion to Promptfoo tests.
 */
@Entity
@Table(name = "eval_backlog")
class EvalBacklog {
    @Id
    var id: UUID? = null

    @Column(name = "turn_id", nullable = false)
    var turnId: UUID? = null

    @Column(name = "eval_family", length = 100)
    var evalFamily: String? = null

    @Column(name = "expected_behavior", columnDefinition = "TEXT")
    var expectedBehavior: String? = null

    @Column(name = "assertion_type", length = 50)
    var assertionType: String? = null

    @Column(name = "severity", length = 20)
    var severity: String? = null

    @Column(name = "status", length = 50)
    var status: String? = null

    @Column(name = "notes", columnDefinition = "TEXT")
    var notes: String? = null

    @Column(name = "promoted_at", nullable = false)
    var promotedAt: Instant? = null

    @PrePersist
    protected fun onCreate() {
        if (promotedAt == null) promotedAt = Instant.now()
    }
}
