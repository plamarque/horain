package com.horain.llm

import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/**
 * Wraps two LLM clients (primary and fallback) and delegates to the current one.
 * When the primary fails with a retryable error (e.g. 400/404/422 from Responses API
 * when the model or endpoint is not supported), switches to the fallback once and
 * remembers it for all subsequent calls — no retry on every request.
 */
class FallbackLlmClient(
    private val primary: StreamingLlmClient,
    private val fallback: StreamingLlmClient
) : StreamingLlmClient {

    private val current = AtomicReference<LlmClient>(primary)

    private fun isRetryableResponsesError(t: Throwable): Boolean {
        val e = t as? WebClientResponseException ?: return false
        val status = e.statusCode.value()
        return status == 400 || status == 404 || status == 422
    }

    override fun isConfigured(): Boolean = current.get().isConfigured()

    override fun chat(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse {
        val client = current.get()
        return try {
            client.chat(messages, tools)
        } catch (t: Throwable) {
            if (client === primary && isRetryableResponsesError(t)) {
                log.warn(
                    "Responses API failed ({}), switching to Chat Completions for subsequent calls",
                    (t as? WebClientResponseException)?.statusCode ?: t.message
                )
                current.set(fallback)
                return fallback.chat(messages, tools)
            }
            throw t
        }
    }

    override fun chatStream(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        textConsumer: Consumer<String>,
        reasoningConsumer: Consumer<String>?
    ): LlmResponse {
        val client = current.get()
        return try {
            (client as StreamingLlmClient).chatStream(messages, tools, textConsumer, reasoningConsumer)
        } catch (t: Throwable) {
            if (client === primary && isRetryableResponsesError(t)) {
                log.warn(
                    "Responses API stream failed ({}), switching to Chat Completions for subsequent calls",
                    (t as? WebClientResponseException)?.statusCode ?: t.message
                )
                current.set(fallback)
                return fallback.chatStream(messages, tools, textConsumer, reasoningConsumer)
            }
            throw t
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FallbackLlmClient::class.java)
    }
}
