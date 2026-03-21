package com.horain.dto

import java.time.Instant
import java.util.UUID

/**
 * DTO for time log.
 */
class TimeLogDto {
    var id: UUID? = null
    var projectId: UUID? = null
    var durationMinutes: Int? = null
    var note: String? = null
    var billable: Boolean? = null
    var loggedAt: Instant? = null
    var createdAt: Instant? = null
    var updatedAt: Instant? = null
    var userId: String? = null
    var activityTypeCode: String? = null
    var activityTypeLabel: String? = null
    var dailyRateCents: Int? = null

    fun id(id: UUID?) = apply { this.id = id }
    fun projectId(projectId: UUID?) = apply { this.projectId = projectId }
    fun durationMinutes(durationMinutes: Int?) = apply { this.durationMinutes = durationMinutes }
    fun note(note: String?) = apply { this.note = note }
    fun billable(billable: Boolean?) = apply { this.billable = billable }
    fun loggedAt(loggedAt: Instant?) = apply { this.loggedAt = loggedAt }
    fun createdAt(createdAt: Instant?) = apply { this.createdAt = createdAt }
    fun updatedAt(updatedAt: Instant?) = apply { this.updatedAt = updatedAt }
    fun userId(userId: String?) = apply { this.userId = userId }
    fun activityTypeCode(activityTypeCode: String?) = apply { this.activityTypeCode = activityTypeCode }
    fun activityTypeLabel(activityTypeLabel: String?) = apply { this.activityTypeLabel = activityTypeLabel }
    fun dailyRateCents(dailyRateCents: Int?) = apply { this.dailyRateCents = dailyRateCents }

    fun build(): TimeLogDto = this

    companion object {
        @JvmStatic
        fun builder() = TimeLogDto()
    }
}
