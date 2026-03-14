package com.horain.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Wraps two LLM clients (primary and fallback) and delegates to the current one.
 * When the primary fails with a retryable error (e.g. 400/404/422 from Responses API
 * when the model or endpoint is not supported), switches to the fallback once and
 * remembers it for all subsequent calls — no retry on every request.
 */
public class FallbackLlmClient implements StreamingLlmClient {

    private static final Logger log = LoggerFactory.getLogger(FallbackLlmClient.class);

    private final StreamingLlmClient primary;
    private final StreamingLlmClient fallback;
    private final AtomicReference<LlmClient> current;

    public FallbackLlmClient(StreamingLlmClient primary, StreamingLlmClient fallback) {
        this.primary = primary;
        this.fallback = fallback;
        this.current = new AtomicReference<>(primary);
    }

    private static boolean isRetryableResponsesError(Throwable t) {
        if (!(t instanceof WebClientResponseException e)) {
            return false;
        }
        int status = e.getStatusCode().value();
        return status == 400 || status == 404 || status == 422;
    }

    @Override
    public boolean isConfigured() {
        return current.get().isConfigured();
    }

    @Override
    public LlmResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        LlmClient client = current.get();
        try {
            return client.chat(messages, tools);
        } catch (Throwable t) {
            if (client == primary && isRetryableResponsesError(t)) {
                log.warn("Responses API failed ({}), switching to Chat Completions for subsequent calls",
                        t instanceof WebClientResponseException e ? e.getStatusCode() : t.getMessage());
                current.set(fallback);
                return fallback.chat(messages, tools);
            }
            throw t;
        }
    }

    @Override
    public LlmResponse chatStream(
            List<ChatMessage> messages,
            List<ToolDefinition> tools,
            Consumer<String> textConsumer,
            Consumer<String> reasoningConsumer) {
        LlmClient client = current.get();
        try {
            return ((StreamingLlmClient) client).chatStream(messages, tools, textConsumer, reasoningConsumer);
        } catch (Throwable t) {
            if (client == primary && isRetryableResponsesError(t)) {
                log.warn("Responses API stream failed ({}), switching to Chat Completions for subsequent calls",
                        t instanceof WebClientResponseException e ? e.getStatusCode() : t.getMessage());
                current.set(fallback);
                return fallback.chatStream(messages, tools, textConsumer, reasoningConsumer);
            }
            throw t;
        }
    }
}
