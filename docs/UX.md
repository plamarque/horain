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
- **Activity period (center):** A compact control shows the current window (default last 28 rolling days). Tap to open presets (7 / 28 days, this month, previous month) or a custom local date range. The same window filters **Projects** (only projects with at least one time log in the window; revenue and counts on cards are for that window only) and the **recent-activity** block on the empty conversation timeline. Modals that need the full project list (e.g. edit entry, edit project from elsewhere) still load all projects without this filter.
- On the right: **Projects** link (opens the Projects view: project cards with a pen icon to edit each project when the card is expanded; **the project description is shown only in that expanded state** (collapsed cards show name and billable revenue for the selected period only); for billable projects, revenue from billable time logs with activity type in that period is displayed in euros, e.g. "2 500 €", instead of a dollar symbol; **Back** in the header returns to the conversation). **Project cards are clickable:** tapping or clicking a card (not the edit icon) adds that project to the conversation context; the user can add several projects. Selected projects remain in chips even if they disappear from the filtered Projects list until the user removes them or sends a message (per product tolerance). The **version** (e.g. v0.2.1 or v0.2.1-SNAPSHOT (sha)) is shown in very small text on the **same line as the seed icon** (below the input bar), without a border; it is clickable (**Refresh app**) to reload the application and pick up the latest PWA update.

### Input bar

- **Text input** with placeholder "Ask anything" — behaviour depends on device (detected via `(hover: hover)`). **Desktop:** Enter sends, Shift+Enter inserts new line; after send, focus returns to the input. **Mobile:** Enter inserts new line only; sending is via the Send button; after send, input is blurred so the on-screen keyboard closes.
- **Send button** (outline arrow up icon) — appears when text is entered; click to send. Same style and dimensions as the mic button. Positioned to the right of the mic (rightmost) for easy one-handed tap.
- **Stop button** — replaces Send during processing; click to cancel the in-flight request. Restores focus to the input field. No data is modified on cancel.
- **Microphone icon** — click to start recording. During recording, the input area is replaced by a waveform (no real-time transcript). Two buttons appear: **Cancel** (X) and **Confirm** (checkmark). Cancel aborts and returns to input. Confirm triggers transcription (with brief “Transcribing…” indicator if needed); the transcript is inserted at the caret position in the input (appending, not replacing existing text). User edits if desired and sends via the Send button.
- Displayed data (e.g. recent activities) is refetched from the server after each message and after editing or deleting an entry.
- **Pull to refresh:** On the conversation timeline and on the Projects view, the user can pull down from the top of the scroll area to refresh data (recent activities or project list). A short “Pull to refresh” / “Release to refresh” hint and a “Refreshing…” spinner give feedback. Touch-only on mobile; no full-page reload.

### Conversation timeline

- Chronological thread of messages.
- **Initial state:** On first load, when the thread is empty, the app fetches logged activities in the **header-selected activity period** (newest first, capped by API) via API (no LLM call) and displays them in the same flip-card format as conversation entries. The block title reflects the current period. If no entries exist in that period or the API fails, a placeholder with example phrasing is shown instead.
- **Scroll behavior:** When a new assistant message arrives, if the user was at the bottom of the thread, auto-scroll to the new message. If the user had scrolled up (e.g. reading earlier content while the agent was responding), do not auto-scroll; show a floating "New message" indicator that lets them jump to the latest response.
- **Gap:** Padding at the bottom of the timeline so content does not appear hidden below the input area.
- **User message:** Transcription of what the user said.
- **Assistant response:** Text reply, confirmations, clarification questions. Assistant messages show the Horain icon (triskelion) next to a violet-toned bubble so it is clear who is speaking. Responses can **stream** (text appears progressively as the agent generates it); a blinking cursor indicates streaming. If the backend does not support streaming, the full message appears at once. **Feedback (thumb up / thumb down):** Below each assistant message, two buttons allow the user to rate the response. Feedback is collected to improve the assistant and to feed the eval pipeline (see EVALS.md); it is stored in the backend and can be exported for triage and promotion into Promptfoo tests.
- **Agent trace (reasoning visibility):** Just below each assistant message bubble (before any chart or activity cards), a small **trace** block shows what the agent did, **activity-first (Cursor-style)**. During the response it shows a minimal “Thinking...” indicator until the first tool call. The trace has **no global “Outils utilisés” wrapper**: it shows in order (1) **Thought** when the model exposes reasoning: **during** the reasoning stream the block shows “Thinking...” and stays **open** with scrollable text; **when** reasoning is done it becomes “Thought for Xs” and **collapses** (one-line summary in white below; click to expand full reasoning), (2) **turns** (Tour 1, Tour 2…) each with a **flat list** of tool calls (no “Lecture · X outils” / “Écriture · X outils” sections). Each turn shows “N appels”; when expanded, each line is the natural-language description (e.g. “Chargement des temps facturables mois de Janvier 2026”); click to expand params/result/status. **Model name** is displayed at the top of the trace when known (event `model` during stream or `modelName` in done payload). The **activity name** (Thought, Exploration, Lecture, Écriture, Suppression, Autre) is in a distinct color; the **qualifier** (e.g. “ for 11s”, “ · 2 outils”) is in muted grey. **Chevron (expand/collapse)** is visible **on hover** for Thought and for each activity block. When an activity block is expanded, only **natural-language lines** are shown (one per tool call, e.g. “Chargement des temps pour graphique agrégé”); **clicking a line** expands that call to show params, result, and status (OK/Erreur). The trace is not framed, uses attenuated colors, and the expanded detail scrolls within ~14rem. Only the current turn stays expanded during streaming; when the stream ends, the last turn stays expanded. The trace is **session-only** (not persisted). When a turn has only one tool call, the description is shown directly (no "1 appel" header). Reasoning: stream sends `reasoning_chunk`, `done` may include `reasoningText`, `reasoningDurationMs`, `reasoningSummary`; the summary is shown in white below the Thought block (backend or derived from first sentence / ~120 chars).

  **Référence UX type Cursor (séquences à répliquer) :**

  | Phase | Cursor (exemple) | Horain (équivalent) |
  |-------|------------------|------------------------|
  | Pendant la réflexion | “Thinking...”, “Planning next moves” | “Thinking...” tant qu’aucun tool call n’est reçu |
  | Activités affichées | “Thought for 11s”, “Explored 2 files” (activité + qualifier, chevron au survol) | “Thought for Xs”, “Exploration · 2 outils”, “Lecture · 3 outils” (libellé d’activité en couleur, qualificateur en gris, chevron au survol) |
  | Tour en cours (outils en direct) | Tool calls en langage pseudo-naturel (e.g. “Read X L1-100”) | Tool calls streamés, blocs par type (Exploration, Lecture, …) ; section ouverte = lignes en langage naturel uniquement |
  | Détail d’un appel | Clic sur une ligne → params et statut de l’appel | Clic sur une ligne de description → arguments, résultat, statut OK/Erreur |
  | Fin du tour / tour suivant | Bloc précédent se replie, en-tête reste visible | Tour précédent replié (résumé visible) ; tour courant ouvert |
  | Phases / types de tâche | “Exploring”, “Read”, etc. | Exploration, Lecture, Écriture, Suppression, Autre (voir agentTraceTaskTypes) |

  Autres patterns Cursor utiles (doc / blog) : **Debug Mode** et **Plan Mode** ; pour la trace in-chat, on s’aligne sur les séquences ci-dessus (activités d’abord, chevron au survol, double niveau d’expansion pour les outils).

  **Quand le bloc « Thought » apparaît :** Il n’est affiché que si le **modèle** expose du raisonnement (API Responses avec modèles type o1, o4-mini, gpt-5, etc.). Les modèles sans raisonnement (ex. gpt-4o-mini via Chat Completions) ne renvoient pas de texte de raisonnement, donc **pas de Thought** : c’est normal. Le backend route par complexité (voir `ComplexityClassifier`) : selon la requête, il choisit un modèle simple (sans raisonnement) ou complexe/très complexe (avec raisonnement). Pour voir Thought, il faut une requête classée complexe ou très complexe (ex. question ouverte, demande de graphique, « combien d’heures par projet »). Vérifier : logs backend au moment de l’appel (modèle utilisé), ou configuration `llm.multi-model` et modèles simple vs complex.

- **Action confirmations:** e.g. "I recorded 30 minutes on HatCast. Note: work on the player selection algorithm." When the user creates or updates a time entry, the message is followed by the same flip-card list as when listing entries, so the user can verify that the action was correctly captured and can select, edit or delete it if needed. Double-click on a card opens the entry edit screen (full-page; Save, Cancel, Delete with confirmation).
- **Activity cards layout:** Log entries are shown as **flip cards** below the message bubble (mobile-friendly). **Recto:** date, duration, an activity type tag (nature) when the entry has one (e.g. "DEV", "Développement"), and the start of the note (up to 2–3 lines with ellipsis), with a background colour derived from the project. **Verso:** project name, euro symbol (€) if the entry is billable; when the entry has an activity type (nature) with a daily rate, the computed amount in euros (e.g. "100 €") is shown next to the €. Then the note. Tap/click flips the card and adds the entry to context when applicable. **Right-click** (desktop) or **long-press** (mobile) on a card opens a context menu with "Edit entry" and "Edit project". **Double-click on the project name** (on the verso) opens the **project** edit modal (name, description, Facturable). **Double-click on the card** opens the **entry edit screen** (full-page: Project, Nature d'activité, Duration, Note, Facturable, Date). Activity types (natures and daily rates) are managed via the conversation with the assistant (e.g. "add a nature CONSULT at 800 €/day", "list my natures"). A “+N more” button expands the list when there are many entries.
- **Entry edit screen:** Editing a time log opens a **full-page screen** (not a modal): header with Back button and "Edit entry" title, then a scrollable form (Project, Nature d'activité, Duration, Note, Facturable, Date, Save / Cancel / Delete) so all fields fit comfortably.
- **Chart layout:** Charts (pie, bar, etc.) are displayed full-width below the message bubble, not inside it, for a larger and clearer visualization. Charts can show billable vs non-billable time (e.g. pie "Facturé / Non facturé") when the user asks for that breakdown.

### Typical flow

1. User types in the field and sends via Enter (desktop) or the Send button (both); on mobile, Enter adds a new line only. Or clicks the mic, speaks, confirms (checkmark), waits for transcription, edits the inserted text if desired, then sends via the Send button.
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
