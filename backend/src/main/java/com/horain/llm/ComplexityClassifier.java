package com.horain.llm;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Classifies user message complexity into SIMPLE, COMPLEX, or VERY_COMPLEX
 * using heuristic rules (no extra LLM call). Used by RoutingLlmClient to select
 * the appropriate model and reasoning budget.
 */
@Component
public class ComplexityClassifier {

    private static final int SIMPLE_MAX_WORDS = 6;
    private static final int VERY_COMPLEX_MIN_WORDS = 25;

    /** Patterns that suggest a closed question from the assistant (user is likely answering). */
    private static final List<Pattern> CLOSED_QUESTION_PATTERNS = List.of(
            Pattern.compile("which one\\??\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("confirm\\??\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("yes or no\\??\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("oui ou non\\??\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(yes|no|oui|non)\\s*\\?\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("go ahead\\??\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(the )?first one\\??\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(the )?second one\\??\\s*$", Pattern.CASE_INSENSITIVE));

    /** Keywords that indicate very complex tasks (analytics, mass ops, formulas). */
    private static final List<String> VERY_COMPLEX_KEYWORDS = List.of(
            "toutes", "tous", "all entries", "delete all", "toutes les", "tous les",
            "répartition", "répart", "taux", "occupation", "taux d'occupation",
            "chart", "graph", "graphique", "courbe",
            "bascule", "bascule toutes", "set all",
            "facturable vs", "billable vs", "facturables vs", "heures facturables",
            "non facturable", "non billable", "combien d'heures", "how many hours",
            "par projet", "per project", "par jour", "per day",
            "cette semaine", "this week", "cette mois", "this month");

    /**
     * Classifies the complexity of the current user message given optional last assistant message.
     *
     * @param userMessage         Current user message (never null; may be blank).
     * @param lastAssistantMessage Last assistant message from history, or null if none.
     * @return SIMPLE, COMPLEX, or VERY_COMPLEX.
     */
    public ComplexityLevel classify(String userMessage, String lastAssistantMessage) {
        if (userMessage == null) {
            userMessage = "";
        }
        String trimmed = userMessage.trim();
        String lastAssistant = lastAssistantMessage != null ? lastAssistantMessage.trim() : "";

        // VERY_COMPLEX: keywords or long message suggesting multi-step / analytics
        if (isVeryComplex(trimmed)) {
            return ComplexityLevel.VERY_COMPLEX;
        }

        // SIMPLE: very short message and last assistant asked a closed question (confirmation/correction)
        if (isSimple(trimmed, lastAssistant)) {
            return ComplexityLevel.SIMPLE;
        }

        return ComplexityLevel.COMPLEX;
    }

    private boolean isVeryComplex(String userMessage) {
        if (userMessage.length() == 0) return false;
        String lower = userMessage.toLowerCase(Locale.ROOT);
        for (String kw : VERY_COMPLEX_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        int wordCount = userMessage.split("\\s+").length;
        return wordCount >= VERY_COMPLEX_MIN_WORDS;
    }

    private boolean isSimple(String userMessage, String lastAssistantMessage) {
        int wordCount = userMessage.split("\\s+").length;
        if (wordCount > SIMPLE_MAX_WORDS) return false;
        if (lastAssistantMessage.isBlank()) return false;
        for (Pattern p : CLOSED_QUESTION_PATTERNS) {
            if (p.matcher(lastAssistantMessage).find()) return true;
        }
        return false;
    }
}
