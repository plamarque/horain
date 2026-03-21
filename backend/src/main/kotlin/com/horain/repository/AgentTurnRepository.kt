package com.horain.repository

import com.horain.model.AgentTurn
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

/**
 * JPA repository for agent conversation turns.
 */
interface AgentTurnRepository : JpaRepository<AgentTurn, UUID> {

    fun countByConversationId(conversationId: UUID): Long

    fun findByConversationIdOrderByTurnIndexAsc(conversationId: UUID): List<AgentTurn>

    fun findByCreatedAtAfterOrderByCreatedAtDesc(after: Instant, pageable: Pageable): List<AgentTurn>

    fun findByStatusIn(statuses: List<String>): List<AgentTurn>
}
