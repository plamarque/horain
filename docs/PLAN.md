# Plan

## Current phase

Slice 5 clôturée. Prochaine phase au choix (slice 6 optionnelle, ou différé ISSUES.md).

## État actuel (codebase)

Le projet a progressé au-delà des statuts précédemment indiqués. Vue 3 + Vite, conversation UI, push-to-talk, agent backend avec outils intégrés, PWA et tests e2e sont en place. Voir le détail des slices et tâches ci-dessous.

**Écarts documentés :**
- **PrimeVue :** ARCH/PLAN indiquaient PrimeVue ; l’implémentation utilise une UI custom (Vue 3, CSS). Décision : garder l’UI custom (ARCH mis à jour).
- **MCP :** ARCH décrit un « MCP Server » ; les outils sont intégrés au backend (sémantique MCP_TOOLS). Voir ARCH.md section « MCP : outils intégrés ».

## Slices

| Slice | Objective | Status |
|-------|-----------|--------|
| 0 | Documentation governance | Done |
| 1 | Bootstrap front (Vue 3, Vite) + minimal conversation UI | Done |
| 2 | Tools + Supabase (integrated in backend) | Done |
| 3 | Backend agent + tool calling | Done |
| 4 | Voice (push-to-talk, STT) | Done |
| 5 | Full flow + e2e tests + CI/CD | Done |
| 6 | PWA + stores (optional) | PWA Done, stores optional |
| 7 | Trace par types de tâches (Phase 1) — sections repliables, accordéon | Done |

## Tasks (Slice 1)

- [x] Create Vite + Vue 3 project
- [x] Implement minimal conversation UI (thread, messages)
- [x] Push-to-talk button (Web Speech API)
- [x] Layout and styling (mobile-first)
- [x] Display 8 recent activities on launch (API direct, no LLM)

*Note: PrimeVue non utilisé ; UI custom conforme à UX.md.*

## Tasks (Slice 2)

- [x] Tools implementation (ToolRegistry, ToolExecutorService)
- [x] Supabase setup (projects, time_logs via JDBC)
- [x] list_projects, search_project, create_project, log_time, get_recent_logs, etc.

*Note: Outils intégrés au backend ; pas de serveur MCP externe.*

## Tasks (Slice 3)

- [x] Backend agent runtime (Spring AI)
- [x] Tool integration (Spring AI tool calling)
- [x] Agent orchestration and tool calling

## Tasks (Slice 4)

- [x] Speech-to-text integration (Web Speech API)
- [x] Push-to-talk wiring
- [x] Transcript → agent flow
- [ ] Waveform réaliste (différé, voir ISSUES.md)

## Tasks (Slice 5)

- [x] End-to-end flow: voice → transcript → agent → tools → confirmation
- [x] Playwright e2e tests
- [x] CI: run backend tests + e2e before deploy (deploy.yml); releases (tag v*) run same tests before creating GitHub release (release.yml)
- [x] GitHub Actions deploy (frontend → Pages, backend → Cloud Run)

## Tasks (Slice 6)

- [x] PWA manifest and service worker
- [ ] Store packaging (optional, see PUBLISHING_STORES.md)

## Slices (trace / reasoning)

| Slice | Objective | Status |
|-------|-----------|--------|
| **Phase 1** | Trace par types de tâches (Exploration, Lecture, Écriture, etc.), sections repliables avec résumé succinct / détail, accordéon | Done |
| **A** | Backend : support du raisonnement (optionnel selon modèle) — events `reasoning_chunk`, API Responses ou champ reasoning dans le stream | Done |
| **B** | Frontend : section Thinking repliable (résumé / détail), réception des `reasoning_chunk` | Done |
| **C** | Multi-modèle et détection d'intention (routage vers modèle approprié) | Différé |

Backend : `StreamEventWriter.sendReasoningChunk`, `sendDone(…, reasoningText, reasoningDurationMs)` ; client `OpenAiResponsesLlmClient` (Responses API) ; choix via `llm.client=openai-responses`. Frontend : event `reasoning_chunk`, payload `done` avec `reasoningText` / `reasoningDurationMs`, bloc « Thought for Xs » dans AgentTraceBlock. Voir [docs/ISSUES.md](docs/ISSUES.md) pour le reasoning interne.

## Différé (post-MVP)

Voir [docs/ISSUES.md](ISSUES.md) :

- Streaming des réponses agent (SSE / WebSockets)
- Reasoning interne (o1, o3)
- STT : migration Whisper
- Waveform réaliste
