package com.horain.observability

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Optional export of agent traces to external platforms (LangSmith, etc.).
 * Built-in persistence in agent_turn remains the source of truth when keepNativeTrace is true.
 */
@ConfigurationProperties(prefix = "horain.observability")
data class ObservabilityProperties(
    /** none | langsmith (langfuse reserved for a future implementation) */
    val provider: String = "none",
    /** When true (default), agent turns are always stored in the database regardless of external export. */
    val keepNativeTrace: Boolean = true,
    val langsmith: LangSmithProps = LangSmithProps()
) {
    fun resolvedProvider(): ObservabilityProvider =
        when (provider.lowercase().trim()) {
            "langsmith" -> ObservabilityProvider.LANGSMITH
            else -> ObservabilityProvider.NONE
        }
}

data class LangSmithProps(
    /** API key (same as LangChain/LangSmith dashboard; often LANGCHAIN_API_KEY). */
    val apiKey: String = "",
    val endpoint: String = "https://api.smith.langchain.com",
    /** Tracing project name as shown in LangSmith (e.g. Horain). Resolved to a session UUID via the API. */
    val project: String = "default"
)

enum class ObservabilityProvider {
    NONE,
    LANGSMITH
}
