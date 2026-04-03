# Horain

Horain is a voice-first time journal assistant.

You speak (or type) naturally, and Horain turns it into structured time logs with project matching, clarifications, and conversational confirmations.

## Why Horain

Most time tracking tools start with forms. Horain starts with language.

- Log work in plain sentences
- Keep context through conversation
- Track billable and non-billable time
- Ask analytics questions without building reports manually

Example:

> "I just spent 30 minutes on HatCast working on the selection algorithm."

## What You Can Do

- Capture activity from voice or text
- Create, update, and find projects conversationally
- Log time with note, date, billable flag, and activity type
- Ask period questions (today, this week, this month)
- Get chart-ready aggregations from the assistant flow
- Review recent activity directly in the app on first load

For exact functional behavior, use the normative spec: [docs/SPEC.md](docs/SPEC.md).

## Quick Start

### Prerequisites

- Node 20+
- npm or pnpm
- JDK 21+
- Maven

### Run locally

```bash
./scripts/start-dev.sh
```

This starts:

- Backend on `http://localhost:8080`
- Frontend on `https://localhost:5173` (HTTPS dev server for microphone usage)

You can also run services separately:

```bash
# Backend
cd backend
mvn spring-boot:run

# Frontend
cd frontend
npm install
npm run dev
```

### LLM setup (required for real assistant responses)

Configure `backend/.env` from `backend/.env.example`, then set at least one key:

- `LLM_API_KEY`, or
- `OPENAI_API_KEY`

Optional: `LLM_MODEL`, `LLM_BASE_URL`, multi-model routing variables.

Full environment and deployment setup: [docs/ENV_SETUP.md](docs/ENV_SETUP.md).

## Repo Layout

```text
horain/
├── backend/      # Spring Boot + Kotlin API, orchestration, tools
├── frontend/     # Vue 3 + Vite PWA client
├── docs/         # Product, architecture, data, UX, governance
├── promptfoo/    # Agent eval suite
├── scripts/      # Dev, test, release, eval automation
└── langsmith/    # LangSmith export/import/evaluation tooling
```

## Documentation Map

This README is intentionally introductory.

Normative behavior and structure live in:

- [docs/SPEC.md](docs/SPEC.md)
- [docs/DOMAIN.md](docs/DOMAIN.md)
- [docs/ARCH.md](docs/ARCH.md)
- [docs/MCP_TOOLS.md](docs/MCP_TOOLS.md)
- [docs/AGENT_DESIGN.md](docs/AGENT_DESIGN.md)
- [docs/DATA_MODEL.md](docs/DATA_MODEL.md)
- [docs/UX.md](docs/UX.md)
- [docs/EVALS.md](docs/EVALS.md)
- [docs/WORKFLOW.md](docs/WORKFLOW.md)
- [docs/ADR/](docs/ADR/)
- [AGENTS.md](AGENTS.md)

Operational/progress docs:

- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)
- [docs/PLAN.md](docs/PLAN.md)
- [docs/ISSUES.md](docs/ISSUES.md)

## Quality Gates

- Backend tests: `cd backend && mvn test`
- Frontend e2e: `./scripts/run-tests.sh e2e`
- Agent evals: `./scripts/run-promptfoo-eval.sh --deterministic-only`

See full testing and CI workflow in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) and eval contract rules in [docs/EVALS.md](docs/EVALS.md).
