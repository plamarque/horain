package com.horain.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Long-term agent memory: one fact per (user_id, kind, memory_key).
 * Consolidation is upsert on that triple; optional TTL via expiresAt.
 */
@Entity
@Table(name = "memories", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "kind", "memory_key"})
})
public class Memory {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "kind", nullable = false, length = 50)
    private String kind;

    @Column(name = "memory_key", nullable = false, length = 255)
    private String memoryKey;

    @Column(name = "memory_value", columnDefinition = "TEXT")
    private String value;

    @Column(name = "fact_text", nullable = false, columnDefinition = "TEXT")
    private String factText;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getMemoryKey() { return memoryKey; }
    public void setMemoryKey(String memoryKey) { this.memoryKey = memoryKey; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getFactText() { return factText; }
    public void setFactText(String factText) { this.factText = factText; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
