# User Experience

## Purpose

Defines the UX principles and UI structure for Horain. Voice-first, conversation over forms, minimal interface.

## Design Principles

- **Voice-first:** Primary input is voice via microphone (click-to-talk) or text (direct typing).
- **Conversation over forms:** No traditional time tracking form; everything flows through dialogue.
- **Clarify when uncertain:** Assistant asks follow-up questions when intent or entities are ambiguous.
- **Minimal UI surface:** Focus on the conversation; avoid clutter.
- **Natural language in → structured storage:** User speaks freely; system extracts and stores structure.

## Main Components

### Header

- App name "Horain" and tagline on the left.
- **Version** (e.g. v0.2.1 or v0.2.1-SNAPSHOT (sha)) on the right. The version label is a clickable control: **Refresh app** — tap or click to reload the application and pick up the latest PWA update.

### Input bar

- **Text input** with placeholder "Ask anything" — user types directly. Enter inserts a new line (multi-line input); sending is only via the Send button (avoids accidental send on mobile when the keyboard Enter is pressed).
- **Send button** (outline arrow up icon) — appears when text is entered; click to send. Same style and dimensions as the mic button. Positioned to the right of the mic (rightmost) for easy one-handed tap.
- **Stop button** — replaces Send during processing; click to cancel the in-flight request. Restores focus to the input field. No data is modified on cancel.
- **Microphone icon** — click to start recording. During recording, the input area is replaced by a waveform (no real-time transcript). Two buttons appear: **Cancel** (X) and **Confirm** (checkmark). Cancel aborts and returns to input. Confirm triggers transcription (with brief “Transcribing…” indicator if needed); the transcript is inserted at the caret position in the input (appending, not replacing existing text). User edits if desired and sends via the Send button.
- Displayed data (e.g. recent activities) is refetched from the server after each message and after editing or deleting an entry.

### Conversation timeline

- Chronological thread of messages.
- **Initial state:** On first load, when the thread is empty, the app fetches the 5 most recent logged activities via API (no LLM call) and displays them in the same table format as conversation entries. This gives the user immediate context of where they left off. If no entries exist or the API fails, a placeholder with example phrasing is shown instead.
- **Scroll behavior:** When a new assistant message arrives, if the user was at the bottom of the thread, auto-scroll to the new message. If the user had scrolled up (e.g. reading earlier content while the agent was responding), do not auto-scroll; show a floating "New message" indicator that lets them jump to the latest response.
- **Gap:** Padding at the bottom of the timeline so content does not appear hidden below the input area.
- **User message:** Transcription of what the user said.
- **Assistant response:** Text reply, confirmations, clarification questions. Assistant messages show the Horain icon (triskelion) next to a violet-toned bubble so it is clear who is speaking. Responses can **stream** (text appears progressively as the agent generates it); a blinking cursor indicates streaming. If the backend does not support streaming, the full message appears at once. **Feedback (thumb up / thumb down):** Below each assistant message, two buttons allow the user to rate the response. Feedback is collected to improve the assistant and to feed the eval pipeline (see EVALS.md); it is stored in the backend and can be exported for triage and promotion into Promptfoo tests.
- **Action confirmations:** e.g. "I recorded 30 minutes on HatCast. Note: work on the player selection algorithm." When the user creates or updates a time entry, the message is followed by the structured entry table (same format as when listing entries), so the user can verify that the action was correctly captured and can select, edit or delete it if needed. Double-click on a row opens the edit modal (Save, Cancel, Delete with confirmation).
- **Activity table layout:** The entry table (Date, Project, Duration, Facturable, Note) is displayed full-width below the message bubble, not inside it, so notes wrap on multiple lines without truncation or horizontal scroll. The "Facturable" column shows Oui/Non per entry. **Double-click on the project name** (Projet cell) opens the **project** edit modal (name, description, Facturable). Double-click on the row (or any other cell) opens the **entry** edit modal (duration, note, Facturable, date).
- **Chart layout:** Charts (pie, bar, etc.) are displayed full-width below the message bubble, not inside it, for a larger and clearer visualization. Charts can show billable vs non-billable time (e.g. pie "Facturé / Non facturé") when the user asks for that breakdown.

### Typical flow

1. User types in the field and sends via the Send button (Enter adds a new line); or clicks the mic, speaks, confirms (checkmark), waits for transcription, edits the inserted text if desired, then sends via the Send button.
2. Voice is transcribed; the transcript is inserted into the input at the caret position.
3. Transcript (possibly edited) appears in the conversation when the user sends.
4. Agent analyzes, optionally calls MCP tools.
5. Assistant response appears.
6. UI updates (new message, confirmation).

## Mobile-first

- Large touch areas (e.g. push-to-talk: min 56×56dp).
- Readable typography; adequate contrast.
- Voice (mic) and text input are both always available in the same bar.
- Optimized for one-handed use on phone (target: Pixel 9a).

## States

- **Idle:** Input bar ready; user can type or click mic. Send button appears when text is entered.
- **Recording:** User clicked mic; input area replaced by waveform and Cancel/Confirm buttons. No real-time transcript.
- **Transcribing:** User confirmed recording; brief “Transcribing…” indicator and Cancel button while STT finalizes. Cancel aborts transcription and returns to Idle.
- **Processing:** Transcript sent; waiting for assistant reply. Send becomes Stop; user can cancel the request. A "thinking" indicator (three animated dots, violet) is shown while the assistant is working. When streaming is supported, the assistant bubble appears and fills progressively, replacing the thinking indicator.
- **Response:** Assistant message displayed (possibly after streaming).

## Accessibility

- Input bar must be keyboard and screen-reader accessible.
- Send and Stop buttons must have clear labels (e.g. "Send", "Stop").
- Mic button must have clear label (e.g. "Click to speak"). Cancel and Confirm buttons must have clear labels (e.g. "Cancel", "Confirm").
- Conversation thread should be announced when updated.
