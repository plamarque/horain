package com.horain.dto;

/**
 * DTO for time log entries with project name, used by API responses
 * (e.g. GET /time-logs/recent) and aligned with frontend TimeLogEntry.
 */
public record TimeLogEntryDto(
    String id,
    String projectId,
    String projectName,
    int durationMinutes,
    String note,
    boolean billable,
    String loggedAt,
    String createdAt
) {}
