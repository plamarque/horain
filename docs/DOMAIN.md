# Domain Model

## Purpose

Shared vocabulary and rules for Horain: projects, time logs, intent detection, and clarification flow.

## Key Terms

| Term | Definition |
|------|------------|
| **Project** | An activity or initiative on which the user logs time. Has id, name, optional description. |
| **Time log** | A recorded entry: project_id, duration_minutes, note, timestamp, source. |
| **Intent** | The user's inferred goal from natural language (e.g. log time, create project, needs clarification). |
| **Clarification** | A follow-up question from the assistant (e.g. which project? what duration?). |
| **Transcription** | Text output from speech-to-text (STT) based on user voice input. |
| **Source** | Origin of a time log; `"voice"` for voice-sourced entries. |

## Entities and Relationships

- **Project:** id, name, description, created_at. User-defined; referenced by time_logs.
- **Time log:** id, project_id (FK), duration_minutes, note, created_at, source. Belongs to one project.
- **Relationship:** One project has many time_logs; each time_log belongs to one project.

## Domain Rules

1. A time_log must reference an existing project (project_id FK).
2. source = "voice" for entries created from voice input (MVP).
3. Projects are matched by name (exact or fuzzy search); names should be unique or disambiguated.
4. Duration is in minutes; required for logging (agent prompts if missing).
5. The agent never writes directly to the database; all writes go through MCP tools.

## Assumptions and Uncertainties

- [ASSUMPTION] Project names are case-sensitive for matching; fuzzy match used for variants.
- [UNCERTAIN] Whether to allow duplicate project names with different descriptions.
