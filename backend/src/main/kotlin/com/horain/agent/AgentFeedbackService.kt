package com.horain.agent

import com.horain.model.AgentFeedback
import com.horain.repository.AgentFeedbackRepository
import com.horain.repository.AgentTurnRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Saves or updates user feedback (thumb up/down) on agent turns.
 */
@Service
class AgentFeedbackService(
    private val feedbackRepository: AgentFeedbackRepository,
    private val turnRepository: AgentTurnRepository
) {

    /**
     * Creates or updates feedback for the given turn. Validates that the turn exists and rating is "up" or "down".
     */
    @Transactional
    fun saveFeedback(turnId: UUID, rating: String?, reasonCode: String?, comment: String?): AgentFeedback {
        if (!turnRepository.existsById(turnId)) {
            throw IllegalArgumentException("Turn not found: $turnId")
        }
        val normalizedRating = rating?.trim()?.lowercase() ?: ""
        if (normalizedRating != "up" && normalizedRating != "down") {
            throw IllegalArgumentException("Rating must be 'up' or 'down'")
        }
        var feedback = feedbackRepository.findByTurnId(turnId).orElse(null)
        if (feedback == null) {
            feedback = AgentFeedback()
            feedback.id = UUID.randomUUID()
            feedback.turnId = turnId
        }
        feedback.rating = normalizedRating
        feedback.reasonCode = if (!reasonCode.isNullOrBlank()) reasonCode else null
        feedback.comment = if (!comment.isNullOrBlank()) comment else null
        return feedbackRepository.save(feedback)
    }
}
