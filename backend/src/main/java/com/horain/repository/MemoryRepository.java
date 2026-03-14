package com.horain.repository;

import com.horain.model.Memory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for agent memories.
 */
public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    /**
     * Active memories for a user: not expired, ordered by updated_at desc.
     */
    @Query("SELECT m FROM Memory m WHERE m.userId = :userId AND (m.expiresAt IS NULL OR m.expiresAt > :now) ORDER BY m.updatedAt DESC")
    List<Memory> findActiveByUserIdOrderByUpdatedAtDesc(@Param("userId") String userId, @Param("now") Instant now);

    /**
     * Active memories for a user and kind.
     */
    @Query("SELECT m FROM Memory m WHERE m.userId = :userId AND m.kind = :kind AND (m.expiresAt IS NULL OR m.expiresAt > :now) ORDER BY m.updatedAt DESC")
    List<Memory> findActiveByUserIdAndKindOrderByUpdatedAtDesc(@Param("userId") String userId, @Param("kind") String kind, @Param("now") Instant now);

    Optional<Memory> findByUserIdAndKindAndMemoryKey(String userId, String kind, String memoryKey);

    void deleteByUserIdAndKindAndMemoryKey(String userId, String kind, String memoryKey);

    void deleteByUserIdAndKind(String userId, String kind);
}
