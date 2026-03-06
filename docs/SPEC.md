# Functional Specification

## Purpose

Horain is a **voice-first personal time journal assistant**.

The user presses a **push-to-talk** button and speaks naturally. The system converts voice to text, detects intent, extracts entities, matches or creates projects, asks for clarification when needed, logs the time entry, and confirms the action conversationally.

## Scope

- **In scope:**
  - Minimal mobile web interface (PWA)
  - Push-to-talk voice interaction
  - Speech-to-text (STT) transcription
  - Agent-based intent detection
  - Project matching (direct, ambiguous, unknown)
  - Clarification questions
  - Project creation on demand
  - Time logging with notes
  - Supabase storage
  - Conversational confirmation messages
- **Out of scope:** (MVP) Reports, editing logs, multi-user, offline mode beyond basic PWA cache.

## Main Capabilities

1. **Voice input:** User speaks; system transcribes and processes.
2. **Intent detection:** Agent infers log time, create project, or needs clarification.
3. **Project matching:** Direct match, disambiguation when ambiguous, creation prompt when unknown.
4. **Clarification:** Assistant asks follow-up questions (e.g. duration, project choice).
5. **Time logging:** Structured entry (project_id, duration_minutes, note, timestamp).
6. **Confirmations:** Assistant responds conversationally (e.g. "Got it. 20 minutes logged for Chrono EPS.").

## Expected Behaviors

### Direct project recognition

**User:** "20 minutes on Chrono EPS."

**Assistant:** "Got it. 20 minutes logged for Chrono EPS."

---

### Ambiguous project name

**User:** "30 minutes on HatCast."

**Assistant:** "I found two similar projects: HatCast V1 and HatCast V2. Which one?"

---

### Unknown project

**User:** "40 minutes on Weather Station."

**Assistant:** "I don't know a project named Weather Station yet. Should I create it and log 40 minutes?"

---

### Missing duration

**User:** "I worked on Meeds all morning."

**Assistant:** "Can you estimate the duration?"

---

### Full flow example

**User:** "I just spent 30 minutes on HatCast working on the player selection algorithm."

**Assistant:** "I recorded 30 minutes on project HatCast. Note: work on the player selection algorithm."

## Boundaries

- **Inputs:** Voice (push-to-talk), optional text input.
- **Outputs:** Assistant text responses, confirmations, conversation updates.
- **External dependencies:** STT service, LLM (agent), MCP Server (tools), Supabase.

## Assumptions and Uncertainties

- [ASSUMPTION] Single user per instance for MVP.
- [ASSUMPTION] STT and LLM run server-side; API keys never exposed to client.
- [UNCERTAIN] Exact STT provider (Web Speech API vs server-side).
- [UNCERTAIN] MCP Server deployment (co-located with agent vs separate service).
