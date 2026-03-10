# Domain Model

## Purpose

Shared vocabulary and rules for Horain: projects, time logs, intent detection, and clarification flow.

## Key Terms

| Term | Definition |
|------|------------|
| **Project** | An activity or initiative on which the user logs time. Has id, name, optional description, and billable (whether time is billable by default). |
| **Time log** | A recorded entry: project_id, duration_minutes, note, billable, timestamp, source. |
| **Billable** | Whether time is invoicable / facturable. Projects have a default; each time log inherits it at creation but can be overridden per entry for reporting (billable vs non-billable time). |
| **Intent** | The user's inferred goal from natural language (e.g. log time, create project, needs clarification). |
| **Clarification** | A follow-up question from the assistant (e.g. which project? what duration?). |
| **Transcription** | Text output from speech-to-text (STT) based on user voice input. |
| **Source** | Origin of a time log; `"voice"` for voice-sourced entries. |

## Entities and Relationships

- **Project:** id, name, description, billable, created_at. User-defined; referenced by time_logs.
- **Time log:** id, project_id (FK), duration_minutes, note, billable, created_at, source. Belongs to one project. Billable is set from the project at creation and can be overridden per entry.
- **Relationship:** One project has many time_logs; each time_log belongs to one project.

## Domain Rules

1. A time_log must reference an existing project (project_id FK).
2. When creating a time_log, billable defaults to the project's billable; the user or agent can override it per entry.
3. source = "voice" for entries created from voice input (MVP).
4. Project names are unique; at most one project per name. The database enforces this with a UNIQUE constraint.
5. Duration is in minutes; required for logging (agent prompts if missing).
6. The agent never writes directly to the database; all writes go through MCP tools.

## Assumptions and Uncertainties

- [ASSUMPTION] Project names are case-sensitive for matching; fuzzy match used for variants.
