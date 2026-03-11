package com.horain.repository;

import com.horain.model.AgentFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for user feedback on agent turns.
 */
public interface AgentFeedbackRepository extends JpaRepository<AgentFeedback, UUID> {

    Optional<AgentFeedback> findByTurnId(UUID turnId);

    List<AgentFeedback> findByRating(String rating);
}
