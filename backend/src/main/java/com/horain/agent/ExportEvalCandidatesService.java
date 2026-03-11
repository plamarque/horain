package com.horain.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.horain.model.AgentFeedback;
import com.horain.model.AgentTurn;
import com.horain.repository.AgentFeedbackRepository;
import com.horain.repository.AgentTurnRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds eval candidates (turns with thumbs-down or tool/empty errors) for export.
 * Used by the admin HTTP endpoint and optionally by the CLI export runner.
 */
@Service
public class ExportEvalCandidatesService {

    private final AgentTurnRepository turnRepository;
    private final AgentFeedbackRepository feedbackRepository;
    private final ObjectMapper objectMapper;

    public ExportEvalCandidatesService(AgentTurnRepository turnRepository,
                                       AgentFeedbackRepository feedbackRepository,
                                       ObjectMapper objectMapper) {
        this.turnRepository = turnRepository;
        this.feedbackRepository = feedbackRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns one row (map) per eval candidate, sorted by created_at descending.
     */
    public List<Map<String, Object>> getCandidates() {
        Set<UUID> turnIds = new LinkedHashSet<>();
        List<AgentFeedback> downFeedbacks = feedbackRepository.findByRating("down");
        for (AgentFeedback f : downFeedbacks) {
            turnIds.add(f.getTurnId());
        }
        List<AgentTurn> errorTurns = turnRepository.findByStatusIn(List.of("tool_error", "empty_result", "max_iterations"));
        for (AgentTurn t : errorTurns) {
            turnIds.add(t.getId());
        }

        Map<UUID, AgentFeedback> feedbackByTurn = downFeedbacks.stream()
                .collect(Collectors.toMap(AgentFeedback::getTurnId, f -> f, (a, b) -> a));

        List<AgentTurn> turns = new ArrayList<>();
        for (UUID id : turnIds) {
            turnRepository.findById(id).ifPresent(turns::add);
        }
        turns.sort(Comparator.comparing(AgentTurn::getCreatedAt).reversed());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (AgentTurn turn : turns) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source_turn_id", turn.getId().toString());
            row.put("conversation_id", turn.getConversationId().toString());
            row.put("user_message", turn.getUserMessage());
            row.put("assistant_message", turn.getAssistantMessage());
            row.put("system_prompt_version", turn.getSystemPromptVersion());
            row.put("model", turn.getModel());
            row.put("status", turn.getStatus());
            row.put("created_at", turn.getCreatedAt() != null ? turn.getCreatedAt().toString() : null);
            if (turn.getToolCallsJson() != null && !turn.getToolCallsJson().isBlank()) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> toolCalls = objectMapper.readValue(turn.getToolCallsJson(), List.class);
                    row.put("tool_calls", toolCalls);
                } catch (Exception e) {
                    row.put("tool_calls_raw", turn.getToolCallsJson());
                }
            }
            AgentFeedback fb = feedbackByTurn.get(turn.getId());
            if (fb != null) {
                row.put("feedback", fb.getRating());
                row.put("feedback_reason", fb.getReasonCode());
                row.put("feedback_comment", fb.getComment());
            } else {
                row.put("feedback", null);
            }
            row.put("expected_behavior", "");
            row.put("eval_family", "");
            row.put("assertion_strategy", "deterministic");
            rows.add(row);
        }
        return rows;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
