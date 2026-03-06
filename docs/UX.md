# User Experience

## Purpose

Defines the UX principles and UI structure for Horain. Voice-first, conversation over forms, minimal interface.

## Design Principles

- **Voice-first:** Primary input is voice via push-to-talk.
- **Conversation over forms:** No traditional time tracking form; everything flows through dialogue.
- **Clarify when uncertain:** Assistant asks follow-up questions when intent or entities are ambiguous.
- **Minimal UI surface:** Focus on the conversation; avoid clutter.
- **Natural language in → structured storage:** User speaks freely; system extracts and stores structure.

## Main Components

### Home screen

- Central **push-to-talk** button.
- Large touch target for mobile.
- Clear visual state: idle, recording, processing.

### Conversation timeline

- Chronological thread of messages.
- **User message:** Transcription of what the user said.
- **Assistant response:** Text reply, confirmations, clarification questions.
- **Action confirmations:** e.g. "I recorded 30 minutes on HatCast. Note: work on the player selection algorithm."

### Typical flow

1. User taps push-to-talk and speaks.
2. User releases; voice is sent for transcription.
3. Transcript appears in the conversation.
4. Agent analyzes, optionally calls MCP tools.
5. Assistant response appears.
6. UI updates (new message, confirmation).

## Mobile-first

- Large touch areas (e.g. push-to-talk: min 56×56dp).
- Readable typography; adequate contrast.
- Voice is the primary path; text input secondary or fallback.
- Optimized for one-handed use on phone (target: Pixel 9a).

## States

- **Idle:** Push-to-talk ready.
- **Recording:** User holding button; visual feedback (e.g. waveform, pulse).
- **Processing:** Transcript sent; waiting for assistant reply (loading indicator).
- **Response:** Assistant message displayed.

## Accessibility

- Push-to-talk must be keyboard and screen-reader accessible.
- Conversation thread should be announced when updated.
- Fallback: text input when voice is unavailable.
