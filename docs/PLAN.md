# Plan

## Current phase

Slice 5 clôturée. Prochaine phase au choix (slice 6 optionnelle, ou différé ISSUES.md).

**Observabilité agent (slices O1–O6) :** tranches optionnelles dans la section *Slices (observabilité agent — optionnel)* ci-dessous ; statut global **Done** dans le codebase actuel.

## État actuel (codebase)

- **Agent :** injection du contexte temporel serveur en fin de system prompt (réduction des appels `get_current_datetime` dans le chat in-app) — voir `ServerTemporalContextService`, [docs/MCP_TOOLS.md](MCP_TOOLS.md), [docs/AGENT_DESIGN.md](AGENT_DESIGN.md).

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
- [x] CI: run backend tests + e2e before deploy (deploy.yml); e2e skipped when push only changes version manifests; releases (tag v*) create GitHub release via release.yml (tests on same commit via deploy on main)
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

## Slices (observabilité agent — optionnel)

Tranches de livraison **distinctes** des slices produit ci-dessus. Objectif : export optionnel vers des plateformes externes (LangSmith, OTLP) **sans** remplacer la trace native (`agent_turn` / `agent_feedback`). Les **slices O1–O6** ci-dessous correspondent aux **tranches de livraison 1 à 6** (même découpage, libellés courts dans le tableau récapitulatif). Détail comportementnel : [ARCH.md](ARCH.md), variables d’environnement : [ENV_SETUP.md](ENV_SETUP.md), décision : [ADR/ADR-optional-external-agent-observability.md](ADR/ADR-optional-external-agent-observability.md).

**Configuration typée :** le préfixe Spring est **`horain.observability`** (pas `horain.tracing`). `provider` : `none` | `langsmith` (Langfuse réservé pour une implémentation ultérieure). Clés et endpoints via variables d’environnement — voir [ENV_SETUP.md](ENV_SETUP.md). La politique « DB toujours remplie + export externe optionnel » est la propriété **`keep-native-trace`** (équivalent intentionnel d’un mode dual-write explicite : la base reste la source de vérité).

### Correspondance tranches de livraison ↔ slices O

| Tranche | Slice | Contenu (résumé) |
|---------|-------|------------------|
| 1 | **O1** | Abstractions : `AgentTraceSink`, `NoOpAgentTraceSink`, branchement après persistance (`LlmChatService.persistTurn` après `AgentTurnService.saveTurn` ; `AgentFeedbackService` après sauvegarde feedback) |
| 2 | **O2** | LangSmith : client HTTP, runs async, `external_trace_id`, thread / session |
| 3 | **O3** | Feedback utilisateur → LangSmith ; file d’attente si run id externe inconnu |
| 4 | **O4** | `keep-native-trace` + métriques `horain.observability.export` |
| 5 | **O5** | Script eval → dataset LangSmith (hors hot path) ; option endpoint admin différée |
| 6 | **O6** | OTLP / Micrometer, span `horain.agent.turn`, `OTEL_*` |

| Slice | Objective | Status |
|-------|-----------|--------|
| **O1** | Propriétés `horain.observability`, interface `AgentTraceSink` + `NoOpAgentTraceSink`, branchement après persistance DB (`LlmChatService`, feedback) | Done |
| **O2** | Client HTTP LangSmith (`POST /runs`), export async des tours, migration `agent_turn.external_trace_id`, résolution projet tracing via `GET /api/v1/sessions`, fil de discussion LangSmith (`conversationId` API + client) | Done |
| **O3** | Feedback utilisateur → `POST /feedback` LangSmith, file d’attente tant que le run id externe n’est pas connu | Done |
| **O4** | Politique dual-write explicite (`keep-native-trace`), métriques Micrometer `horain.observability.export` | Done |
| **O5** | Pont hors hot path : export eval candidates vers dataset LangSmith (script, voir [DEVELOPMENT.md](DEVELOPMENT.md)) | Done |
| **O6** | OTLP : Micrometer tracing bridge, span `horain.agent.turn`, variables `OTEL_*` et sampling documentés dans [ARCH.md](ARCH.md) / [ENV_SETUP.md](ENV_SETUP.md) | Done |

### Vérification opérationnelle (staging / prod)

Checklist pour valider chaque brique après déploiement ou avant passage en prod (pas automatisée dans le repo ; à exécuter manuellement sur l’environnement cible).

- **O1 —** Déployer avec `HORAIN_OBSERVABILITY_PROVIDER` absent ou `none` : comportement identique à avant observabilité ; aucune requête sortante nouvelle vers LangSmith (logs / absence de trafic réseau vers l’API LangSmith).
- **O2 —** Activer LangSmith sur staging puis prod (`LANGCHAIN_API_KEY`, projet) ; envoyer 1–2 messages ; vérifier les runs dans le tableau de bord LangSmith et la cohérence des métadonnées (modèle, latence).
- **O3 —** Après un tour tracé, envoyer pouce haut / bas ; vérifier dans LangSmith que le feedback est attaché au bon run.
- **O4 —** Sous charge faible réelle : confirmer que `agent_turn` continue d’être peuplé comme avant et que LangSmith reçoit toujours les runs ; surveiller le compteur `horain.observability.export` si disponible.
- **O5 —** Exécuter le script [scripts/export-eval-to-langsmith.mjs](../scripts/export-eval-to-langsmith.mjs) depuis une machine de confiance ou la CI avec les variables documentées dans [DEVELOPMENT.md](DEVELOPMENT.md) ; vérifier les entrées dans le dataset LangSmith.
- **O6 —** Activer l’export OTLP sur staging avec un faible taux d’échantillonnage ; comparer la lisibilité des traces (backend OTLP / LangSmith selon configuration) avec les runs créés en O2 ; documenter les risques (double envoi, cardinalité, sampling) déjà couverts dans [ARCH.md](ARCH.md).

Évolutions possibles (non planifiées comme tranches séparées ici) : enrichissement des payloads LangSmith, evals Promptfoo supplémentaires pour l’observabilité, autres fournisseurs (ex. Langfuse) derrière le même `AgentTraceSink`.

## Différé (post-MVP)

Voir [docs/ISSUES.md](ISSUES.md) :

- Streaming des réponses agent (SSE / WebSockets)
- Reasoning interne (o1, o3)
- STT : migration Whisper
- Waveform réaliste
- Module facturation (fournisseurs multiples Qonto / Dougs / PDF in-app — voir [ROADMAP.md](ROADMAP.md) Integrations, détail [ISSUES.md](ISSUES.md))
