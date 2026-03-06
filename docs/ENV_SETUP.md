# Environment setup guide

This document explains how to configure Horain for local development and production (Supabase, Render, GitHub Actions, OpenAI).

---

## Summary of configuration locations

| Variable | Local dev | Render | GitHub Actions |
|----------|-----------|--------|----------------|
| `VITE_API_URL` | frontend/.env → `http://localhost:8080` | — | Repository Secret → `https://horain.onrender.com` |
| `VITE_API_KEY` | frontend/.env | — | Repository Secret |
| `SPRING_DATASOURCE_URL` | — | Environment | — |
| `SPRING_DATASOURCE_USERNAME` | — | Environment | — |
| `SPRING_DATASOURCE_PASSWORD` | — | Environment | — |
| `SPRING_PROFILES_ACTIVE` | — | Environment | — |
| `HORAIN_API_KEY` | — | Environment | — |
| `OPENAI_API_KEY` | — | Environment | — |

**Important:** En dev local, le frontend tourne sur localhost et utilise `http://localhost:8080`.

En production, le build GitHub Actions injecte `VITE_API_URL` depuis les secrets (ex. `https://horain.onrender.com`). Le frontend déployé sur GitHub Pages pointe vers le backend Render.

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

**Important:** Render, GitHub Actions and other major platforms are **IPv4-only**. The direct connection (port 5432 to `db.xxx.supabase.co`) is **not IPv4 compatible**. You must use the **Session Pooler** instead.

### 3. Build the JDBC URL for Render (Session Pooler)

1. In Supabase: **Project Settings** → **Database** → **Method** = **Session pooler**
2. Type = **JDBC**. Copy the host and port from the connection string (e.g. `aws-1-eu-west-1.pooler.supabase.com:5432`)
3. Use **separate variables** — do not embed the password in the URL:

| Variable | Value |
|----------|-------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://aws-1-eu-west-1.pooler.supabase.com:5432/postgres` (no `?user=` or `?password=`) |
| `SPRING_DATASOURCE_USERNAME` | `postgres.zganzlhymnrdrmryungy` (from Supabase) |
| `SPRING_DATASOURCE_PASSWORD` | **Your real database password** (replace `[YOUR-PASSWORD]`) |

**Important:** Put your actual Supabase database password in `SPRING_DATASOURCE_PASSWORD`. Do not use a placeholder — Spring Boot needs the real password to connect. On Render, add it as a secret environment variable (it will not be visible in logs).

---

## B. Render (backend)

### 1. Create a Web Service

See [docs/RENDER_SETUP.md](RENDER_SETUP.md) for a copy-paste checklist.

1. Go to [render.com](https://render.com) and sign in
2. **New** → **Web Service**
3. Connect your GitHub repository (horain)
4. Voir [RENDER_SETUP.md](RENDER_SETUP.md) pour les champs (Docker, Root Directory, etc.)

### 2. Environment variables (Render dashboard)

In your Web Service → **Environment** tab, add:

| Key | Value |
|-----|-------|
| `SPRING_PROFILES_ACTIVE` | `postgres` |
| `SPRING_DATASOURCE_URL` | From Supabase (JDBC URL above) |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Your Supabase database password |
| `HORAIN_API_KEY` | A secure random string (e.g. `openssl rand -hex 32`). The frontend will use this same value. |
| `OPENAI_API_KEY` | Your OpenAI API key (for future LLM agent) |

### 3. Get the backend URL

After deployment, Render provides a URL (ex. `https://horain.onrender.com`).  
Cette URL sert pour `VITE_API_URL` du build frontend en production (voir section C).

---

## C. GitHub (Repository Secrets + Pages)

Le frontend est buildé par GitHub Actions (`.github/workflows/deploy.yml`) et déployé sur GitHub Pages à chaque push sur `main`. Le backend sur Render se redéploie automatiquement si le repo est connecté (voir [RENDER_SETUP.md](RENDER_SETUP.md)).

**En dev local** (`npm run dev`), le frontend lit `frontend/.env` (ex. `VITE_API_URL=http://localhost:8080`).

**En production**, le workflow GitHub Actions utilise les secrets. Le frontend déployé pointe vers le backend Render.

### 1. Activer GitHub Pages

1. Repo → **Settings** → **Pages**
2. **Build and deployment** → **Source** : choisir **GitHub Actions**

### 2. Add secrets

1. Repo → **Settings** → **Secrets and variables** → **Actions**
2. **New repository secret** pour chaque :

| Secret name | Value |
|-------------|-------|
| `VITE_API_URL` | URL du backend Render, ex. `https://horain.onrender.com` |
| `VITE_API_KEY` | Même valeur que `HORAIN_API_KEY` sur Render |

Le workflow passe ces secrets au build Vite. Le bundle contiendra l’URL de prod. L'app sera accessible sur `https://<owner>.github.io/<repo>/` (ex. `https://patrice.github.io/horain/`).

---

## D. OpenAI (for future Spring AI agent)

### 1. Create an API key

1. Go to [platform.openai.com](https://platform.openai.com)
2. Sign in or create an account
3. **API keys** → **Create new secret key**
4. Copy the key (starts with `sk-`). It is shown only once.

### 2. Add to Render

Add `OPENAI_API_KEY` to your Render Web Service environment variables (see section B.2).

**Note:** The assistant requires an LLM. Configure `LLM_API_KEY` on the backend (or `OPENAI_API_KEY` if using the Spring default). See `backend/.env.example` for the exact variable names.

---

## E. Recommended setup order

1. **Supabase** – Create project, get connection details
2. **Render** – Create Web Service, add Supabase + `HORAIN_API_KEY`, deploy
3. **GitHub** – Add `VITE_API_URL` and `VITE_API_KEY` secrets for the frontend workflow
4. **OpenAI** – Create key, add `OPENAI_API_KEY` to Render (when ready for LLM)

---

## Local development

### Frontend

```bash
cd frontend
cp .env.example .env
# .env contient VITE_API_URL=http://localhost:8080 (backend local)
npm run dev
```

Le frontend tourne sur localhost et appelle le backend local. Ne pas modifier `VITE_API_URL` en dev — `frontend/.env` reste avec localhost.

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
- [backend/.env.example](../backend/.env.example) – Backend variables template for Render
