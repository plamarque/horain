package com.horain.repository

import com.horain.model.EvalBacklog
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * JPA repository for eval backlog (candidates for Promptfoo promotion).
 */
interface EvalBacklogRepository : JpaRepository<EvalBacklog, UUID> {

    fun findByStatus(status: String): List<EvalBacklog>

    fun findByTurnId(turnId: UUID): List<EvalBacklog>
}
