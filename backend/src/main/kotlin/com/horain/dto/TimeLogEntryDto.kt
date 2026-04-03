package com.horain.dto

/**
 * DTO for time log entries with project name, used by API responses
 * (e.g. GET /time-logs/recent) and aligned with frontend TimeLogEntry.
 */
data class TimeLogEntryDto(
    val id: String,
    val projectId: String,
    val projectName: String,
    val projectCardColorIndex: Int?,
    val durationMinutes: Int,
    val note: String?,
    val billable: Boolean,
    val loggedAt: String,
    val createdAt: String,
    val activityTypeCode: String?,
    val activityTypeLabel: String?,
    val dailyRateCents: Int?
)
