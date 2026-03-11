package com.horain.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * User feedback (thumb up/down) on a single agent turn.
 */
@Entity
@Table(name = "agent_feedback")
public class AgentFeedback {

    @Id
    private UUID id;

    @Column(name = "turn_id", nullable = false, unique = true)
    private UUID turnId;

    @Column(name = "rating", nullable = false, length = 20)
    private String rating;

    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTurnId() { return turnId; }
    public void setTurnId(UUID turnId) { this.turnId = turnId; }
    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
