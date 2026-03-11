package com.horain.agent;

import com.horain.model.AgentFeedback;
import com.horain.model.AgentTurn;
import com.horain.repository.AgentFeedbackRepository;
import com.horain.repository.AgentTurnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Saves or updates user feedback (thumb up/down) on agent turns.
 */
@Service
public class AgentFeedbackService {

    private final AgentFeedbackRepository feedbackRepository;
    private final AgentTurnRepository turnRepository;

    public AgentFeedbackService(AgentFeedbackRepository feedbackRepository, AgentTurnRepository turnRepository) {
        this.feedbackRepository = feedbackRepository;
        this.turnRepository = turnRepository;
    }

    /**
     * Creates or updates feedback for the given turn. Validates that the turn exists and rating is "up" or "down".
     */
    @Transactional
    public AgentFeedback saveFeedback(UUID turnId, String rating, String reasonCode, String comment) {
        if (!turnRepository.existsById(turnId)) {
            throw new IllegalArgumentException("Turn not found: " + turnId);
        }
        String normalizedRating = rating != null ? rating.strip().toLowerCase() : "";
        if (!"up".equals(normalizedRating) && !"down".equals(normalizedRating)) {
            throw new IllegalArgumentException("Rating must be 'up' or 'down'");
        }
        AgentFeedback feedback = feedbackRepository.findByTurnId(turnId).orElse(null);
        if (feedback == null) {
            feedback = new AgentFeedback();
            feedback.setId(UUID.randomUUID());
            feedback.setTurnId(turnId);
        }
        feedback.setRating(normalizedRating);
        feedback.setReasonCode(reasonCode != null && !reasonCode.isBlank() ? reasonCode : null);
        feedback.setComment(comment != null && !comment.isBlank() ? comment : null);
        return feedbackRepository.save(feedback);
    }
}
