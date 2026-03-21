package com.horain.repository

import com.horain.model.AgentFeedback
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

/**
 * JPA repository for user feedback on agent turns.
 */
interface AgentFeedbackRepository : JpaRepository<AgentFeedback, UUID> {

    fun findByTurnId(turnId: UUID): Optional<AgentFeedback>

    fun findByRating(rating: String): List<AgentFeedback>
}
