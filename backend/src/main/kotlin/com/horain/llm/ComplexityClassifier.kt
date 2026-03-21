package com.horain.llm

import org.springframework.stereotype.Component
import java.util.Locale
import java.util.regex.Pattern

/**
 * Classifies user message complexity into SIMPLE, COMPLEX, or VERY_COMPLEX
 * using heuristic rules (no extra LLM call). Used by RoutingLlmClient to select
 * the appropriate model and reasoning budget.
 */
@Component
class ComplexityClassifier {

    /**
     * Classifies the complexity of the current user message given optional last assistant message.
     *
     * @param userMessage Current user message (never null; may be blank).
     * @param lastAssistantMessage Last assistant message from history, or null if none.
     * @return SIMPLE, COMPLEX, or VERY_COMPLEX.
     */
    fun classify(userMessage: String?, lastAssistantMessage: String?): ComplexityLevel {
        var um = userMessage ?: ""
        val trimmed = um.trim()
        val lastAssistant = lastAssistantMessage?.trim() ?: ""

        if (isVeryComplex(trimmed)) {
            return ComplexityLevel.VERY_COMPLEX
        }
        if (isSimpleIdentityOrGreeting(trimmed)) {
            return ComplexityLevel.SIMPLE
        }
        if (isSimple(trimmed, lastAssistant)) {
            return ComplexityLevel.SIMPLE
        }
        return ComplexityLevel.COMPLEX
    }

    private fun isVeryComplex(userMessage: String): Boolean {
        if (userMessage.isEmpty()) return false
        val lower = userMessage.lowercase(Locale.ROOT)
        for (kw in VERY_COMPLEX_KEYWORDS) {
            if (lower.contains(kw)) return true
        }
        val wordCount = userMessage.split(Regex("\\s+")).size
        return wordCount >= VERY_COMPLEX_MIN_WORDS
    }

    private fun isSimpleIdentityOrGreeting(userMessage: String): Boolean {
        if (userMessage.isBlank()) return false
        val wordCount = userMessage.split(Regex("\\s+")).size
        if (wordCount > SIMPLE_MAX_WORDS) return false
        val lower = userMessage.lowercase(Locale.ROOT)
        for (phrase in SIMPLE_IDENTITY_GREETING_PHRASES) {
            if (lower.contains(phrase)) return true
        }
        return false
    }

    private fun isSimple(userMessage: String, lastAssistantMessage: String): Boolean {
        val wordCount = userMessage.split(Regex("\\s+")).size
        if (wordCount > SIMPLE_MAX_WORDS) return false
        if (lastAssistantMessage.isBlank()) return false
        for (p in CLOSED_QUESTION_PATTERNS) {
            if (p.matcher(lastAssistantMessage).find()) return true
        }
        return false
    }

    companion object {
        private const val SIMPLE_MAX_WORDS = 6
        private const val VERY_COMPLEX_MIN_WORDS = 25

        private val CLOSED_QUESTION_PATTERNS = listOf(
            Pattern.compile("which one\\??\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("confirm\\??\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("yes or no\\??\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("oui ou non\\??\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(yes|no|oui|non)\\s*\\?\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("go ahead\\??\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(the )?first one\\??\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(the )?second one\\??\\s*$", Pattern.CASE_INSENSITIVE)
        )

        private val SIMPLE_IDENTITY_GREETING_PHRASES = listOf(
            "qui es tu", "who are you", "what are you", "c'est quoi", "what is horain",
            "présente-toi", "introduce yourself", "what can you do", "que sais-tu faire",
            "hello", "bonjour", "salut", "hi ", "hey "
        )

        private val VERY_COMPLEX_KEYWORDS = listOf(
            "toutes", "tous", "all entries", "delete all", "toutes les", "tous les",
            "répartition", "répart", "taux", "occupation", "taux d'occupation",
            "chart", "graph", "graphique", "courbe",
            "bascule", "bascule toutes", "set all",
            "facturable vs", "billable vs", "facturables vs", "heures facturables",
            "non facturable", "non billable", "combien d'heures", "how many hours",
            "par projet", "per project", "par jour", "per day",
            "cette semaine", "this week", "cette mois", "this month"
        )
    }
}
