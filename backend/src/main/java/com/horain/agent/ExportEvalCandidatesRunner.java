package com.horain.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.horain.model.AgentFeedback;
import com.horain.model.AgentTurn;
import com.horain.repository.AgentFeedbackRepository;
import com.horain.repository.AgentTurnRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exports eval candidates (turns with thumbs-down or tool/empty errors) to JSONL
 * for triage and promotion to Promptfoo. Run with profile "export" and optional
 * --export.output=path (default: stdout).
 */
@Component
@Order(Integer.MAX_VALUE)
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "export")
public class ExportEvalCandidatesRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ExportEvalCandidatesRunner.class);

    private final AgentTurnRepository turnRepository;
    private final AgentFeedbackRepository feedbackRepository;
    private final ObjectMapper objectMapper;

    public ExportEvalCandidatesRunner(AgentTurnRepository turnRepository,
                                       AgentFeedbackRepository feedbackRepository,
                                       ObjectMapper objectMapper) {
        this.turnRepository = turnRepository;
        this.feedbackRepository = feedbackRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        String outputPath = null;
        for (String arg : args) {
            if (arg.startsWith("--export.output=")) {
                outputPath = arg.substring("--export.output=".length()).trim();
                break;
            }
        }

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

        try (BufferedWriter writer = outputPath != null && !outputPath.isBlank()
                ? Files.newBufferedWriter(Path.of(outputPath), StandardCharsets.UTF_8)
                : new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
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
                writer.write(objectMapper.writeValueAsString(row));
                writer.newLine();
            }
        }
        log.info("Exported {} eval candidates to {}", turns.size(), outputPath != null ? outputPath : "stdout");
        System.exit(0);
    }
}
