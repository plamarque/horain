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
    public static final String SEARCH_TIME_LOGS = "search_time_logs";
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
    public static final String STORE_MEMORY = "store_memory";
    public static final String GET_MEMORIES = "get_memories";
    public static final String FORGET_MEMORY = "forget_memory";

    public List<ToolDefinition> getAllTools() {
        return List.of(
                new ToolDefinition(
                        LIST_PROJECTS,
                        "List all projects. Use to see available projects before logging time or when answering questions about projects. Do NOT use to search by name—use search_project instead. Returns: projects (id, name, description, billable).",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of()
                        )
                ),
                new ToolDefinition(
                        SEARCH_PROJECT,
                        "Search for projects by name. Do NOT use to list all projects—use list_projects instead. Returns matching_projects (name contains query, case-insensitive). When no match is found, may return close_matches (typo-tolerant); if so, propose the first and ask for confirmation before logging. Example: {\"name\": \"Horain\"} or {\"name\": \"HatCast\"}. Only offer to create a new project if no close_matches or user declines.",
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
                        "Create a new project. Use when the user wants to log time on a project that does not exist yet. Do NOT use if the project may already exist—call search_project first. Returns: project (id, name, description, billable).",
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
                                                "description", "Whether time on this project is billable by default (default true)",
                                                "default", true
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
                        "List all activity types (natures with daily rate, TJM). Use to show or match natures when the user mentions dev, IA, marketing, etc., or when managing rates. Do NOT use to create or update—use create_activity_type or update_activity_type. Returns: activity_types (code, label, dailyRateCents, description).",
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
                        "Create a time log entry. Record time spent on a project. Requires projectId from list_projects or search_project (do NOT guess or invent ids). When the user mentions an activity nature (dev, IA, marketing), pass activityTypeCode from list_activity_types. Example: {\"projectId\": \"Horain\", \"durationMinutes\": 90, \"note\": \"backend API\"} or with nature {\"projectId\": \"<uuid>\", \"durationMinutes\": 30, \"activityTypeCode\": \"DEV\"}.",
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
                        "Get the most recent time log entries. Use to answer 'what did I do today?' or show recent activity. Do NOT use for a specific date range—use get_time_logs_for_period instead. Returns: time_logs array (id, projectName, durationMinutes, note, loggedAt, billable, activity type fields).",
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
                        "Get time logs for a date range. For 'today/week/month', use start/end from the Current server time block at the end of the system message; only call get_current_datetime if you need a refresh. Use for 'what did I do today/week/month?' or to list entries. Do NOT use for 'most recent N' without a range—use get_recent_logs. Returns: time_logs array.",
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
                        SEARCH_TIME_LOGS,
                        "Search time log entries by keyword. Matches the keyword in the entry note or project name (case-insensitive). Use when the user asks to find entries containing a word or phrase (e.g. 'find logs with backend', 'entries mentioning Horain', 'recherche pie chart'). Do NOT use for listing by date—use get_time_logs_for_period or get_recent_logs. Returns: time_logs array. Then call propose_entries to display them.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of(
                                                "type", "string",
                                                "description", "Keyword or phrase to search for in note or project name"
                                        ),
                                        "limit", Map.of(
                                                "type", "integer",
                                                "description", "Maximum number of results (1-50)",
                                                "default", 20
                                        )
                                ),
                                "required", List.of("query")
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
                        "Get the current server date and time with timezone. Do NOT call when the system message already ends with 'Current server time'—use those bounds instead to save a tool round. Call only if that block is missing or the user needs an explicit fresh server read. Returns: iso, timezone, startOfToday, endOfToday, startOfWeek, endOfWeek, startOfMonth, endOfMonth.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of()
                        )
                ),
                new ToolDefinition(
                        GET_TIME_AGGREGATED_FOR_CHART,
                        "Get time aggregated for chart display. Use start/end from the Current server time block at the end of the system message (or get_current_datetime if missing). Use when the user asks analytical questions ('what did I work on this week?', 'how much time per project?', 'billable vs non-billable per day?'). groupBy: 'day_and_project' (stacked bar by project per day), 'day_and_billable' (stacked bar billable vs non-billable per day), 'project_only' (pie by project), 'billable_vs_non_billable' (pie for whole period). Example: {\"start\": \"<startOfWeek>\", \"end\": \"<endOfWeek>\", \"groupBy\": \"day_and_project\"}. Then call propose_chart with the returned categories and series.",
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
                        "Propose a chart to display in the conversation. Call ONLY after get_time_aggregated_for_chart; pass its categories and series as-is. Do NOT call without chart data. chartType: stackedBar, pie, or bar. Returns: status ok.",
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
                        "Propose time log entries to display in the conversation. Call ONLY after get_time_logs_for_period, get_recent_logs, or search_time_logs; pass the time_logs array from that result as the entries argument. Do NOT call with empty or invented data. Example: get_recent_logs(limit=10) returns time_logs; then propose_entries(entries: <that time_logs array>). The UI will display them in a table.",
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
                        "Update an existing time log entry. Use when the user asks to edit, change, or correct an entry (e.g. change duration, update note). Requires entry id from get_recent_logs, get_time_logs_for_period, or [Context] selected entries. Do NOT use create_time_log to modify—only update. Only provided fields are updated. Returns: time_log.",
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
                        "Delete a time log entry. Use when the user asks to remove or delete an entry. Requires entry id from get_recent_logs, get_time_logs_for_period, or [Context]. Returns: status deleted.",
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
                ),
                new ToolDefinition(
                        STORE_MEMORY,
                        "Store or update a long-term memory about the user. Use after the user has confirmed a disambiguation (e.g. which project they meant) or stated an explicit preference (e.g. default project). Do NOT store every project name mentioned—only store when you have a clear fact to remember (disambiguation choice, typo correction, or user said 'remember that...'). kind: project_disambiguation, typo, default_project, preference, or explicit_fact. memoryKey: logical key for this fact (e.g. ambiguous name 'HatCast' for project_disambiguation, 'default' for default_project). factText: one short sentence for the LLM (e.g. 'When the user says HatCast without specifying, they mean HatCast V2.'). Optional ttlSeconds: memory expires after that many seconds; omit for no expiry. Returns: confirmation.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "kind", Map.of(
                                                "type", "string",
                                                "description", "Memory kind: project_disambiguation, typo, default_project, preference, explicit_fact"
                                        ),
                                        "memoryKey", Map.of(
                                                "type", "string",
                                                "description", "Logical key for consolidation (e.g. 'HatCast', 'default', 'Horian')"
                                        ),
                                        "value", Map.of(
                                                "type", "string",
                                                "description", "Optional structured value (e.g. project id as string)"
                                        ),
                                        "factText", Map.of(
                                                "type", "string",
                                                "description", "One short sentence describing the fact for the LLM"
                                        ),
                                        "ttlSeconds", Map.of(
                                                "type", "integer",
                                                "description", "Optional TTL in seconds; omit for no expiration"
                                        )
                                ),
                                "required", List.of("kind", "memoryKey", "factText")
                        )
                ),
                new ToolDefinition(
                        GET_MEMORIES,
                        "Retrieve stored memories for the user. Memories are already injected into your prompt each turn; use this only to refresh or re-check after you have just stored a memory. Optional kind: filter by kind (project_disambiguation, default_project, etc.). Returns: list of facts (kind, memoryKey, factText).",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "kind", Map.of(
                                                "type", "string",
                                                "description", "Optional: filter by kind"
                                        )
                                ),
                                "required", List.of()
                        )
                ),
                new ToolDefinition(
                        FORGET_MEMORY,
                        "Forget one or more memories. Use when the user explicitly asks to forget something (e.g. 'forget my default project', 'stop remembering HatCast'). kind is required. memoryKey: if provided, forget only that key; if omitted, forget ALL memories of that kind (ask for confirmation before forgetting a whole category). Returns: confirmation.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "kind", Map.of(
                                                "type", "string",
                                                "description", "Memory kind to forget (project_disambiguation, default_project, etc.)"
                                        ),
                                        "memoryKey", Map.of(
                                                "type", "string",
                                                "description", "Optional: forget only this key; omit to forget all of this kind"
                                        )
                                ),
                                "required", List.of("kind")
                        )
                )
        );
    }
}
