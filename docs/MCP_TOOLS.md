# MCP Tools Specification

## Purpose

The MCP (Model Context Protocol) server exposes tools that allow the conversation agent to interact with the system. **These tools are the only way the agent can read or write data.** The agent never manipulates the database directly.

## Tools

| Tool | Input | Output | Description |
|------|-------|--------|-------------|
| `list_projects` | — | `projects[]` | Returns all existing projects. |
| `search_project` | `name` (string) | `matching_projects[]` | Fuzzy search by project name. Returns projects whose name matches (exact or similar). |
| `create_project` | `name` (string), `description` (string, optional) | `project` | Creates a new project. Returns the created project. |
| `create_time_log` | `projectId` (UUID), `durationMinutes` (int), `note` (optional), `loggedAt` (ISO-8601, optional) | `time_log` | Records a time entry for the given project. Returns the created time_log. |
| `get_recent_logs` | `limit` (int, optional) | `time_logs[]` | Returns the most recent time logs (default 20, max 50). |
| `get_time_logs_for_period` | `start`, `end` (ISO-8601), `projectId` (optional) | `time_logs[]` | Returns logs in the date range. |
| `propose_entries` | `entries` (array of {id, projectId, projectName, durationMinutes, note, loggedAt}) | `status` | Proposes time log entries for structured table display in the UI. Call after get_time_logs_for_period or get_recent_logs. |
| `update_time_log` | `id` (UUID), `durationMinutes`, `note`, `loggedAt`, `projectId` (all optional except id) | `time_log` | Updates an existing time log. Only provided fields are changed. |
| `delete_time_log` | `id` (UUID) | `status` | Deletes a time log entry. |
| `sum_time_by_project` | `projectId`, `start`, `end` (ISO-8601) | `totalMinutes`, `totalHours` | Sums logged time for a project in the period. |
| `sum_time_for_period` | `start`, `end` (ISO-8601) | `totalMinutes`, `totalHours` | Sums total logged time in the period. |
| `get_current_datetime` | — | `iso`, `timezone`, period bounds | Returns current server datetime and period bounds (today, week, month). |

## Constraints

- **Single data path:** The agent must use these tools for all data operations. No direct Supabase access from the agent.
- **Idempotency:** create_project and create_time_log create new records; update_time_log and delete_time_log modify existing entries.
- **Validation:** Tools validate inputs (e.g. project_id exists, duration_minutes > 0) and return errors when invalid.

## Implementation Notes

- `search_project` should support fuzzy matching (e.g. "HatCast" matches "HatCast V1", "HatCast V2").
- `list_recent_logs` order: most recent first. Limit (e.g. 50) to be defined.
- `log_time` timestamp: defaults to "now" if not provided; used for created_at.
