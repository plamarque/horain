package com.horain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.Id
import java.time.Instant
import java.util.UUID

/**
 * Time log entity.
 * A recorded entry for time spent on a project.
 */
@Entity
@Table(name = "time_logs")
class TimeLog {
    @Id
    var id: UUID? = null

    @Column(name = "project_id", nullable = false)
    var projectId: UUID? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", insertable = false, updatable = false)
    var project: Project? = null

    @Column(name = "duration_minutes", nullable = false)
    var durationMinutes: Int? = null

    @Column(length = 2000)
    var note: String? = null

    @Column(nullable = false)
    var billable: Boolean? = true

    @Column(name = "logged_at", nullable = false)
    var loggedAt: Instant? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null

    @Column(name = "user_id")
    var userId: String? = null

    @Column(name = "activity_type_code")
    var activityTypeCode: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_type_code", insertable = false, updatable = false)
    var activityType: ActivityType? = null

    @PrePersist
    protected fun onCreate() {
        val now = Instant.now()
        if (loggedAt == null) loggedAt = now
        if (createdAt == null) createdAt = now
        if (updatedAt == null) updatedAt = now
    }

    @PreUpdate
    protected fun onUpdate() {
        updatedAt = Instant.now()
    }
}
