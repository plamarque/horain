package com.horain.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Eval candidate derived from a turn (and optionally feedback).
 * Triage status and metadata for promotion to Promptfoo tests.
 */
@Entity
@Table(name = "eval_backlog")
public class EvalBacklog {

    @Id
    private UUID id;

    @Column(name = "turn_id", nullable = false)
    private UUID turnId;

    @Column(name = "eval_family", length = 100)
    private String evalFamily;

    @Column(name = "expected_behavior", columnDefinition = "TEXT")
    private String expectedBehavior;

    @Column(name = "assertion_type", length = 50)
    private String assertionType;

    @Column(name = "severity", length = 20)
    private String severity;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "promoted_at", nullable = false)
    private Instant promotedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTurnId() { return turnId; }
    public void setTurnId(UUID turnId) { this.turnId = turnId; }
    public String getEvalFamily() { return evalFamily; }
    public void setEvalFamily(String evalFamily) { this.evalFamily = evalFamily; }
    public String getExpectedBehavior() { return expectedBehavior; }
    public void setExpectedBehavior(String expectedBehavior) { this.expectedBehavior = expectedBehavior; }
    public String getAssertionType() { return assertionType; }
    public void setAssertionType(String assertionType) { this.assertionType = assertionType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getPromotedAt() { return promotedAt; }
    public void setPromotedAt(Instant promotedAt) { this.promotedAt = promotedAt; }

    @PrePersist
    protected void onCreate() {
        if (promotedAt == null) promotedAt = Instant.now();
    }
}
