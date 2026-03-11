package com.horain.repository;

import com.horain.model.AgentTurn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA repository for agent conversation turns.
 */
public interface AgentTurnRepository extends JpaRepository<AgentTurn, UUID> {

    List<AgentTurn> findByConversationIdOrderByTurnIndexAsc(UUID conversationId);

    List<AgentTurn> findByCreatedAtAfterOrderByCreatedAtDesc(Instant after, org.springframework.data.domain.Pageable pageable);

    List<AgentTurn> findByStatusIn(List<String> statuses);
}
