# Data Model

## Purpose

Defines the database schema for Horain. Supabase (PostgreSQL) stores projects and time logs.

## Table: projects

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, default gen_random_uuid() |
| name | VARCHAR(255) | NOT NULL, UNIQUE |
| description | VARCHAR(2000) | nullable |
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
| logged_at | TIMESTAMPTZ | NOT NULL, default now() |
| created_at | TIMESTAMPTZ | NOT NULL, default now() |
| updated_at | TIMESTAMPTZ | NOT NULL, default now() |
| user_id | VARCHAR(255) | nullable |

## Relationships

- **projects** ↔ **time_logs**: One-to-many. One project has many time_logs; each time_log belongs to one project.
- `project_id` in time_logs is a foreign key to projects.id.

## Indexes

- `time_logs(project_id)` — for lookups by project
- `time_logs(logged_at DESC)` — for list_recent_logs
- `projects(name)` — for search_project; UNIQUE constraint enforces one project per name.

## Notes

- **logged_at** (activity date): The date the activity refers to. Can be overridden when logging past activity (e.g. via `loggedAt` parameter). Displayed in the UI, used for period queries and charts.
- **created_at** (entry date): When the user created the record. Used for sorting and search ("when did I enter this?"). Not displayed in the log table.
- `user_id` supports future multi-tenant isolation (Supabase RLS).
