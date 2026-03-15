package com.horain.llm;

/**
 * Complexity level of a user request, used to route to the appropriate LLM
 * (simple: no reasoning, complex: reasoning mini, very complex: reasoning large).
 */
public enum ComplexityLevel {

    /** Short confirmations, simple lists; use fast model without reasoning. */
    SIMPLE,

    /** Disambiguation, single project, one or two tools; use reasoning model with medium effort. */
    COMPLEX,

    /** Multi-step, analytics, mass operations, formulas; use reasoning model with high effort. */
    VERY_COMPLEX
}
