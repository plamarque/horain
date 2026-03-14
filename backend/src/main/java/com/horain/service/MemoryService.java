package com.horain.service;

import com.horain.model.Memory;
import com.horain.repository.MemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for agent long-term memory: save (upsert), find active, forget.
 * MVP uses a default user id when no multi-account is present.
 */
@Service
public class MemoryService {

    /**
     * Default user id when the app does not yet support multi-account.
     * Overridable via horain.memory.default-user-id.
     */
    public static final String DEFAULT_USER_ID = "default";

    private final MemoryRepository memoryRepository;
    private final String defaultUserId;

    public MemoryService(
            MemoryRepository memoryRepository,
            @Value("${horain.memory.default-user-id:" + DEFAULT_USER_ID + "}") String defaultUserId) {
        this.memoryRepository = memoryRepository;
        this.defaultUserId = defaultUserId != null && !defaultUserId.isBlank() ? defaultUserId : DEFAULT_USER_ID;
    }

    /**
     * Returns the user id to use for memory operations when no request-scoped user is provided.
     */
    public String getDefaultUserId() {
        return defaultUserId;
    }

    /**
     * Save or update a memory. Consolidation: same (userId, kind, memoryKey) updates existing row.
     *
     * @param ttlSeconds Optional TTL; if null, no expiration. If present, expires_at = now + ttlSeconds.
     * @return The saved or updated memory.
     */
    @Transactional
    public Memory save(String userId, String kind, String memoryKey, String value, String factText, Long ttlSeconds) {
        if (userId == null || userId.isBlank()) {
            userId = defaultUserId;
        }
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("kind is required");
        }
        if (memoryKey == null || memoryKey.isBlank()) {
            throw new IllegalArgumentException("memoryKey is required");
        }
        if (factText == null || factText.isBlank()) {
            throw new IllegalArgumentException("factText is required");
        }
        Instant now = Instant.now();
        Instant expiresAt = ttlSeconds != null && ttlSeconds > 0
                ? now.plusSeconds(ttlSeconds)
                : null;

        Memory memory = memoryRepository.findByUserIdAndKindAndMemoryKey(userId, kind, memoryKey)
                .orElse(null);
        if (memory != null) {
            memory.setValue(value);
            memory.setFactText(factText.trim());
            memory.setExpiresAt(expiresAt);
            memory.setUpdatedAt(now);
            return memoryRepository.save(memory);
        }
        memory = new Memory();
        memory.setId(UUID.randomUUID());
        memory.setUserId(userId);
        memory.setKind(kind.trim());
        memory.setMemoryKey(memoryKey.trim());
        memory.setValue(value != null ? value : "");
        memory.setFactText(factText.trim());
        memory.setExpiresAt(expiresAt);
        memory.setCreatedAt(now);
        memory.setUpdatedAt(now);
        return memoryRepository.save(memory);
    }

    /**
     * All active (non-expired) memories for the user, most recently updated first.
     */
    @Transactional(readOnly = true)
    public List<Memory> findActiveByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            userId = defaultUserId;
        }
        return memoryRepository.findActiveByUserIdOrderByUpdatedAtDesc(userId, Instant.now());
    }

    /**
     * Active memories for the user filtered by kind.
     */
    @Transactional(readOnly = true)
    public List<Memory> findActiveByUserIdAndKind(String userId, String kind) {
        if (userId == null || userId.isBlank()) {
            userId = defaultUserId;
        }
        if (kind == null || kind.isBlank()) {
            return findActiveByUserId(userId);
        }
        return memoryRepository.findActiveByUserIdAndKindOrderByUpdatedAtDesc(userId, kind, Instant.now());
    }

    /**
     * Forget: if memoryKey is present, delete that one; if null/blank, delete all memories for the user and kind.
     */
    @Transactional
    public void forget(String userId, String kind, String memoryKey) {
        if (userId == null || userId.isBlank()) {
            userId = defaultUserId;
        }
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("kind is required");
        }
        if (memoryKey != null && !memoryKey.isBlank()) {
            memoryRepository.deleteByUserIdAndKindAndMemoryKey(userId, kind, memoryKey.trim());
        } else {
            memoryRepository.deleteByUserIdAndKind(userId, kind);
        }
    }
}
