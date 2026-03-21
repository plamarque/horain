package com.horain.repository

import com.horain.model.Memory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * JPA repository for agent memories.
 */
interface MemoryRepository : JpaRepository<Memory, UUID> {

    /**
     * Active memories for a user: not expired, ordered by updated_at desc.
     */
    @Query(
        "SELECT m FROM Memory m WHERE m.userId = :userId AND (m.expiresAt IS NULL OR m.expiresAt > :now) ORDER BY m.updatedAt DESC"
    )
    fun findActiveByUserIdOrderByUpdatedAtDesc(
        @Param("userId") userId: String,
        @Param("now") now: Instant
    ): List<Memory>

    /**
     * Active memories for a user and kind.
     */
    @Query(
        "SELECT m FROM Memory m WHERE m.userId = :userId AND m.kind = :kind AND (m.expiresAt IS NULL OR m.expiresAt > :now) ORDER BY m.updatedAt DESC"
    )
    fun findActiveByUserIdAndKindOrderByUpdatedAtDesc(
        @Param("userId") userId: String,
        @Param("kind") kind: String,
        @Param("now") now: Instant
    ): List<Memory>

    fun findByUserIdAndKindAndMemoryKey(userId: String, kind: String, memoryKey: String): Optional<Memory>

    fun deleteByUserIdAndKindAndMemoryKey(userId: String, kind: String, memoryKey: String)

    fun deleteByUserIdAndKind(userId: String, kind: String)
}
