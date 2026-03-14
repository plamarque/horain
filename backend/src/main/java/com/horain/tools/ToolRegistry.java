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
    public static final String UPDATE_PROJECT = "update_project";
    public static final String DELETE_PROJECT = "delete_project";
    public static final String LIST_ACTIVITY_TYPES = "list_activity_types";
    public static final String CREATE_ACTIVITY_TYPE = "create_activity_type";
    public static final String UPDATE_ACTIVITY_TYPE = "update_activity_type";
    public static final String DELETE_ACTIVITY_TYPE = "delete_activity_type";
    public static final String CREATE_TIME_LOG = "create_time_log";
    public static final String GET_RECENT_LOGS = "get_recent_logs";
    public static final String GET_TIME_LOGS_FOR_PERIOD = "get_time_logs_for_period";
    public static final String SUM_TIME_BY_PROJECT = "sum_time_by_project";
    public static final String SUM_TIME_FOR_PERIOD = "sum_time_for_period";
    public static final String SUM_BILLABLE_TIME_FOR_PERIOD = "sum_billable_time_for_period";
    public static final String SUM_NON_BILLABLE_TIME_FOR_PERIOD = "sum_non_billable_time_for_period";
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
                        "Search for projects by name. Returns matching_projects (name contains query, case-insensitive). When no match is found, may also return close_matches: similar project names (typo-tolerant). If you get close_matches, propose the first one and ask the user to confirm (e.g. 'Did you mean Horain? Should I log 120 minutes on Horain?') before logging; only offer to create a new project if there are no close_matches or the user declines.",
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
                                        ),
                                        "billable", Map.of(
                                                "type", "boolean",
                                                "description", "Whether time on this project is billable by default (default true)"
                                        )
                                ),
                                "required", List.of("name")
                        )
                ),
                new ToolDefinition(
                        UPDATE_PROJECT,
                        "Update an existing project. Use when the user asks to rename, edit, or change a project (name or description). Only provided fields are updated. id accepts UUID or project name.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "id", Map.of(
                                                "type", "string",
                                                "description", "Project UUID or name to update"
                                        ),
                                        "name", Map.of(
                                                "type", "string",
                                                "description", "New project name"
                                        ),
                                        "description", Map.of(
                                                "type", "string",
                                                "description", "New project description"
                                        ),
                                        "billable", Map.of(
                                                "type", "boolean",
                                                "description", "Whether time on this project is billable by default"
                                        )
                                ),
                                "required", List.of("id")
                        )
                ),
                new ToolDefinition(
                        DELETE_PROJECT,
                        "Delete a project. Fails if the project has time log entries; inform the user and ask what to do. Use when the user asks to remove or delete a project. id accepts UUID or project name.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "id", Map.of(
                                                "type", "string",
                                                "description", "Project UUID or name to delete"
                                        )
                                ),
                                "required", List.of("id")
                        )
                ),
                new ToolDefinition(
                        LIST_ACTIVITY_TYPES,
                        "List all activity types (natures with daily rate, TJM). Use to show or match natures when the user mentions dev, IA, marketing, etc., or when managing rates.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of()
                        )
                ),
                new ToolDefinition(
                        CREATE_ACTIVITY_TYPE,
                        "Create an activity type (nature + daily rate in cents). Use when the user asks to add a new nature or rate (e.g. 'add CONSULT at 800 euros per day').",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "code", Map.of("type", "string", "description", "Short code (e.g. DEV, AI, MARK)"),
                                        "label", Map.of("type", "string", "description", "Human-readable label"),
                                        "dailyRateCents", Map.of("type", "integer", "description", "Daily rate in cents (e.g. 40000 for 400 €)"),
                                        "description", Map.of("type", "string", "description", "Optional description or detection hints for the AI (e.g. synonyms, typical phrases)")
                                ),
                                "required", List.of("code", "label", "dailyRateCents")
                        )
                ),
                new ToolDefinition(
                        UPDATE_ACTIVITY_TYPE,
                        "Update an existing activity type (label or daily rate). Use when the user asks to change a rate or rename a nature.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "code", Map.of("type", "string", "description", "Code of the activity type to update"),
                                        "label", Map.of("type", "string", "description", "New label (optional)"),
                                        "dailyRateCents", Map.of("type", "integer", "description", "New daily rate in cents (optional)"),
                                        "description", Map.of("type", "string", "description", "Optional description or detection hints for the AI (e.g. synonyms, typical phrases)")
                                ),
                                "required", List.of("code")
                        )
                ),
                new ToolDefinition(
                        DELETE_ACTIVITY_TYPE,
                        "Delete an activity type. Entries that used this nature will have their activity type cleared (set to null). Use when the user asks to remove a nature or rate.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "code", Map.of("type", "string", "description", "Code of the activity type to delete")
                                ),
                                "required", List.of("code")
                        )
                ),
                new ToolDefinition(
                        CREATE_TIME_LOG,
                        "Create a time log entry. Record time spent on a project. Requires project ID from list_projects or search_project. When the user mentions an activity nature (dev, IA, marketing, etc.), pass activityTypeCode from list_activity_types.",
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
                                                "description", "Activity date (when the work was done). ISO-8601. Omit for now."
                                        ),
                                        "billable", Map.of(
                                                "type", "boolean",
                                                "description", "Override billable for this entry (default: project's billable)"
                                        ),
                                        "activityTypeCode", Map.of(
                                                "type", "string",
                                                "description", "Optional activity nature code (e.g. DEV, AI, MARK) from list_activity_types. Set when user says 'dev', 'expertise IA', 'marketing', etc."
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
                        SUM_BILLABLE_TIME_FOR_PERIOD,
                        "Sum billable (invoicable) time for a period. Use when the user asks 'how much billable time this week?' or 'temps facturé'.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "start", Map.of("type", "string", "description", "Start of period (ISO-8601)"),
                                        "end", Map.of("type", "string", "description", "End of period (ISO-8601)")
                                ),
                                "required", List.of("start", "end")
                        )
                ),
                new ToolDefinition(
                        SUM_NON_BILLABLE_TIME_FOR_PERIOD,
                        "Sum non-billable time for a period. Use when the user asks about non-billable or non-invoiced time.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "start", Map.of("type", "string", "description", "Start of period (ISO-8601)"),
                                        "end", Map.of("type", "string", "description", "End of period (ISO-8601)")
                                ),
                                "required", List.of("start", "end")
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
                        "Get time aggregated for chart display. Use when the user asks analytical questions ('what did I work on this week?', 'how much time per project?', 'billable vs non-billable per day?') and you want to show a chart. groupBy: 'day_and_project' for stacked bar (hours by project per day), 'day_and_billable' for stacked bar (billable vs non-billable hours per day; use this for 'heures facturables vs non facturables par jour'), 'project_only' for pie (distribution by project), 'billable_vs_non_billable' for pie (Facturé vs Non facturé for whole period).",
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
                                                "description", "day_and_project or day_and_billable for stacked bar, project_only for pie, billable_vs_non_billable for whole-period billable split"
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
                                                                "billable", Map.of("type", "boolean", "description", "Whether this entry is billable"),
                                                                "loggedAt", Map.of("type", "string"),
                                                                "activityTypeCode", Map.of("type", "string"),
                                                                "activityTypeLabel", Map.of("type", "string"),
                                                                "dailyRateCents", Map.of("type", "integer")
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
                                                "description", "Activity date (when the work was done). ISO-8601. This updates the entry's activity date only; the record's updated_at is set to now server-side."
                                        ),
                                        "projectId", Map.of(
                                                "type", "string",
                                                "description", "New project UUID or name"
                                        ),
                                        "billable", Map.of(
                                                "type", "boolean",
                                                "description", "Whether this entry is billable"
                                        ),
                                        "activityTypeCode", Map.of(
                                                "type", "string",
                                                "description", "Activity nature code (e.g. DEV, AI) or omit to leave unchanged"
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
