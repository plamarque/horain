package com.horain.chat;

import com.horain.llm.*;
import com.horain.tools.ToolExecutorService;
import com.horain.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the chat flow: receives user message, calls LLM with tools,
 * executes requested tools, loops until final response.
 */
@Service
public class LlmChatService {

    private static final Logger log = LoggerFactory.getLogger(LlmChatService.class);
    private static final int MAX_TOOL_ITERATIONS = 10;

    private static final String SYSTEM_PROMPT = """
            You are Horain, a personal time logging assistant. You help users log time spent on projects and answer questions about their tracked time.

            Rules:
            - Use the available tools to read and write data. You never guess data.
            - For logging time: extract project name, duration (in minutes), and optional note from the user's message.
            - Duration: "une demi heure" / "demi-heure" / "half hour" = 30 min. "1h30" = 90 min. Support French and English.
            - Multiple entries in one message: process each separately. E.g. "2H sur Horain et une demi heure sur festibask" = two create_time_log calls.
            - Follow-ups like "et une demi heure sur festibask" (and X on Y) are additional entries; use conversation history for context.
            - Search for projects by name before creating or logging. If multiple projects match, ask which one.
            - If the project doesn't exist, create it with create_project then log time.
            - For time queries ("combien de temps?", "how many hours?", "what did I do?"): use get_current_datetime first, then sum_time_for_period or get_time_logs_for_period.
            - When you need "this week" or "today" or "this month", call get_current_datetime to get the correct start/end timestamps.
            - IMPORTANT: Once you have the tool results needed to answer, respond with a clear text summary. Do NOT make additional tool calls.
            - Be concise and friendly. Confirm actions clearly.
            - When the user makes a correction: they refer to the previous action. Keep the same project; only change what they correct.
            """;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ToolExecutorService toolExecutor;

    public LlmChatService(LlmClient llmClient, ToolRegistry toolRegistry, ToolExecutorService toolExecutor) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
    }

    public ChatResponse chat(String userMessage, List<ChatHistoryEntry> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(SYSTEM_PROMPT));
        if (history != null && !history.isEmpty()) {
            for (ChatHistoryEntry e : history) {
                if (e != null && e.role() != null && e.content() != null) {
                    String role = e.role().toLowerCase();
                    if ("user".equals(role)) {
                        messages.add(ChatMessage.user(e.content()));
                    } else if ("assistant".equals(role)) {
                        messages.add(ChatMessage.assistant(e.content()));
                    }
                }
            }
        }
        messages.add(ChatMessage.user(userMessage));

        List<ToolDefinition> tools = toolRegistry.getAllTools();
        List<ToolCallRecord> toolCallsExecuted = new ArrayList<>();

        for (int iterations = 0; iterations < MAX_TOOL_ITERATIONS; iterations++) {
            LlmResponse response = llmClient.chat(messages, tools);

            if (iterations > 0) {
                log.debug("Tool iteration {} for message: {}", iterations, userMessage);
            }

            if (!response.hasToolCalls()) {
                return new ChatResponse(
                        response.content() != null && !response.content().isBlank()
                                ? response.content()
                                : "I'm sorry, I couldn't generate a response.",
                        toolCallsExecuted,
                        null);
            }

            // Append assistant message with tool_calls
            messages.add(ChatMessage.assistantWithToolCalls(
                    response.content() != null ? response.content() : "",
                    response.toolCalls()));

            // Execute each tool and append tool result messages
            for (ToolCallRequest tc : response.toolCalls()) {
                ToolCallResult result = toolExecutor.execute(tc);
                toolCallsExecuted.add(new ToolCallRecord(tc.name(), tc.arguments(), result.content()));
                messages.add(ChatMessage.tool(result.content(), result.toolCallId()));
            }
        }

        log.warn("Max tool iterations reached ({}): {} - tool calls so far: {}",
                MAX_TOOL_ITERATIONS, userMessage, toolCallsExecuted.stream().map(ToolCallRecord::name).toList());
        return new ChatResponse(
                "I'm sorry, I reached the maximum number of steps. Please try a simpler request.",
                toolCallsExecuted,
                null);
    }
}
