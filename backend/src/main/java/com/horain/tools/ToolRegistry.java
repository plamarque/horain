package com.horain.tools;

import com.horain.llm.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Registry of available tools for the LLM.
 * Each tool has a name, description, and JSON schema for parameters.
 */
@Component
public class ToolRegistry {

    public static final String LIST_PROJECTS = "list_projects";
    public static final String SEARCH_PROJECT = "search_project";
    public static final String CREATE_PROJECT = "create_project";
    public static final String CREATE_TIME_LOG = "create_time_log";
    public static final String GET_RECENT_LOGS = "get_recent_logs";
    public static final String GET_TIME_LOGS_FOR_PERIOD = "get_time_logs_for_period";
    public static final String SUM_TIME_BY_PROJECT = "sum_time_by_project";
    public static final String SUM_TIME_FOR_PERIOD = "sum_time_for_period";
    public static final String GET_CURRENT_DATETIME = "get_current_datetime";
    public static final String GET_TIME_AGGREGATED_FOR_CHART = "get_time_aggregated_for_chart";
    public static final String PROPOSE_CHART = "propose_chart";
    public static final String PROPOSE_ENTRIES = "propose_entries";
    public static final String UPDATE_TIME_LOG = "update_time_log";
    public static final String DELETE_TIME_LOG = "delete_time_log";

    public List<ToolDefinition> getAllTools() {
        return List.of(
                new ToolDefinition(
                        LIST_PROJECTS,
                        "List all projects. Use to see available projects before logging time or when answering questions about projects.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of()
                        )
                ),
                new ToolDefinition(
                        SEARCH_PROJECT,
                        "Search for projects by name (fuzzy match). Returns projects whose name contains the query. Use when the user mentions a project name to find matching projects.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "name", Map.of(
                                                "type", "string",
                                                "description", "Project name or partial name to search for"
                                        )
                                ),
                                "required", List.of("name")
                        )
                ),
                new ToolDefinition(
                        CREATE_PROJECT,
                        "Create a new project. Use when the user wants to log time on a project that does not exist yet.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "name", Map.of(
                                                "type", "string",
                                                "description", "Project name"
                                        ),
                                        "description", Map.of(
                                                "type", "string",
                                                "description", "Optional project description"
                                        )
                                ),
                                "required", List.of("name")
                        )
                ),
                new ToolDefinition(
                        CREATE_TIME_LOG,
                        "Create a time log entry. Record time spent on a project. Requires project ID from list_projects or search_project.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "projectId", Map.of(
                                                "type", "string",
                                                "description", "UUID of the project"
                                        ),
                                        "durationMinutes", Map.of(
                                                "type", "integer",
                                                "description", "Duration in minutes"
                                        ),
                                        "note", Map.of(
                                                "type", "string",
                                                "description", "Optional note describing the work"
                                        ),
                                        "loggedAt", Map.of(
                                                "type", "string",
                                                "description", "ISO-8601 datetime when the work was done. Omit for now."
                                        )
                                ),
                                "required", List.of("projectId", "durationMinutes")
                        )
                ),
                new ToolDefinition(
                        GET_RECENT_LOGS,
                        "Get the most recent time log entries. Use to answer 'what did I do today?' or show recent activity.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "limit", Map.of(
                                                "type", "integer",
                                                "description", "Maximum number of logs to return (1-50)",
                                                "default", 20
                                        )
                                ),
                                "required", List.of()
                        )
                ),
                new ToolDefinition(
                        GET_TIME_LOGS_FOR_PERIOD,
                        "Get time logs for a date range. Use for 'what did I do today/week/month?' or to list entries.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "start", Map.of(
                                                "type", "string",
                                                "description", "Start of period (ISO-8601)"
                                        ),
                                        "end", Map.of(
                                                "type", "string",
                                                "description", "End of period (ISO-8601)"
                                        ),
                                        "projectId", Map.of(
                                                "type", "string",
                                                "description", "Optional project UUID to filter by"
                                        )
                                ),
                                "required", List.of("start", "end")
                        )
                ),
                new ToolDefinition(
                        SUM_TIME_BY_PROJECT,
                        "Sum total logged time for a specific project in a period. Use for 'how many hours on X this week?'",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "projectId", Map.of(
                                                "type", "string",
                                                "description", "Project UUID"
                                        ),
                                        "start", Map.of(
                                                "type", "string",
                                                "description", "Start of period (ISO-8601)"
                                        ),
                                        "end", Map.of(
                                                "type", "string",
                                                "description", "End of period (ISO-8601)"
                                        )
                                ),
                                "required", List.of("projectId", "start", "end")
                        )
                ),
                new ToolDefinition(
                        SUM_TIME_FOR_PERIOD,
                        "Sum total logged time for a period across all projects. Use for 'how much time this month?'",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "start", Map.of(
                                                "type", "string",
                                                "description", "Start of period (ISO-8601)"
                                        ),
                                        "end", Map.of(
                                                "type", "string",
                                                "description", "End of period (ISO-8601)"
                                        )
                                ),
                                "required", List.of("start", "end")
                        )
                ),
                new ToolDefinition(
                        GET_CURRENT_DATETIME,
                        "Get the current server date and time with timezone. Use to determine 'today', 'this week', 'this month' when the user asks relative time questions.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of()
                        )
                ),
                new ToolDefinition(
                        GET_TIME_AGGREGATED_FOR_CHART,
                        "Get time aggregated for chart display. Use when the user asks analytical questions ('what did I work on this week?', 'how much time per project?') and you want to show a chart. groupBy: 'day_and_project' for stacked bar (hours by project per day), 'project_only' for pie (distribution by project).",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "start", Map.of(
                                                "type", "string",
                                                "description", "Start of period (ISO-8601)"
                                        ),
                                        "end", Map.of(
                                                "type", "string",
                                                "description", "End of period (ISO-8601)"
                                        ),
                                        "groupBy", Map.of(
                                                "type", "string",
                                                "description", "day_and_project for stacked bar, project_only for pie"
                                        )
                                ),
                                "required", List.of("start", "end", "groupBy")
                        )
                ),
                new ToolDefinition(
                        PROPOSE_CHART,
                        "Propose a chart to display in the conversation. Call this after get_time_aggregated_for_chart when you have data to visualize. chartType: stackedBar (hours by project per day), pie (distribution by project), bar (simple bar chart). Pass the categories and series from the aggregation result.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "chartType", Map.of(
                                                "type", "string",
                                                "description", "stackedBar, pie, or bar"
                                        ),
                                        "title", Map.of(
                                                "type", "string",
                                                "description", "Chart title"
                                        ),
                                        "categories", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "string"),
                                                "description", "X-axis labels or pie segments"
                                        ),
                                        "series", Map.of(
                                                "type", "array",
                                                "items", Map.of(
                                                        "type", "object",
                                                        "properties", Map.of(
                                                                "name", Map.of("type", "string"),
                                                                "data", Map.of(
                                                                        "type", "array",
                                                                        "items", Map.of("type", "number")
                                                                )
                                                        )
                                                ),
                                                "description", "Data series"
                                        )
                                ),
                                "required", List.of("chartType", "title", "categories", "series")
                        )
                ),
                new ToolDefinition(
                        PROPOSE_ENTRIES,
                        "Propose time log entries to display in the conversation. Call this after get_time_logs_for_period or get_recent_logs when the user asked for a list of entries, details, or 'what did I log'. Pass the time_logs array from the tool result. The UI will display them in a table.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "entries", Map.of(
                                                "type", "array",
                                                "items", Map.of(
                                                        "type", "object",
                                                        "properties", Map.of(
                                                                "id", Map.of("type", "string", "description", "UUID of the time log"),
                                                                "projectId", Map.of("type", "string", "description", "UUID of the project"),
                                                                "projectName", Map.of("type", "string"),
                                                                "durationMinutes", Map.of("type", "integer"),
                                                                "note", Map.of("type", "string"),
                                                                "loggedAt", Map.of("type", "string")
                                                        )
                                                ),
                                                "description", "Time log entries from get_time_logs_for_period or get_recent_logs"
                                        )
                                ),
                                "required", List.of("entries")
                        )
                ),
                new ToolDefinition(
                        UPDATE_TIME_LOG,
                        "Update an existing time log entry. Use when the user asks to edit, change, or correct an entry (e.g. change duration, update note). Only provided fields are updated.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "id", Map.of(
                                                "type", "string",
                                                "description", "UUID of the time log to update"
                                        ),
                                        "durationMinutes", Map.of(
                                                "type", "integer",
                                                "description", "New duration in minutes"
                                        ),
                                        "note", Map.of(
                                                "type", "string",
                                                "description", "New note"
                                        ),
                                        "loggedAt", Map.of(
                                                "type", "string",
                                                "description", "New logged-at timestamp (ISO-8601)"
                                        ),
                                        "projectId", Map.of(
                                                "type", "string",
                                                "description", "New project UUID or name"
                                        )
                                ),
                                "required", List.of("id")
                        )
                ),
                new ToolDefinition(
                        DELETE_TIME_LOG,
                        "Delete a time log entry. Use when the user asks to remove or delete an entry.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "id", Map.of(
                                                "type", "string",
                                                "description", "UUID of the time log to delete"
                                        )
                                ),
                                "required", List.of("id")
                        )
                )
        );
    }
}
