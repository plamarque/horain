# Roadmap

## Purpose

This document describes the longer-term vision for Horain. Unlike [PLAN.md](PLAN.md), which tracks current slices and tasks, the roadmap focuses on phases beyond MVP and learning goals.

## MVP

The first version includes (see [SPEC.md](SPEC.md)):

- Mobile web interface
- Push-to-talk interaction
- Voice transcription
- Agent-based intent detection
- Project matching (direct, ambiguous, unknown)
- Clarification questions
- Project creation
- Time logging
- Supabase storage
- Conversational confirmations

## Post-MVP

| Phase | Ideas |
|-------|-------|
| **Editing** | Edit or delete time logs; correct mistaken entries |
| **Reports** | Time summaries by project, week, month; export |
| **Multi-user** | Auth (e.g. Supabase Auth); per-user projects and logs |
| **Offline** | Better PWA offline support; queue logs when offline |
| **Integrations** | Export to calendar, invoicing, or external tools |

## AI Playground

Horain also serves as a **learning playground for AI product patterns**. Areas to explore:

- **Intent detection:** Inferring user goal from natural language (log time, create project, clarify)
- **Entity extraction:** Project name, duration, note from free-form text
- **Conversational clarification:** When to ask vs. when to assume
- **Agent orchestration:** Multi-step flows (search → create → log)
- **MCP tool usage:** Structured tool calling, validation, error handling
- **Structured data extraction:** Natural language → projects, time_logs
- **Conversational UI design:** Minimal prompts, confirmation patterns, error recovery

## Priorities

- MVP first: get voice → log → confirm working end-to-end.
- Then iterate on clarification, matching, and edge cases.
- Post-MVP features based on user feedback and learning goals.
