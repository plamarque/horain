package com.horain.service

import com.horain.model.Memory
import com.horain.repository.MemoryRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Service for agent long-term memory: save (upsert), find active, forget.
 * MVP uses a default user id when no multi-account is present.
 */
@Service
class MemoryService(
    private val memoryRepository: MemoryRepository,
    @Value("\${horain.memory.default-user-id:default}") defaultUserIdRaw: String
) {

    private val defaultUserId: String =
        if (defaultUserIdRaw.isNotBlank()) defaultUserIdRaw else DEFAULT_USER_ID

    /**
     * Returns the user id to use for memory operations when no request-scoped user is provided.
     */
    fun getDefaultUserId(): String = defaultUserId

    /**
     * Save or update a memory. Consolidation: same (userId, kind, memoryKey) updates existing row.
     *
     * @param ttlSeconds Optional TTL; if null, no expiration. If present, expires_at = now + ttlSeconds.
     * @return The saved or updated memory.
     */
    @Transactional
    fun save(
        userId: String?,
        kind: String,
        memoryKey: String,
        value: String?,
        factText: String,
        ttlSeconds: Long?
    ): Memory {
        var uid = userId
        if (uid.isNullOrBlank()) {
            uid = defaultUserId
        }
        if (kind.isBlank()) {
            throw IllegalArgumentException("kind is required")
        }
        if (memoryKey.isBlank()) {
            throw IllegalArgumentException("memoryKey is required")
        }
        if (factText.isBlank()) {
            throw IllegalArgumentException("factText is required")
        }
        val now = Instant.now()
        val expiresAt = if (ttlSeconds != null && ttlSeconds > 0) {
            now.plusSeconds(ttlSeconds)
        } else {
            null
        }
        var memory = memoryRepository.findByUserIdAndKindAndMemoryKey(uid, kind, memoryKey).orElse(null)
        if (memory != null) {
            memory.value = value
            memory.factText = factText.trim()
            memory.expiresAt = expiresAt
            memory.updatedAt = now
            return memoryRepository.save(memory)
        }
        memory = Memory()
        memory.id = UUID.randomUUID()
        memory.userId = uid
        memory.kind = kind.trim()
        memory.memoryKey = memoryKey.trim()
        memory.value = value ?: ""
        memory.factText = factText.trim()
        memory.expiresAt = expiresAt
        memory.createdAt = now
        memory.updatedAt = now
        return memoryRepository.save(memory)
    }

    /**
     * All active (non-expired) memories for the user, most recently updated first.
     */
    @Transactional(readOnly = true)
    fun findActiveByUserId(userId: String?): List<Memory> {
        var uid = userId
        if (uid.isNullOrBlank()) {
            uid = defaultUserId
        }
        return memoryRepository.findActiveByUserIdOrderByUpdatedAtDesc(uid, Instant.now())
    }

    /**
     * Active memories for the user filtered by kind.
     */
    @Transactional(readOnly = true)
    fun findActiveByUserIdAndKind(userId: String?, kind: String?): List<Memory> {
        var uid = userId
        if (uid.isNullOrBlank()) {
            uid = defaultUserId
        }
        if (kind.isNullOrBlank()) {
            return findActiveByUserId(uid)
        }
        return memoryRepository.findActiveByUserIdAndKindOrderByUpdatedAtDesc(uid, kind, Instant.now())
    }

    /**
     * Forget: if memoryKey is present, delete that one; if null/blank, delete all memories for the user and kind.
     */
    @Transactional
    fun forget(userId: String?, kind: String?, memoryKey: String?) {
        var uid = userId
        if (uid.isNullOrBlank()) {
            uid = defaultUserId
        }
        if (kind.isNullOrBlank()) {
            throw IllegalArgumentException("kind is required")
        }
        if (!memoryKey.isNullOrBlank()) {
            memoryRepository.deleteByUserIdAndKindAndMemoryKey(uid, kind, memoryKey.trim())
        } else {
            memoryRepository.deleteByUserIdAndKind(uid, kind)
        }
    }

    companion object {
        /**
         * Default user id when the app does not yet support multi-account.
         * Overridable via horain.memory.default-user-id.
         */
        const val DEFAULT_USER_ID = "default"
    }
}
