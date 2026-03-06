# Horain

**Voice-first time logging assistant** — a Progressive Web App that lets you log time by speaking naturally.

Example: *"I just spent 30 minutes on HatCast working on the selection algorithm."*

The system uses an **LLM-driven tool-calling assistant** for intent detection and structured actions. Architecture follows a **local-first pattern** with asynchronous sync to the server.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│  Frontend (Vue 3 + Vite PWA)                                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────────┐ │
│  │ Conversation│  │ Chat Client  │  │ Dexie       │  │ Sync Engine  │ │
│  │ UI          │──│ POST/chat    │  │ IndexedDB   │◄─│ (push/pull)  │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └──────────────┘ │
└─────────────────────────────────────────────┼───────────────────────┘
                                              │ HTTP
                                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Backend (Spring Boot)                                               │
│  ┌────────────┐  ┌─────────────┐  ┌──────────────┐                   │
│  │ Chat       │  │ LLM         │  │ Tool         │   PostgreSQL      │
│  │ Controller │──│ Orchestration│──│ Executor    │── (Supabase)      │
│  └────────────┘  └─────────────┘  └──────────────┘                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                           │
│  │ /sync/   │  │ /projects│  │ /time-   │                           │
│  │ push,pull│  │          │  │ logs     │                           │
│  └──────────┘  └──────────┘  └──────────┘                           │
└─────────────────────────────────────────────────────────────────────┘
```

### Tool-calling architecture

- User messages go to `POST /chat/message`.
- Backend sends to an LLM with tool definitions (list_projects, search_project, create_project, create_time_log, get_recent_logs, get_time_logs_for_period, sum_time_by_project, sum_time_for_period, get_current_datetime).
- **The LLM decides when to call tools. Tools perform all reads and writes**; the LLM never accesses storage directly.
- Loop continues until the LLM produces a final assistant response.
- Supports both **action requests** (log time, create project) and **analytics questions** (how many hours this week?, what did I do today?).

### Sync flow

- Backend tools write to server; frontend pulls via `GET /sync/pull` to refresh local state.
- Sync runs on: app startup, after each chat response, network online, manual trigger.

## Quick start

### Prerequisites

- Node.js 18+
- Java 17+
- Maven

### Backend

```bash
cd backend
mvn spring-boot:run
```

Uses H2 in-memory by default (no PostgreSQL required). Runs at http://localhost:8080.

For PostgreSQL (e.g. Supabase):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
# Or set SPRING_DATASOURCE_URL, etc. via env vars
```

Backend runs at `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs at `http://localhost:5173`.

### Environment

Copy `frontend/.env.example` to `frontend/.env` for local dev. See [docs/ENV_SETUP.md](docs/ENV_SETUP.md) for the full configuration guide (Supabase, Render, GitHub Actions, OpenAI).

**LLM integration** (required — the assistant requires an LLM):

| Variable      | Description                            | Default              |
|---------------|----------------------------------------|----------------------|
| `LLM_API_KEY` | API key for OpenAI-compatible API      | (required)           |
| `LLM_BASE_URL`| Base URL for chat completions           | `https://api.openai.com/v1` |
| `LLM_MODEL`   | Model name (e.g. gpt-4o-mini)           | `gpt-4o-mini`       |

Without `LLM_API_KEY`, the backend returns a placeholder message instructing you to configure it.

## Project structure

```
horain/
├── backend/           # Spring Boot API
│   └── src/main/java/com/horain/
│       ├── chat/      # ChatController, LlmChatService
│       ├── llm/       # LlmClient, OpenAI-compatible client
│       ├── tools/     # ToolRegistry, ToolExecutorService
│       ├── analytics/ # AnalyticsService
│       ├── config/
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── model/
│       ├── dto/
│       ├── sync/
│       └── auth/
├── frontend/          # Vue 3 + Vite PWA
│   └── src/
│       ├── components/
│       ├── views/
│       ├── services/   # apiClient, chatClient, speechRecognition
│       ├── db/        # Dexie IndexedDB
│       ├── sync/     # Sync engine
│       ├── tools/    # listProjects, createProject, logTime (local)
│       └── pwa/     # Network listener
└── docs/             # Specification, architecture
```

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | /health | Health check (no auth) |
| POST | /chat/message | Send message, get assistant response (LLM + tool calling) |
| POST | /sync/push | Push batch of operations |
| GET | /sync/pull?since=<ms> | Pull updates since timestamp |
| POST | /projects | Create project |
| GET | /projects | List projects |
| POST | /time-logs | Create time log |
| GET | /time-logs | List time logs |

## Key documents

| Document | Purpose |
|----------|---------|
| [docs/ENV_SETUP.md](docs/ENV_SETUP.md) | Environment setup (Supabase, Render, GitHub, OpenAI) |
| [docs/SPEC.md](docs/SPEC.md) | Functional specification |
| [docs/ARCH.md](docs/ARCH.md) | Architecture |
| [docs/DOMAIN.md](docs/DOMAIN.md) | Domain model |
| [docs/DATA_MODEL.md](docs/DATA_MODEL.md) | Database schema |
| [AGENTS.md](AGENTS.md) | Agent governance |
