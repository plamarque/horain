package com.horain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Project entity.
 * An activity or initiative on which the user logs time.
 */
@Entity
@Table(name = "projects")
class Project {
    @Id
    var id: UUID? = null

    @Column(nullable = false, unique = true)
    var name: String? = null

    @Column(length = 2000)
    var description: String? = null

    @Column(nullable = false)
    var billable: Boolean? = true

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null

    @Column(name = "user_id")
    var userId: String? = null

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
