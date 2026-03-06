# Data Model

## Purpose

Defines the database schema for Horain. Supabase (PostgreSQL) stores projects and time logs.

## Table: projects

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, default gen_random_uuid() |
| name | TEXT | NOT NULL |
| description | TEXT | nullable |
| created_at | TIMESTAMPTZ | default now() |

## Table: time_logs

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, default gen_random_uuid() |
| project_id | UUID | NOT NULL, REFERENCES projects(id) ON DELETE CASCADE |
| duration_minutes | INTEGER | NOT NULL, CHECK (duration_minutes > 0) |
| note | TEXT | nullable |
| created_at | TIMESTAMPTZ | default now() |
| source | TEXT | default 'voice' |

## Relationships

- **projects** ↔ **time_logs**: One-to-many. One project has many time_logs; each time_log belongs to one project.
- `project_id` in time_logs is a foreign key to projects.id.

## Indexes (recommended)

- `time_logs(project_id)` — for lookups by project
- `time_logs(created_at DESC)` — for list_recent_logs
- `projects(name)` — for search_project (consider unique or GIN for fuzzy)

## Notes

- `source` identifies the origin of a time log; `"voice"` for voice-sourced entries (MVP).
- `created_at` in time_logs can be overridden when logging past activity (timestamp parameter in log_time).
