package com.horain.dto

import java.time.Instant
import java.util.UUID

/**
 * DTO for project.
 */
class ProjectDto {
    var id: UUID? = null
    var name: String? = null
    var description: String? = null
    var billable: Boolean? = null
    var createdAt: Instant? = null
    var updatedAt: Instant? = null
    var userId: String? = null
    /** Total revenue in cents from billable time logs with activity type (TJM). Optional, set when listing projects. */
    var revenueCents: Long? = null
    /** Number of time log entries (activities) for this project. Optional, set when listing projects. */
    var timeLogCount: Long? = null
    /** Top activity types by count for this project (e.g. for tags). Optional, set when listing projects. */
    var topActivityTypes: List<ProjectActivityTypeSummaryDto>? = null

    fun id(id: UUID?) = apply { this.id = id }
    fun name(name: String?) = apply { this.name = name }
    fun description(description: String?) = apply { this.description = description }
    fun billable(billable: Boolean?) = apply { this.billable = billable }
    fun createdAt(createdAt: Instant?) = apply { this.createdAt = createdAt }
    fun updatedAt(updatedAt: Instant?) = apply { this.updatedAt = updatedAt }
    fun userId(userId: String?) = apply { this.userId = userId }
    fun revenueCents(revenueCents: Long?) = apply { this.revenueCents = revenueCents }
    fun timeLogCount(timeLogCount: Long?) = apply { this.timeLogCount = timeLogCount }
    fun topActivityTypes(topActivityTypes: List<ProjectActivityTypeSummaryDto>?) = apply {
        this.topActivityTypes = topActivityTypes
    }

    fun build(): ProjectDto = this

    companion object {
        @JvmStatic
        fun builder() = ProjectDto()
    }
}
