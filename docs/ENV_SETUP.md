# Environment setup guide

This document explains how to configure Horain for local development and production (Supabase, Render, GitHub Actions, OpenAI).

---

## Summary of configuration locations

| Variable | Local dev | Render | GitHub Actions |
|----------|-----------|--------|----------------|
| `VITE_API_URL` | frontend/.env | — | Repository Secret |
| `VITE_API_KEY` | frontend/.env | — | Repository Secret |
| `SPRING_DATASOURCE_URL` | — | Environment | — |
| `SPRING_DATASOURCE_USERNAME` | — | Environment | — |
| `SPRING_DATASOURCE_PASSWORD` | — | Environment | — |
| `SPRING_PROFILES_ACTIVE` | — | Environment | — |
| `HORAIN_API_KEY` | — | Environment | — |
| `OPENAI_API_KEY` | — | Environment | — |

---

## A. Supabase (database)

### 1. Create a project

1. Go to [supabase.com](https://supabase.com) and sign in
2. Create a new project
3. Choose a region and set a database password (save it securely)

### 2. Get connection details

1. **Project Settings** → **Database**
2. Under **Connection string**, select **URI**
3. Copy the connection string. It looks like:
   ```
   postgresql://postgres.[PROJECT-REF]:[YOUR-PASSWORD]@aws-0-[REGION].pooler.supabase.com:6543/postgres
   ```

### 3. Build the JDBC URL for Render

For Spring Boot, use the **direct** connection (port 5432), not the pooler:

1. In Supabase: **Project Settings** → **Database** → **Connection string** → **URI** (direct connection)
2. Or construct it: `jdbc:postgresql://db.[PROJECT-REF].supabase.co:5432/postgres`
3. Replace `[PROJECT-REF]` with your project reference (visible in the Supabase dashboard URL)
4. The username is usually `postgres`
5. Use the database password you set when creating the project

**Example:**
```
SPRING_DATASOURCE_URL=jdbc:postgresql://db.abcdefghijk.supabase.co:5432/postgres
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_password
```

---

## B. Render (backend)

### 1. Create a Web Service

1. Go to [render.com](https://render.com) and sign in
2. **New** → **Web Service**
3. Connect your GitHub repository (horain)
4. Configure:
   - **Name:** e.g. `horain-api`
   - **Region:** choose closest to your Supabase region
   - **Build command:** `cd backend && mvn -B package -DskipTests`
   - **Start command:** `cd backend && java -jar target/horain-backend-*.jar`
   - **Instance type:** Free (or paid for production)

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

After deployment, Render provides a URL like `https://horain-api.onrender.com`.  
Use this as `VITE_API_URL` for the frontend.

---

## C. GitHub (Repository Secrets for frontend build)

The frontend is built by GitHub Actions and deployed to GitHub Pages. Build-time variables must be set as **Repository Secrets**.

### 1. Add secrets

1. Go to your repo: **Settings** → **Secrets and variables** → **Actions**
2. **New repository secret** for each:

| Secret name | Value |
|-------------|-------|
| `VITE_API_URL` | Your Render backend URL, e.g. `https://horain-api.onrender.com` |
| `VITE_API_KEY` | Same value as `HORAIN_API_KEY` on Render |

**Important:** Vite bakes these into the bundle at build time. They are not secret (they appear in the client), but `VITE_API_KEY` should still be a fixed token that only your frontend knows, to avoid unauthorized API access.

---

## D. OpenAI (for future Spring AI agent)

### 1. Create an API key

1. Go to [platform.openai.com](https://platform.openai.com)
2. Sign in or create an account
3. **API keys** → **Create new secret key**
4. Copy the key (starts with `sk-`). It is shown only once.

### 2. Add to Render

Add `OPENAI_API_KEY` to your Render Web Service environment variables (see section B.2).

**Note:** The current implementation uses a rule-based agent. The OpenAI key will be used when Spring AI is integrated (Slice 3 in [PLAN.md](PLAN.md)).

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
# Edit .env if needed (defaults work with local backend)
npm run dev
```

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
