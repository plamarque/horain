package com.horain.llm

import java.util.function.Consumer

/**
 * Routes each request to one of three LLM clients (simple, complex, very complex)
 * based on ComplexityClassifier. Uses ThreadLocal to remember the selected client
 * for the duration of one request (multiple tool iterations). Call clearRequestScope()
 * at the end of each request to avoid leaking into the next request on the same thread.
 */
class RoutingLlmClient(
    private val classifier: ComplexityClassifier,
    private val clientSimple: LlmClient,
    private val clientComplex: LlmClient,
    private val clientVeryComplex: LlmClient,
    modelNameSimple: String?,
    modelNameComplex: String?,
    modelNameVeryComplex: String?
) : StreamingLlmClient {

    private val modelNameSimple: String = modelNameSimple ?: "simple"
    private val modelNameComplex: String = modelNameComplex ?: "complex"
    private val modelNameVeryComplex: String = modelNameVeryComplex ?: "very-complex"

    private data class RequestScope(val client: LlmClient, val modelName: String)

    private val requestScope = ThreadLocal<RequestScope?>()

    override fun isConfigured(): Boolean =
        clientSimple.isConfigured() || clientComplex.isConfigured() || clientVeryComplex.isConfigured()

    override fun chat(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse {
        val client = resolveClient(messages)
        return client.chat(messages, tools)
    }

    override fun chatStream(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        textConsumer: Consumer<String>,
        reasoningConsumer: Consumer<String>?
    ): LlmResponse {
        val client = resolveClient(messages)
        return if (client is StreamingLlmClient) {
            client.chatStream(messages, tools, textConsumer, reasoningConsumer)
        } else {
            client.chat(messages, tools)
        }
    }

    /**
     * Returns the model name selected for the current request, or null if none (e.g. before first call or after clearRequestScope).
     */
    fun getLastSelectedModel(): String? = requestScope.get()?.modelName

    /**
     * Clears the request-scoped client selection. Call at the end of each chat/chatStream handling to avoid leaking to the next request on the same thread.
     */
    fun clearRequestScope() {
        requestScope.remove()
    }

    private fun resolveClient(messages: List<ChatMessage>): LlmClient {
        requestScope.get()?.let { return it.client }
        val lastUser = extractLastUserMessage(messages)
        val lastAssistant = extractLastAssistantMessage(messages)
        val level = classifier.classify(lastUser, lastAssistant)
        val (client, modelName) = when (level) {
            ComplexityLevel.SIMPLE -> clientSimple to modelNameSimple
            ComplexityLevel.COMPLEX -> clientComplex to modelNameComplex
            ComplexityLevel.VERY_COMPLEX -> clientVeryComplex to modelNameVeryComplex
        }
        requestScope.set(RequestScope(client, modelName))
        return client
    }

    private fun extractLastUserMessage(messages: List<ChatMessage>?): String {
        if (messages == null) return ""
        for (i in messages.indices.reversed()) {
            val m = messages[i]
            if (m.role.equals("user", ignoreCase = true)) {
                return m.content ?: ""
            }
        }
        return ""
    }

    private fun extractLastAssistantMessage(messages: List<ChatMessage>?): String {
        if (messages == null) return ""
        for (i in messages.indices.reversed()) {
            val m = messages[i]
            if (m.role.equals("assistant", ignoreCase = true)) {
                return m.content ?: ""
            }
        }
        return ""
    }
}
