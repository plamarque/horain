package com.horain.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComplexityClassifierTest {

    private ComplexityClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ComplexityClassifier();
    }

    @Test
    void simple_shortConfirmationAfterWhichOne() {
        assertEquals(ComplexityLevel.SIMPLE,
                classifier.classify("the first one", "I found two projects: A and B. Which one?"));
    }

    @Test
    void simple_yesAfterConfirm() {
        assertEquals(ComplexityLevel.SIMPLE,
                classifier.classify("yes", "You are about to delete 5 entries. Confirm?"));
    }

    @Test
    void simple_ouiAfterOuiOuNon() {
        assertEquals(ComplexityLevel.SIMPLE,
                classifier.classify("oui", "Créer le projet et logger 30 min ? Oui ou non?"));
    }

    @Test
    void simple_identityQuestionWithoutHistory() {
        assertEquals(ComplexityLevel.SIMPLE,
                classifier.classify("qui es tu ?", null));
        assertEquals(ComplexityLevel.SIMPLE,
                classifier.classify("who are you", ""));
    }

    @Test
    void notSimple_whenNoLastAssistant() {
        assertEquals(ComplexityLevel.COMPLEX,
                classifier.classify("yes", null));
        assertEquals(ComplexityLevel.COMPLEX,
                classifier.classify("oui", ""));
    }

    @Test
    void notSimple_whenMessageTooLong() {
        assertEquals(ComplexityLevel.COMPLEX,
                classifier.classify("yes please the first one and also add a note",
                        "Which one?"));
    }

    @Test
    void veryComplex_keywordRepartition() {
        assertEquals(ComplexityLevel.VERY_COMPLEX,
                classifier.classify("répartition par projet cette semaine", null));
    }

    @Test
    void veryComplex_keywordChart() {
        assertEquals(ComplexityLevel.VERY_COMPLEX,
                classifier.classify("show me a chart of hours per project", null));
    }

    @Test
    void veryComplex_keywordTauxOccupation() {
        assertEquals(ComplexityLevel.VERY_COMPLEX,
                classifier.classify("quel est mon taux d'occupation sur 2 semaines ?", null));
    }

    @Test
    void veryComplex_keywordDeleteAll() {
        assertEquals(ComplexityLevel.VERY_COMPLEX,
                classifier.classify("delete all entries for Horain", null));
    }

    @Test
    void veryComplex_keywordBascule() {
        assertEquals(ComplexityLevel.VERY_COMPLEX,
                classifier.classify("bascule toutes les activités en facturable", null));
    }

    @Test
    void veryComplex_longMessage() {
        String longMsg = "I worked on several projects today including Horain for two hours and then "
                + "Meeds for another hour and also did some admin work on the internal project";
        assertEquals(ComplexityLevel.VERY_COMPLEX,
                classifier.classify(longMsg, null));
    }

    @Test
    void complex_disambiguation() {
        assertEquals(ComplexityLevel.COMPLEX,
                classifier.classify("30 minutes on HatCast", null));
    }

    @Test
    void complex_singleProjectWithDuration() {
        assertEquals(ComplexityLevel.COMPLEX,
                classifier.classify("20 minutes on Chrono EPS", null));
    }

    @Test
    void complex_unknownProject() {
        assertEquals(ComplexityLevel.COMPLEX,
                classifier.classify("40 minutes on Weather Station", null));
    }

    @Test
    void complex_typoCorrection() {
        assertEquals(ComplexityLevel.COMPLEX,
                classifier.classify("I meant Horain", "Did you mean Horain?"));
        // Last assistant is not a closed question ending with Which one? so not SIMPLE
    }

    @Test
    void nullUserMessage_treatedAsBlank() {
        assertEquals(ComplexityLevel.COMPLEX,
                classifier.classify(null, "Some assistant message"));
    }
}
