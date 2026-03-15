package com.horain.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * Routes each request to one of three LLM clients (simple, complex, very complex)
 * based on ComplexityClassifier. Uses ThreadLocal to remember the selected client
 * for the duration of one request (multiple tool iterations). Call clearRequestScope()
 * at the end of each request to avoid leaking into the next request on the same thread.
 */
public class RoutingLlmClient implements StreamingLlmClient {

    private static final class RequestScope {
        final LlmClient client;
        final String modelName;

        RequestScope(LlmClient client, String modelName) {
            this.client = client;
            this.modelName = modelName;
        }
    }

    private final ComplexityClassifier classifier;
    private final LlmClient clientSimple;
    private final LlmClient clientComplex;
    private final LlmClient clientVeryComplex;
    private final String modelNameSimple;
    private final String modelNameComplex;
    private final String modelNameVeryComplex;

    private final ThreadLocal<RequestScope> requestScope = new ThreadLocal<>();

    public RoutingLlmClient(
            ComplexityClassifier classifier,
            LlmClient clientSimple,
            LlmClient clientComplex,
            LlmClient clientVeryComplex,
            String modelNameSimple,
            String modelNameComplex,
            String modelNameVeryComplex) {
        this.classifier = classifier;
        this.clientSimple = clientSimple;
        this.clientComplex = clientComplex;
        this.clientVeryComplex = clientVeryComplex;
        this.modelNameSimple = modelNameSimple != null ? modelNameSimple : "simple";
        this.modelNameComplex = modelNameComplex != null ? modelNameComplex : "complex";
        this.modelNameVeryComplex = modelNameVeryComplex != null ? modelNameVeryComplex : "very-complex";
    }

    @Override
    public boolean isConfigured() {
        return clientSimple.isConfigured() || clientComplex.isConfigured() || clientVeryComplex.isConfigured();
    }

    @Override
    public LlmResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        LlmClient client = resolveClient(messages);
        return client.chat(messages, tools);
    }

    @Override
    public LlmResponse chatStream(
            List<ChatMessage> messages,
            List<ToolDefinition> tools,
            Consumer<String> textConsumer,
            Consumer<String> reasoningConsumer) {
        LlmClient client = resolveClient(messages);
        if (client instanceof StreamingLlmClient streaming) {
            return streaming.chatStream(messages, tools, textConsumer, reasoningConsumer);
        }
        return client.chat(messages, tools);
    }

    /**
     * Returns the model name selected for the current request, or null if none (e.g. before first call or after clearRequestScope).
     */
    public String getLastSelectedModel() {
        RequestScope scope = requestScope.get();
        return scope != null ? scope.modelName : null;
    }

    /**
     * Clears the request-scoped client selection. Call at the end of each chat/chatStream handling to avoid leaking to the next request on the same thread.
     */
    public void clearRequestScope() {
        requestScope.remove();
    }

    private LlmClient resolveClient(List<ChatMessage> messages) {
        RequestScope scope = requestScope.get();
        if (scope != null) {
            return scope.client;
        }
        String lastUser = extractLastUserMessage(messages);
        String lastAssistant = extractLastAssistantMessage(messages);
        ComplexityLevel level = classifier.classify(lastUser, lastAssistant);
        LlmClient client;
        String modelName;
        switch (level) {
            case SIMPLE -> {
                client = clientSimple;
                modelName = modelNameSimple;
            }
            case COMPLEX -> {
                client = clientComplex;
                modelName = modelNameComplex;
            }
            case VERY_COMPLEX -> {
                client = clientVeryComplex;
                modelName = modelNameVeryComplex;
            }
            default -> {
                client = clientComplex;
                modelName = modelNameComplex;
            }
        }
        requestScope.set(new RequestScope(client, modelName));
        return client;
    }

    private static String extractLastUserMessage(List<ChatMessage> messages) {
        if (messages == null) return "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m != null && "user".equalsIgnoreCase(m.role())) {
                return m.content() != null ? m.content() : "";
            }
        }
        return "";
    }

    private static String extractLastAssistantMessage(List<ChatMessage> messages) {
        if (messages == null) return "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m != null && "assistant".equalsIgnoreCase(m.role())) {
                return m.content() != null ? m.content() : "";
            }
        }
        return "";
    }
}
