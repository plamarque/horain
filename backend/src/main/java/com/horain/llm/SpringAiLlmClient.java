package com.horain.llm;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LlmClient implementation using Spring AI ChatModel.
 * Delegates to OpenAiChatModel (or compatible) for OpenAI-compatible APIs.
 */
public class SpringAiLlmClient implements LlmClient {

    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final ChatModel chatModel;
    private final String model;

    public SpringAiLlmClient(ChatModel chatModel, LlmProperties llmProperties) {
        this.chatModel = chatModel;
        this.model = llmProperties != null && llmProperties.model() != null ? llmProperties.model() : DEFAULT_MODEL;
    }

    @Override
    public boolean isConfigured() {
        return chatModel != null;
    }

    @Override
    public LlmResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        List<Message> springMessages = toSpringMessages(messages);
        OpenAiChatOptions options = buildChatOptions(tools);

        Prompt prompt = new Prompt(springMessages, options);
        ChatResponse response = chatModel.call(prompt);

        return toLlmResponse(response);
    }

    private List<Message> toSpringMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Message> result = new ArrayList<>();
        for (ChatMessage m : messages) {
            String role = m.role();
            if (role == null) continue;
            switch (role.toLowerCase()) {
                case "system" -> result.add(new SystemMessage(m.content() != null ? m.content() : ""));
                case "user" -> result.add(new UserMessage(m.content() != null ? m.content() : ""));
                case "assistant" -> {
                    if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                        List<AssistantMessage.ToolCall> toolCalls = m.toolCalls().stream()
                                .map(tc -> new AssistantMessage.ToolCall(
                                        tc.id(),
                                        "function",
                                        tc.name(),
                                        tc.arguments() != null ? tc.arguments() : "{}"))
                                .toList();
                        result.add(AssistantMessage.builder()
                                .content(m.content() != null ? m.content() : "")
                                .toolCalls(toolCalls)
                                .build());
                    } else {
                        result.add(new AssistantMessage(m.content() != null ? m.content() : ""));
                    }
                }
                case "tool" -> {
                    var toolResponse = new ToolResponseMessage.ToolResponse(
                            m.toolCallId() != null ? m.toolCallId() : "",
                            "",
                            m.content() != null ? m.content() : "");
                    result.add(ToolResponseMessage.builder()
                            .responses(List.of(toolResponse))
                            .build());
                }
                default -> {
                    // Ignore unknown roles
                }
            }
        }
        return result;
    }

    private OpenAiChatOptions buildChatOptions(List<ToolDefinition> tools) {
        // Explicitly set model to avoid merge bug where per-request options lose the default model
        OpenAiChatOptions options = OpenAiChatOptions.builder().model(model).build();
        // Do not let the model execute tools; we handle execution in LlmChatService
        options.setInternalToolExecutionEnabled(false);
        if (tools != null && !tools.isEmpty()) {
            List<OpenAiApi.FunctionTool> functionTools = tools.stream()
                    .map(t -> new OpenAiApi.FunctionTool(new OpenAiApi.FunctionTool.Function(
                            t.description(),
                            t.name(),
                            t.parameters() != null ? t.parameters() : Collections.emptyMap(),
                            false)))
                    .collect(Collectors.toList());
            options.setTools(functionTools);
        }
        return options;
    }

    private LlmResponse toLlmResponse(ChatResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return new LlmResponse("", null, "stop");
        }
        Generation gen = response.getResult();
        AssistantMessage output = gen.getOutput();
        if (output == null) {
            return new LlmResponse("", null, "stop");
        }

        String content = output.getText() != null ? output.getText() : "";
        List<ToolCallRequest> toolCalls = null;
        if (output.hasToolCalls() && output.getToolCalls() != null) {
            toolCalls = output.getToolCalls().stream()
                    .map(tc -> new ToolCallRequest(
                            tc.id() != null ? tc.id() : "",
                            tc.name() != null ? tc.name() : "",
                            tc.arguments() != null ? tc.arguments() : "{}"))
                    .toList();
        }

        String finishReason = "stop";
        if (gen.getMetadata() != null && gen.getMetadata().getFinishReason() != null) {
            Object reason = gen.getMetadata().getFinishReason();
            finishReason = reason instanceof Enum<?> e ? e.name().toLowerCase() : String.valueOf(reason).toLowerCase();
        }

        return new LlmResponse(content, toolCalls, finishReason);
    }
}
