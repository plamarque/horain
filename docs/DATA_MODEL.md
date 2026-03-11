# Data Model

## Purpose

Defines the database schema for Horain. Supabase (PostgreSQL) stores projects and time logs.

## Table: projects

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, default gen_random_uuid() |
| name | VARCHAR(255) | NOT NULL, UNIQUE |
| description | VARCHAR(2000) | nullable |
| billable | BOOLEAN | NOT NULL, default true |
| created_at | TIMESTAMPTZ | NOT NULL, default now() |
| updated_at | TIMESTAMPTZ | NOT NULL, default now() |
| user_id | VARCHAR(255) | nullable |

## Table: time_logs

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, default gen_random_uuid() |
| project_id | UUID | NOT NULL, REFERENCES projects(id) ON DELETE RESTRICT |
| duration_minutes | INTEGER | NOT NULL, CHECK (duration_minutes > 0) |
| note | VARCHAR(2000) | nullable |
| billable | BOOLEAN | NOT NULL (set from project at creation; can be overridden per entry) |
| logged_at | TIMESTAMPTZ | NOT NULL, default now() |
| created_at | TIMESTAMPTZ | NOT NULL, default now() |
| updated_at | TIMESTAMPTZ | NOT NULL, default now() |
| user_id | VARCHAR(255) | nullable |

## Table: agent_turn

Trace of one conversation turn (user message + assistant response and metadata). Used for the eval pipeline (extraction, triage, promotion to Promptfoo).

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY |
| conversation_id | UUID | NOT NULL |
| turn_index | INTEGER | NOT NULL |
| user_message | TEXT | NOT NULL |
| assistant_message | TEXT | nullable |
| tool_calls_json | TEXT | nullable |
| tool_results_json | TEXT | nullable |
| ui_payload_json | TEXT | nullable |
| system_prompt_version | VARCHAR(50) | nullable |
| model | VARCHAR(255) | nullable |
| status | VARCHAR(50) | nullable (e.g. success, tool_error, empty_result, max_iterations) |
| history_snapshot_json | TEXT | nullable |
| context_entries_json | TEXT | nullable |
| latency_ms | BIGINT | nullable |
| created_at | TIMESTAMPTZ | NOT NULL |

Indexes: `(conversation_id, turn_index)`, `(created_at DESC)`.

## Table: agent_feedback

User feedback (thumb up/down) on a single agent turn. One feedback per turn (unique on turn_id).

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY |
| turn_id | UUID | NOT NULL, REFERENCES agent_turn(id) ON DELETE CASCADE, UNIQUE |
| rating | VARCHAR(20) | NOT NULL ('up' \| 'down') |
| reason_code | VARCHAR(100) | nullable |
| comment | TEXT | nullable |
| created_at | TIMESTAMPTZ | NOT NULL |

## Table: eval_backlog

Eval candidates derived from turns (and optionally feedback). Triage status and metadata for promotion to Promptfoo tests.

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY |
| turn_id | UUID | NOT NULL, REFERENCES agent_turn(id) ON DELETE CASCADE |
| eval_family | VARCHAR(100) | nullable |
| expected_behavior | TEXT | nullable |
| assertion_type | VARCHAR(50) | nullable |
| severity | VARCHAR(20) | nullable |
| status | VARCHAR(50) | nullable (new, triaged, converted, ignored) |
| notes | TEXT | nullable |
| promoted_at | TIMESTAMPTZ | NOT NULL |

Indexes: `(status)`, `(turn_id)`.

## Relationships

- **projects** ↔ **time_logs**: One-to-many. One project has many time_logs; each time_log belongs to one project.
- `project_id` in time_logs is a foreign key to projects.id.
- **agent_turn** ↔ **agent_feedback**: One-to-one. Each turn can have at most one feedback row.
- **agent_turn** ↔ **eval_backlog**: One-to-many. A turn can have multiple eval_backlog entries (e.g. different triage outcomes).

## Indexes

- `time_logs(project_id)` — for lookups by project
- `time_logs(logged_at DESC)` — for list_recent_logs
- `projects(name)` — for search_project; UNIQUE constraint enforces one project per name.

## Notes

- **billable** (projects): Whether time logged on this project is billable by default. New time entries inherit this value but can be overridden per entry.
- **billable** (time_logs): Whether this entry is billable. Set from the project at creation; can be toggled per entry for reporting (billable vs non-billable time).
- **logged_at** (activity date): The date the activity refers to. Can be overridden when logging past activity (e.g. via `loggedAt` parameter). Displayed in the UI, used for period queries and charts.
- **created_at** (entry date): When the user created the record. Used for sorting and search ("when did I enter this?"). Not displayed in the log table.
- `user_id` supports future multi-tenant isolation (Supabase RLS).
