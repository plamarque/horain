package com.horain.repository;

import com.horain.model.EvalBacklog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for eval backlog (candidates for Promptfoo promotion).
 */
public interface EvalBacklogRepository extends JpaRepository<EvalBacklog, UUID> {

    List<EvalBacklog> findByStatus(String status);

    List<EvalBacklog> findByTurnId(UUID turnId);
}
