package com.horain.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for time log.
 */
public class TimeLogDto {

    private UUID id;
    private UUID projectId;
    private Integer durationMinutes;
    private String note;
    private Instant loggedAt;
    private Instant updatedAt;
    private String userId;

    public static TimeLogDto builder() { return new TimeLogDto(); }
    public TimeLogDto id(UUID id) { this.id = id; return this; }
    public TimeLogDto projectId(UUID projectId) { this.projectId = projectId; return this; }
    public TimeLogDto durationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; return this; }
    public TimeLogDto note(String note) { this.note = note; return this; }
    public TimeLogDto loggedAt(Instant loggedAt) { this.loggedAt = loggedAt; return this; }
    public TimeLogDto updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
    public TimeLogDto userId(String userId) { this.userId = userId; return this; }
    public TimeLogDto build() { return this; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getLoggedAt() { return loggedAt; }
    public void setLoggedAt(Instant loggedAt) { this.loggedAt = loggedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
