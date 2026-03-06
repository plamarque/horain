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

### Input bar

- **Text input** with placeholder "Ask anything" — user can type directly and press Enter.
- **Microphone icon** — click to start recording, click again to stop and send.
- Sync icon (discrete) for manual sync; sync also runs automatically after each message.

### Conversation timeline

- Chronological thread of messages.
- **User message:** Transcription of what the user said.
- **Assistant response:** Text reply, confirmations, clarification questions.
- **Action confirmations:** e.g. "I recorded 30 minutes on HatCast. Note: work on the player selection algorithm."

### Typical flow

1. User either types in the field (Enter to send) or clicks the mic, speaks, then clicks again to send.
2. Voice is sent for transcription.
3. Transcript appears in the conversation.
4. Agent analyzes, optionally calls MCP tools.
5. Assistant response appears.
6. UI updates (new message, confirmation).

## Mobile-first

- Large touch areas (e.g. push-to-talk: min 56×56dp).
- Readable typography; adequate contrast.
- Voice (mic) and text input are both always available in the same bar.
- Optimized for one-handed use on phone (target: Pixel 9a).

## States

- **Idle:** Input bar ready; user can type or click mic.
- **Recording:** User clicked mic; interim transcript shown above bar.
- **Processing:** Transcript sent; waiting for assistant reply (loading indicator).
- **Response:** Assistant message displayed.

## Accessibility

- Input bar must be keyboard and screen-reader accessible.
- Mic button must have clear label (e.g. "Click to speak" / "Click to stop").
- Conversation thread should be announced when updated.
