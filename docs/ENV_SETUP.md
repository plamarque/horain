# Environment setup guide

This document explains how to configure Horain for local development and production (Supabase, Cloud Run, GitHub Actions, OpenAI).

---

## Summary of configuration locations

| Variable | Local dev | Cloud Run | GitHub Actions |
|----------|-----------|-----------|----------------|
| `VITE_API_URL` | frontend/.env → vide (proxy /api) | — | Repository Secret → URL du service Cloud Run |
| `VITE_API_KEY` | frontend/.env | — | Repository Secret |
| `SPRING_DATASOURCE_URL` | — | Service env / Secret Manager | — |
| `SPRING_DATASOURCE_USERNAME` | — | Service env | — |
| `SPRING_DATASOURCE_PASSWORD` | — | Service env / Secret Manager | — |
| `SPRING_PROFILES_ACTIVE` | — | Service env | — |
| `HORAIN_API_KEY` | — | Service env | — |
| `LLM_API_KEY` / `OPENAI_API_KEY` | backend/.env | Service env / Secret Manager | Clé API du fournisseur LLM |
| `LLM_BASE_URL` | backend/.env | Service env | URL de base (optionnel, défaut: OpenAI v1) |
| `LLM_MODEL` | backend/.env | Service env | Modèle (optionnel, défaut: gpt-4o-mini) |

**Important:** En dev local, laisser `VITE_API_URL` vide pour utiliser le proxy Vite (`/api`). Cela fonctionne à la fois sur localhost et sur smartphone (réseau local).

En production, le build GitHub Actions injecte `VITE_API_URL` depuis les secrets (URL du service Cloud Run). Le frontend déployé sur GitHub Pages pointe vers le backend sur Cloud Run.

---

## A. Supabase (database)

### 1. Create a project

1. Go to [supabase.com](https://supabase.com) and sign in
2. Create a new project
3. Choose a region and set a database password (save it securely)

### 2. Get connection details

1. **Project Settings** → **Database**
2. Under **Connection string**, set **Method** to **Session** (or **Transaction** for serverless)
3. Copy the URI shown

**Important:** Cloud Run, GitHub Actions and other major platforms are **IPv4-only**. The direct connection (port 5432 to `db.xxx.supabase.co`) is **not IPv4 compatible**. You must use the **Session Pooler** instead.

### 3. Build the JDBC URL for the backend (Session Pooler)

1. In Supabase: **Project Settings** → **Database** → **Method** = **Session pooler**
2. Type = **JDBC**. Copy the host and port from the connection string (e.g. `aws-1-eu-west-1.pooler.supabase.com:5432`)
3. Use **separate variables** — do not embed the password in the URL:

| Variable | Value |
|----------|-------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://aws-1-eu-west-1.pooler.supabase.com:5432/postgres` (no `?user=` or `?password=`) |
| `SPRING_DATASOURCE_USERNAME` | `postgres.zganzlhymnrdrmryungy` (from Supabase) |
| `SPRING_DATASOURCE_PASSWORD` | **Your real database password** (replace `[YOUR-PASSWORD]`) |

**Important:** Put your actual Supabase database password in `SPRING_DATASOURCE_PASSWORD`. Do not use a placeholder — Spring Boot needs the real password to connect. On Cloud Run, use Secret Manager or environment variables (see [CLOUD_RUN_SETUP.md](CLOUD_RUN_SETUP.md)).

**Transaction pooler (port 6543):** If you hit "MaxClientsInSessionMode" with Session pooler, use **Transaction pooler** in Supabase (Method = Transaction pooler, port 6543). Append `?prepareThreshold=0` to the JDBC URL (e.g. `jdbc:postgresql://HOST:6543/postgres?prepareThreshold=0`), because Transaction mode does not support prepared statements. See [CLOUD_RUN_SETUP.md](CLOUD_RUN_SETUP.md).

---

## B. Google Cloud Run (backend)

### 1. Create the service and trigger

See [docs/CLOUD_RUN_SETUP.md](CLOUD_RUN_SETUP.md) for the full checklist: enable APIs, create Artifact Registry repository, connect the GitHub repo, create a Cloud Build trigger on `main`, and configure the first deployment.

### 2. Environment variables (Cloud Run service)

In Cloud Run → your service → **Edit & deploy new revision** → **Variables and secrets**, add:

| Key | Value |
|-----|-------|
| `SPRING_PROFILES_ACTIVE` | `postgres` |
| `SPRING_DATASOURCE_URL` | From Supabase (JDBC URL above) |
| `SPRING_DATASOURCE_USERNAME` | From Supabase (e.g. `postgres.PROJECT_REF`) |
| `SPRING_DATASOURCE_PASSWORD` | Your Supabase database password (prefer Secret Manager) |
| `HORAIN_API_KEY` | A secure random string (e.g. `openssl rand -hex 32`). The frontend will use this same value. |
| `LLM_API_KEY` or `OPENAI_API_KEY` | Your API key (OpenAI sk-..., OpenRouter sk-or-...) |
| `LLM_BASE_URL` | (optional) `https://api.openai.com/v1` or `https://openrouter.ai/api/v1` |
| `LLM_MODEL` | (optional) `gpt-4o-mini` (OpenAI) or OpenRouter model |

Prefer **Secret Manager** for `SPRING_DATASOURCE_PASSWORD` and API keys; see [CLOUD_RUN_SETUP.md](CLOUD_RUN_SETUP.md).

### 3. Get the backend URL

After the first deployment, Cloud Run shows the service URL (e.g. `https://horain-api-xxxxx-ew.a.run.app`).  
Use this URL as `VITE_API_URL` in GitHub Actions secrets (section C).

---

## C. GitHub (Repository Secrets + Pages)

Le frontend est buildé par GitHub Actions (`.github/workflows/deploy.yml`) et déployé sur GitHub Pages à chaque push sur `main`. Le backend sur Cloud Run se redéploie automatiquement via le trigger Cloud Build à chaque push sur `main` (voir [CLOUD_RUN_SETUP.md](CLOUD_RUN_SETUP.md)).

**En dev local** (`npm run dev`), le frontend lit `frontend/.env`. Laisser `VITE_API_URL` vide pour le proxy (localhost + smartphone).

**En production**, le workflow GitHub Actions utilise les secrets. Le frontend déployé pointe vers le backend Cloud Run.

### 1. Activer GitHub Pages

1. Repo → **Settings** → **Pages**
2. **Build and deployment** → **Source** : choisir **GitHub Actions**

### 2. Add secrets

1. Repo → **Settings** → **Secrets and variables** → **Actions**
2. **New repository secret** pour chaque :

| Secret name | Value |
|-------------|-------|
| `VITE_API_URL` | URL du backend Cloud Run, ex. `https://horain-api-xxxxx-ew.a.run.app` |
| `VITE_API_KEY` | Même valeur que `HORAIN_API_KEY` sur Cloud Run |
| `VITE_STT_LANG` | (optionnel) Langue STT, ex. `fr-FR` pour forcer le français |
| `OPENAI_API_KEY` | Clé API LLM pour les tests e2e en CI (voir [DEVELOPMENT.md](DEVELOPMENT.md) CI) |

Le workflow passe ces secrets au build Vite. Le bundle contiendra l’URL de prod. L'app sera accessible sur `https://<owner>.github.io/<repo>/` (ex. `https://patrice.github.io/horain/`).

---

## D. LLM (assistant conversationnel)

L'assistant nécessite un fournisseur LLM pour répondre aux questions ("combien d'heures ?", etc.). Sans clé API, un PlaceholderLlmClient renvoie des réponses fixes.

### 1. Variables (voir `backend/.env.example`)

| Variable | Obligatoire | Description |
|----------|-------------|-------------|
| `LLM_API_KEY` ou `OPENAI_API_KEY` | Oui | Clé API du fournisseur (OpenAI, OpenRouter, etc.) |
| `LLM_BASE_URL` | Non | URL de base de l'API chat. Défaut: `https://api.openai.com/v1`. OpenRouter: `https://openrouter.ai/api/v1` |
| `LLM_MODEL` | Non | Modèle à utiliser. Défaut: `gpt-4o-mini`. OpenRouter: `openai/gpt-4o-mini`, `anthropic/claude-3-haiku`, etc. |
| `LLM_CLIENT` | Non | Client à utiliser. Vide = détection auto (modèles o1/o3/o4-mini → Responses API avec repli sur Completions si échec). Valeurs: `openai-responses`, `openai-compatible`, `spring-ai`. |

**Multi-model routing (3 niveaux)** — Si les trois variables suivantes sont renseignées, le backend route chaque requête vers un modèle selon la complexité (simple / complexe / très complexe) :

| Variable | Description |
|----------|-------------|
| `LLM_MODEL_SIMPLE` | Modèle sans raisonnement (confirmations, listes). Ex. `gpt-4o-mini`. |
| `LLM_MODEL_COMPLEX` | Modèle avec raisonnement (effort medium). Ex. `o4-mini`. |
| `LLM_MODEL_VERY_COMPLEX` | Modèle avec raisonnement (effort high). Ex. `gpt-5.4`. |
| `LLM_REASONING_EFFORT_COMPLEX` | (optionnel) Effort raisonnement pour le niveau complexe. Défaut: `medium`. |
| `LLM_REASONING_EFFORT_VERY_COMPLEX` | (optionnel) Effort raisonnement pour le niveau très complexe. Défaut: `high`. |

Si seul `LLM_MODEL` est défini, le comportement reste mono-modèle (pas de routeur).

### 2. Fournisseurs supportés

- **OpenAI** – Clé sur [platform.openai.com](https://platform.openai.com/api-keys). Base URL par défaut.
- **OpenRouter** – Clé sur [openrouter.ai](https://openrouter.ai/keys). Définir `LLM_BASE_URL=https://openrouter.ai/api/v1`.
- **LiteLLM / proxy custom** – Base URL de votre endpoint, se terminant par `/v1`.

### 3. Configurer sur Cloud Run

Ajouter les variables (ou secrets) dans le service Cloud Run (section B.2). Ne pas exposer la clé API.

---

## E. Recommended setup order

1. **Supabase** – Create project, get connection details
2. **Cloud Run** – Create service and Cloud Build trigger, add Supabase + `HORAIN_API_KEY` env vars (see [CLOUD_RUN_SETUP.md](CLOUD_RUN_SETUP.md))
3. **GitHub** – Add `VITE_API_URL` and `VITE_API_KEY` secrets for the frontend workflow
4. **OpenAI** – Create key, add `OPENAI_API_KEY` to Cloud Run (when ready for LLM)

---

## Local development

### Frontend

```bash
cd frontend
cp .env.example .env
# .env : VITE_API_URL vide = proxy /api (localhost + smartphone)
npm run dev
```

Le frontend utilise le proxy Vite vers le backend. Fonctionne sur localhost et sur smartphone (via l’IP réseau affichée par `start-dev.sh`).

### Backend

- **Without PostgreSQL:** `mvn spring-boot:run` uses H2 in-memory (default)
- **With PostgreSQL:** Set env vars or use the `postgres` profile with local DB:

```bash
export SPRING_PROFILES_ACTIVE=postgres
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/horain
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres
export HORAIN_API_KEY=HORAIN_DEV_KEY
mvn spring-boot:run
```

Or use `scripts/start-dev.sh` for frontend + backend with H2.

---

## Reference files

- [frontend/.env.example](../frontend/.env.example) – Frontend variables template
- [backend/.env.example](../backend/.env.example) – Backend variables template (Cloud Run / local)
