# Functional Specification

## Purpose

Horain is a **voice-first personal time journal assistant**.

The user captures voice via the mic (waveform during recording, Confirm/Cancel to transcribe); the transcript is inserted into the input and sent manually. The system converts voice to text, detects intent, extracts entities, matches or creates projects, asks for clarification when needed, logs the time entry, and confirms the action conversationally.

## Scope

- **In scope:**
  - Minimal mobile web interface (PWA)
  - Voice input (click mic, record, confirm to transcribe, insert at caret, send manually)
  - Speech-to-text (STT) transcription
  - Agent-based intent detection
  - Project matching (direct, ambiguous, unknown)
  - Clarification questions
  - Project creation on demand
  - Time logging with notes
  - Supabase storage
  - Conversational confirmation messages
- **Out of scope:** (MVP) Reports, multi-user, offline mode beyond basic PWA cache.

## Main Capabilities

1. **Voice input:** User speaks; system transcribes and processes.
2. **Intent detection:** LLM-driven agent infers log time, create project, analytics questions, or needs clarification.
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

### Typo / close match

When the user mentions a project name that does not exist but is similar to an existing project (e.g. typo), the assistant proposes the closest match and asks for confirmation instead of offering to create a new project.

**User:** "I worked 2 hours on Horian."

**Assistant:** "I don't have a project named Horian. Did you mean Horain? Should I log 120 minutes on Horain?"

After the user confirms (e.g. "yes" or "I meant Horain"), the assistant logs the time on the matched project. Only if there is no close match or the user declines does the assistant offer to create a new project.

---

### Missing duration

**User:** "I worked on Meeds all morning."

**Assistant:** "Can you estimate the duration?"

---

### Full flow example

**User:** "I just spent 30 minutes on HatCast working on the player selection algorithm."

**Assistant:** "I recorded 30 minutes on project HatCast. Note: work on the player selection algorithm."

## First Display

When the user opens Horain with an empty conversation, the app displays the 5 most recent time-logged activities (project, duration, note, date). This is fetched directly via API (no agent/LLM call) to give immediate context. If there are no entries or the backend is unavailable, a placeholder with example phrasing is shown instead.

## Boundaries

- **Inputs:** Voice (click mic → record → confirm → insert) or direct text input.
- **Outputs:** Assistant text responses, confirmations, conversation updates. Responses may be delivered progressively (streaming) when the backend supports it.
- **External dependencies:** STT service, LLM (agent), MCP Server (tools), Supabase.

## Assumptions and Uncertainties

- [ASSUMPTION] Single user per instance for MVP.
- [ASSUMPTION] STT and LLM run server-side; API keys never exposed to client.
- [UNCERTAIN] Exact STT provider (Web Speech API vs server-side).
- [UNCERTAIN] MCP Server deployment (co-located with agent vs separate service).
