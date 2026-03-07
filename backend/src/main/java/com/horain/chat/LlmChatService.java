package com.horain.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horain.llm.*;
import com.horain.tools.ToolExecutorService;
import com.horain.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            - projectId: sum_time_by_project, get_time_logs_for_period, create_time_log accept EITHER a project UUID OR a project name (e.g. "HatCast"). If you pass a name, the system resolves it automatically.
            - For logging time: extract project name, duration (in minutes), and optional note from the user's message.
            - Duration: "une demi heure" / "demi-heure" / "half hour" = 30 min. "1h30" = 90 min. Support French and English.
            - Multiple entries in one message: process each separately. E.g. "2H sur Horain et une demi heure sur festibask" = two create_time_log calls.
            - Follow-ups like "et une demi heure sur festibask" (and X on Y) are additional entries; use conversation history for context.
            - Search for projects by name before creating or logging. If multiple projects match, ask which one.
            - If the project doesn't exist, create it with create_project then log time.
            - For time queries ("combien de temps?", "how many hours?", "what did I do?"): use get_current_datetime first, then sum_time_for_period or get_time_logs_for_period.
            - When you need "this week" or "today" or "this month", call get_current_datetime to get the correct start/end timestamps.
            - For listing entries ("les entrées", "détails", "qu'est-ce que j'ai logué?", "what did I log?", "show me my entries"): call get_time_logs_for_period or get_recent_logs, then MUST call propose_entries with the full time_logs array (including id, projectId, projectName for each entry). Do NOT summarize entries in your text; the UI displays them in a table. Keep your text response brief (e.g. "Here are your entries for this week.").
            - When the user asks to edit, change, or correct an entry (e.g. "change duration to 45 min", "update the note", "fix that entry"): use update_time_log with the entry id and the new values.
            - When the user asks to delete or remove an entry: use delete_time_log with the entry id. When context entries are provided (user has selected entries), those entries include their ids; use them for edit/delete.
            - For analytical questions ("sur quoi j'ai travaillé cette semaine?", "what did I work on this week?", "répartition par projet", "hours per project", "un chart"): call get_current_datetime, then get_time_aggregated_for_chart with groupBy "day_and_project" (for stacked bar) or "project_only" (for pie), then propose_chart with chartType "stackedBar", "pie", or "bar". Use stackedBar for day x project view, pie for project distribution. Include a short text summary. You MUST call propose_chart to show a chart; never output markdown image syntax like ![...](url).
            - IMPORTANT: Once you have the tool results needed to answer, respond with a clear text summary. Do NOT make additional tool calls.
            - CRITICAL: When a tool returns an error (e.g. {"error": "..."}), inform the user clearly. Never invent or assume data when tools fail.
            - Be concise and friendly. Confirm actions clearly.
            - When the user makes a correction: they refer to the previous action. Keep the same project; only change what they correct.
            """;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ToolExecutorService toolExecutor;
    private final ObjectMapper objectMapper;

    public LlmChatService(LlmClient llmClient, ToolRegistry toolRegistry, ToolExecutorService toolExecutor,
                          ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
    }

    public ChatResponse chat(String userMessage, List<ChatHistoryEntry> history,
                             List<Map<String, Object>> contextEntries) {
        List<ChatMessage> messages = new ArrayList<>();
        String systemPrompt = SYSTEM_PROMPT;
        if (contextEntries != null && !contextEntries.isEmpty()) {
            try {
                String contextJson = objectMapper.writeValueAsString(contextEntries);
                systemPrompt += "\n\n[Context] The user has selected these time log entries. Use their ids for update_time_log or delete_time_log when asked: "
                        + contextJson;
            } catch (Exception e) {
                log.debug("Failed to serialize context entries: {}", e.getMessage());
            }
        }
        messages.add(ChatMessage.system(systemPrompt));
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
                Object chartData = extractChartDataFromToolCalls(toolCallsExecuted);
                Object timeLogsData = extractTimeLogsFromToolCalls(toolCallsExecuted);
                Map<String, Object> data = new HashMap<>();
                if (chartData != null) data.put("chart", chartData);
                if (timeLogsData != null) data.put("timeLogs", timeLogsData);
                return new ChatResponse(
                        response.content() != null && !response.content().isBlank()
                                ? response.content()
                                : "I'm sorry, I couldn't generate a response.",
                        toolCallsExecuted,
                        data.isEmpty() ? null : data);
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
        Object chartData = extractChartDataFromToolCalls(toolCallsExecuted);
        Object timeLogsData = extractTimeLogsFromToolCalls(toolCallsExecuted);
        Map<String, Object> data = new HashMap<>();
        if (chartData != null) data.put("chart", chartData);
        if (timeLogsData != null) data.put("timeLogs", timeLogsData);
        return new ChatResponse(
                "I'm sorry, I reached the maximum number of steps. Please try a simpler request.",
                toolCallsExecuted,
                data.isEmpty() ? null : data);
    }

    private Object extractChartDataFromToolCalls(List<ToolCallRecord> toolCallsExecuted) {
        ToolCallRecord lastProposeChart = null;
        for (int i = toolCallsExecuted.size() - 1; i >= 0; i--) {
            ToolCallRecord tc = toolCallsExecuted.get(i);
            if (ToolRegistry.PROPOSE_CHART.equals(tc.name())) {
                lastProposeChart = tc;
                break;
            }
        }
        if (lastProposeChart == null || lastProposeChart.arguments() == null) {
            return null;
        }
        try {
            JsonNode args = objectMapper.readTree(lastProposeChart.arguments());
            Map<String, Object> chart = new HashMap<>();
            if (args.has("chartType")) chart.put("type", args.get("chartType").asText());
            if (args.has("title")) chart.put("title", args.get("title").asText());
            if (args.has("categories")) {
                List<String> cat = new ArrayList<>();
                for (JsonNode c : args.get("categories")) cat.add(c.asText());
                chart.put("categories", cat);
            }
            if (args.has("series")) {
                List<Map<String, Object>> series = new ArrayList<>();
                for (JsonNode s : args.get("series")) {
                    Map<String, Object> item = new HashMap<>();
                    if (s.has("name")) item.put("name", s.get("name").asText());
                    if (s.has("data")) {
                        List<Double> data = new ArrayList<>();
                        for (JsonNode d : s.get("data")) data.add(d.isNumber() ? d.asDouble() : 0);
                        item.put("data", data);
                    }
                    series.add(item);
                }
                chart.put("series", series);
            }
            return chart;
        } catch (Exception e) {
            log.debug("Failed to parse propose_chart arguments: {}", e.getMessage());
            return null;
        }
    }

    private Object extractTimeLogsFromToolCalls(List<ToolCallRecord> toolCallsExecuted) {
        // Prefer propose_entries (structured display like propose_chart); fall back to raw tool result
        ToolCallRecord lastProposeEntries = null;
        for (int i = toolCallsExecuted.size() - 1; i >= 0; i--) {
            ToolCallRecord tc = toolCallsExecuted.get(i);
            if (ToolRegistry.PROPOSE_ENTRIES.equals(tc.name())) {
                lastProposeEntries = tc;
                break;
            }
        }
        if (lastProposeEntries != null && lastProposeEntries.arguments() != null) {
            try {
                JsonNode args = objectMapper.readTree(lastProposeEntries.arguments());
                JsonNode entriesNode = args.get("entries");
                if (entriesNode != null && entriesNode.isArray()) {
                    List<Map<String, Object>> entries = new ArrayList<>();
                    for (JsonNode entry : entriesNode) {
                        if (!entry.has("durationMinutes") || !entry.has("loggedAt")) continue;
                        Map<String, Object> map = new HashMap<>();
                        map.put("durationMinutes", entry.get("durationMinutes").asInt());
                        map.put("loggedAt", entry.get("loggedAt").asText());
                        if (entry.has("id")) map.put("id", entry.get("id").asText());
                        if (entry.has("projectId")) map.put("projectId", entry.get("projectId").asText());
                        if (entry.has("projectName")) map.put("projectName", entry.get("projectName").asText());
                        if (entry.has("note")) map.put("note", entry.get("note").asText());
                        entries.add(map);
                    }
                    if (!entries.isEmpty()) return entries;
                }
            } catch (Exception e) {
                log.debug("Failed to parse propose_entries arguments: {}", e.getMessage());
            }
        }
        // Fallback: extract from get_time_logs_for_period or get_recent_logs result
        ToolCallRecord lastLogsCall = null;
        for (int i = toolCallsExecuted.size() - 1; i >= 0; i--) {
            ToolCallRecord tc = toolCallsExecuted.get(i);
            if (ToolRegistry.GET_TIME_LOGS_FOR_PERIOD.equals(tc.name()) || ToolRegistry.GET_RECENT_LOGS.equals(tc.name())) {
                lastLogsCall = tc;
                break;
            }
        }
        if (lastLogsCall == null || lastLogsCall.result() == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(lastLogsCall.result());
            if (root.has("error")) {
                return null;
            }
            JsonNode timeLogs = root.get("time_logs");
            if (timeLogs == null || !timeLogs.isArray()) {
                return null;
            }
            List<Map<String, Object>> entries = new ArrayList<>();
            for (JsonNode entry : timeLogs) {
                Map<String, Object> map = new HashMap<>();
                if (entry.has("id")) map.put("id", entry.get("id").asText());
                if (entry.has("projectId")) map.put("projectId", entry.get("projectId").asText());
                if (entry.has("projectName")) map.put("projectName", entry.get("projectName").asText());
                if (entry.has("durationMinutes")) map.put("durationMinutes", entry.get("durationMinutes").asInt());
                if (entry.has("note")) map.put("note", entry.get("note").asText());
                if (entry.has("loggedAt")) map.put("loggedAt", entry.get("loggedAt").asText());
                entries.add(map);
            }
            return entries;
        } catch (Exception e) {
            log.debug("Failed to parse time_logs from tool result: {}", e.getMessage());
            return null;
        }
    }
}
