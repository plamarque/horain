package com.horain.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for project.
 */
public class ProjectDto {

    private UUID id;
    private String name;
    private String description;
    private Boolean billable;
    private Instant createdAt;
    private Instant updatedAt;
    private String userId;
    /** Total revenue in cents from billable time logs with activity type (TJM). Optional, set when listing projects. */
    private Long revenueCents;

    public static ProjectDto builder() { return new ProjectDto(); }
    public ProjectDto id(UUID id) { this.id = id; return this; }
    public ProjectDto name(String name) { this.name = name; return this; }
    public ProjectDto description(String description) { this.description = description; return this; }
    public ProjectDto billable(Boolean billable) { this.billable = billable; return this; }
    public ProjectDto createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
    public ProjectDto updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
    public ProjectDto userId(String userId) { this.userId = userId; return this; }
    public ProjectDto revenueCents(Long revenueCents) { this.revenueCents = revenueCents; return this; }
    public ProjectDto build() { return this; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getBillable() { return billable; }
    public void setBillable(Boolean billable) { this.billable = billable; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Long getRevenueCents() { return revenueCents; }
    public void setRevenueCents(Long revenueCents) { this.revenueCents = revenueCents; }
}
