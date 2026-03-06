# Horain

**Voice-first time logging assistant** — a Progressive Web App that lets you log time by speaking naturally.

Example: *"I just spent 30 minutes on HatCast working on the selection algorithm."*

The system extracts structured data and logs the activity. Architecture follows a **local-first pattern** with asynchronous sync to the server.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Frontend (Vue 3 + Vite PWA)                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐   │
│  │ Conversation│  │ Dexie      │  │ Sync Engine         │   │
│  │ Agent       │──│ IndexedDB  │◄─│ (push/pull)         │   │
│  └─────────────┘  └─────────────┘  └──────────┬──────────┘   │
└───────────────────────────────────────────────┼──────────────┘
                                                │ HTTP
                                                ▼
┌─────────────────────────────────────────────────────────────┐
│  Backend (Spring Boot)                                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                   │
│  │ /sync/   │  │ /projects │  │ /time-   │  PostgreSQL       │
│  │ push,pull│  │          │  │ logs     │  (Supabase)        │
│  └──────────┘  └──────────┘  └──────────┘                   │
└─────────────────────────────────────────────────────────────┘
```

### Sync flow

- All writes happen **locally first** (IndexedDB).
- Operations are queued and pushed to the server via `POST /sync/push`.
- Server updates are pulled via `GET /sync/pull?since=<timestamp>`.
- Sync runs on: app startup, network online, manual trigger.

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

## Project structure

```
horain/
├── backend/           # Spring Boot API
│   └── src/main/java/com/horain/
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
│       ├── services/
│       ├── db/        # Dexie IndexedDB
│       ├── sync/     # Sync engine
│       ├── agent/    # Conversation agent (rule-based)
│       ├── tools/    # listProjects, createProject, logTime
│       └── pwa/     # Network listener
└── docs/             # Specification, architecture
```

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | /health | Health check (no auth) |
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
