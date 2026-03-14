# MCP Tools Specification

## Purpose

The MCP (Model Context Protocol) server exposes tools that allow the conversation agent to interact with the system. **These tools are the only way the agent can read or write data.** The agent never manipulates the database directly.

## Design principles

Tool design in this project follows context-engineering principles so that the LLM can use tools reliably within a limited context window.

- **Tools are prompt extensions:** Descriptions are written for the LLM. Each tool should convey when to use it, when not to use it, and (where helpful) example usage. See the table and the backend `ToolRegistry` for the canonical descriptions.
- **Outputs are LLM-oriented:** Tool responses should favour readability, structure, and short summaries. Avoid raw database-style dumps or large unstructured JSON. The goal is to improve the model’s reasoning reliability.
- **Task-oriented tools:** Each tool represents a clear capability (e.g. `sum_time_by_project`, `search_project`). Prefer dedicated tools over a generic “query” API.
- **Minimize model errors:** Use a small number of parameters, descriptive names, defaults where possible, and explicit warnings for invalid use. Design for robust tool calling, not API completeness.

Full guidelines: [docs/AGENT_DESIGN.md](AGENT_DESIGN.md).

## Tools

| Tool | Input | Output | Description |
|------|-------|--------|-------------|
| `list_projects` | — | `projects[]` (each with id, name, description, billable) | Returns all existing projects. |
| `search_project` | `name` (string) | `matching_projects[]`, optionally `close_matches[]` (each project includes billable) | Search by project name (contains, case-insensitive). When no match is found, returns `close_matches`: typo-tolerant similar project names. The agent should propose the first close match and ask for confirmation before logging. |
| `create_project` | `name` (string), `description` (string, optional), `billable` (boolean, optional, default true) | `project` | Creates a new project. Returns the created project (includes billable). |
| `update_project` | `id` (UUID or name), `name` (string, optional), `description` (string, optional), `billable` (boolean, optional) | `project` | Updates an existing project. Only provided fields are changed. Returns the updated project. |
| `delete_project` | `id` (UUID or name) | `status` | Deletes a project. Fails if the project has time log entries; inform the user and ask what to do. |
| `list_activity_types` | — | `activity_types[]` (each with code, label, dailyRateCents, description optional) | Returns all activity types (natures + TJM). Use to match user wording (dev, IA, marketing) or when managing rates. Descriptions help the assistant infer the right code from user phrasing. |
| `create_activity_type` | `code` (string), `label` (string), `dailyRateCents` (int), `description` (string, optional) | `activity_type` | Creates an activity type. Code is stored uppercase. Use when the user asks to add a nature or rate (e.g. "add CONSULT at 800 €/day" → dailyRateCents 80000). |
| `update_activity_type` | `code` (string), `label` (optional), `dailyRateCents` (optional), `description` (string, optional) | `activity_type` | Updates an existing activity type. Only provided fields are changed. |
| `delete_activity_type` | `code` (string) | `status` | Deletes an activity type. Time logs that referenced this code have their activity_type_code set to NULL (ON DELETE SET NULL). |
| `create_time_log` | `projectId` (UUID or name), `durationMinutes` (int), `note` (optional), `loggedAt` (ISO-8601, optional), `billable` (boolean, optional), `activityTypeCode` (string, optional) | `time_log` | Records a time entry. If the user mentions an activity nature (dev, IA, marketing), pass the matching code from list_activity_types. Returns the created time_log with id, projectId, projectName, durationMinutes, note, billable, loggedAt, and when set activityTypeCode, activityTypeLabel, dailyRateCents. |
| `get_recent_logs` | `limit` (int, optional) | `time_logs[]` (each with id, projectId, projectName, durationMinutes, note, billable, loggedAt, and when set activityTypeCode, activityTypeLabel, dailyRateCents) | Returns the most recent time logs (default 20, max 50). |
| `get_time_logs_for_period` | `start`, `end` (ISO-8601), `projectId` (optional) | `time_logs[]` | Returns logs in the date range (each entry includes billable and, when set, activity type fields). |
| `propose_entries` | `entries` (array of {id, projectId, projectName, durationMinutes, note, billable, loggedAt, activityTypeCode?, activityTypeLabel?, dailyRateCents?}) | `status` | Proposes time log entries for display. Include activity type fields when present so the UI can show the amount in €. |
| `update_time_log` | `id` (UUID), `durationMinutes`, `note`, `loggedAt`, `projectId`, `billable`, `activityTypeCode` (string or omit; send empty to clear) (all optional except id) | `time_log` | Updates an existing time log. Returns the updated time_log with activity type fields when set. |
| `delete_time_log` | `id` (UUID) | `status` | Deletes a time log entry. |
| `sum_time_by_project` | `projectId`, `start`, `end` (ISO-8601) | `totalMinutes`, `totalHours` | Sums logged time for a project in the period. |
| `sum_time_for_period` | `start`, `end` (ISO-8601) | `totalMinutes`, `totalHours` | Sums total logged time in the period. |
| `sum_billable_time_for_period` | `start`, `end` (ISO-8601) | `totalMinutes`, `totalHours` | Sums billable (invoicable) time in the period. Use for "how much billable time?" or "temps facturé". |
| `sum_non_billable_time_for_period` | `start`, `end` (ISO-8601) | `totalMinutes`, `totalHours` | Sums non-billable time in the period. |
| `get_time_aggregated_for_chart` | `start`, `end`, `groupBy` | categories, series | `groupBy`: `day_and_project` (stacked bar by project per day), `day_and_billable` (stacked bar billable vs non-billable per day), `project_only` (pie), `billable_vs_non_billable` (pie for whole period). |
| `get_current_datetime` | — | `iso`, `timezone`, period bounds | Returns current server datetime and period bounds (today, week, month). |

## When adding a new tool

When documenting or implementing a new tool (in this spec and in `ToolRegistry`), follow this template so that descriptions stay LLM-oriented and consistent with [docs/AGENT_DESIGN.md](AGENT_DESIGN.md):

- **Purpose** — What the tool does in one sentence.
- **When to use** — Explicit guidance (e.g. “Use when the user asks…”, “Call after get_current_datetime when…”).
- **When not to use** — Explicit guidance (e.g. “Do NOT use to list all projects; use list_projects instead.”).
- **Example calls** — Optional but recommended; one or two valid invocations.
- **Parameters** — Name, type, and a short description for each; indicate required vs optional and defaults.
- **Expected output format** — What the tool returns (fields, structure) so the model can reason on the result.

The table above is the contractual spec; this template describes how to write each row and the corresponding `ToolDefinition` in code.

## Constraints

- **Single data path:** The agent must use these tools for all data operations. No direct Supabase access from the agent.
- **Idempotency:** create_project and create_time_log create new records; update_project, update_time_log and delete_project, delete_time_log modify existing entries.
- **Validation:** Tools validate inputs (e.g. project_id exists, duration_minutes > 0) and return errors when invalid.

## Mass Deletion Safeguards

- **Project deletion:** `delete_project` fails if the project has any time log entries. The agent must inform the user and ask explicitly before deleting entries then the project.
- **Mass time log deletion:** The agent must ask for explicit user confirmation before deleting more than 3 entries in one turn. Never suggest or perform mass deletions without confirmation.

## Mass update and “all activities” without date

- **No date specified:** When the user asks to change “all” or “toutes” activities for a project (e.g. “bascule toutes les activités associées à X en facturable”) without specifying a period, the agent must use an all-time range: `get_time_logs_for_period` with `start` = `2000-01-01T00:00:00Z` and `end` = `endOfMonth` from `get_current_datetime`. It must not assume an arbitrary month (e.g. October).
- **Confirmation:** Before applying a mass update (e.g. setting many entries to billable), the agent must state how many entries are concerned and ask for explicit confirmation; it must not call `update_time_log` in a loop until the user has confirmed.

## Implementation Notes

- `create_time_log` and `update_time_log` return a `time_log` object including `projectName` (resolved from the project). The UI displays this in the structured table after create/update so the user can verify the action.
- `search_project`: name contains (case-insensitive) returns `matching_projects`; when empty, backend may return `close_matches` (typo-tolerant similarity). The agent proposes the close match and asks for confirmation before logging.
- `list_recent_logs` order: most recent first. Limit (e.g. 50) to be defined.
- `create_time_log` `loggedAt`: Activity date (when the work was done). Defaults to "now" if not provided. Displayed in the table. `created_at` (when the user entered the record) is set server-side.
- **Billable:** Projects have a `billable` flag (default true). New time entries inherit the project's billable value; the agent or user can override per entry via `create_time_log` or `update_time_log`. Use `sum_billable_time_for_period` and `sum_non_billable_time_for_period` for reports. For charts: `groupBy: "day_and_billable"` gives a stacked bar (billable vs non-billable per day); `groupBy: "billable_vs_non_billable"` gives a single pie for the whole period.
- **Activity types:** The assistant can create, update, delete, and list activity types (natures + daily rate in cents). Each type may have an optional `description` (detection hints: synonyms, typical phrases). When logging time, if the user says "dev", "expertise IA", "marketing", etc., the assistant should call `list_activity_types` (which returns descriptions) and pass the matching `activityTypeCode` in `create_time_log`. Entries with an activity type and billable=true display their value in euros on the card verso (TJM 8h: value = duration_minutes/480 × daily_rate_cents/100).
