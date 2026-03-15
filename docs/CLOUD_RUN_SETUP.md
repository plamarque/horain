# Google Cloud Run — Backend deployment guide

This guide explains how to deploy the Horain backend to Cloud Run with continuous deployment on push to `main` (Cloud Build trigger). The database stays on Supabase; only the backend runtime moves to GCP.

---

## Prerequisites

- A Google Cloud project
- GitHub repo connected (Cloud Build GitHub App or Developer Connect)
- Supabase project with Session Pooler JDBC details (see [ENV_SETUP.md](ENV_SETUP.md) section A)

---

## 1. Enable APIs

In the [Google Cloud Console](https://console.cloud.google.com), enable:

- Cloud Build API
- Cloud Run API
- Artifact Registry API
- Resource Manager API (usually already on)

Or run:

```bash
gcloud services enable cloudbuild.googleapis.com run.googleapis.com artifactregistry.googleapis.com
```

---

## 2. Artifact Registry repository

Create a Docker repository in a region aligned with your Cloud Run service (e.g. `europe-west1`):

1. **Artifact Registry** → **Create repository**
2. **Name:** e.g. `horain`
3. **Format:** Docker
4. **Mode:** Standard
5. **Location type:** Region → e.g. `europe-west1`

Or:

```bash
gcloud artifacts repositories create horain \
  --repository-format=docker \
  --location=europe-west1
```

---

## 3. Cloud Build permissions

Ensure the Cloud Build service account can deploy to Cloud Run and push to Artifact Registry:

1. Go to [Cloud Build Settings](https://console.cloud.google.com/cloud-build/settings) (or **Cloud Build** → **Settings**).
2. Under **Service account permissions**, enable:
   - **Cloud Run Admin**
   - **Service Account User** (for the Cloud Run runtime service account)
   - **Artifact Registry** (write to the repository you created)

Or grant roles to the default Cloud Build service account (`PROJECT_NUMBER@cloudbuild.gserviceaccount.com`):

- `roles/run.admin`
- `roles/iam.serviceAccountUser`
- `roles/artifactregistry.writer`

---

## 4. Connect GitHub and create the trigger

### Option A: From Cloud Run console

1. Go to [Cloud Run](https://console.cloud.google.com/run).
2. **Create Service** (or **Connect repository** if adding to an existing service).
3. Choose **Cloud Build** as the source, then connect your GitHub account and select the `horain` repo.
4. **Branch:** `^main$`
5. **Build type:** Dockerfile
6. **Source location:** `backend/Dockerfile` (path relative to repo root)
7. **Build context directory:** `backend`
8. Create the service; on first run Cloud Build will use the trigger. You can instead define the build in a config file (see Option B).

### Option B: Trigger with repo `cloudbuild.yaml`

The repo contains a root-level [cloudbuild.yaml](../cloudbuild.yaml) that builds from `backend/`, pushes to Artifact Registry, and deploys to Cloud Run.

1. Go to [Cloud Build Triggers](https://console.cloud.google.com/cloud-build/triggers).
2. **Create Trigger**
3. **Name:** e.g. `horain-backend-deploy`
4. **Region:** same as Artifact Registry (e.g. `europe-west1`)
5. **Event:** Push to a branch
6. **Source:** your connected GitHub repo, branch `^main$`
7. **Configuration:** Cloud Build configuration file (YAML or JSON)
8. **Location:** Repository; path `cloudbuild.yaml` (at repo root)
9. **Substitution variables** (add these so the YAML does not hardcode project/region):

   | Variable        | Value           |
   |----------------|-----------------|
   | `_REGION`      | `europe-west1`  |
   | `_SERVICE_NAME`| `horain-api`    |
   | `_REPOSITORY`  | `horain`        |

10. Save. A push to `main` will run the build and deploy.

---

## 5. First deployment and environment variables

If you created the service from the console (Option A), the first revision may run with no env vars. Add them before or right after the first deploy.

1. **Cloud Run** → your service (`horain-api`) → **Edit & deploy new revision**.
2. **Variables and secrets** → add:

| Key | Value |
|-----|-------|
| `SPRING_PROFILES_ACTIVE` | `postgres` |
| `SPRING_DATASOURCE_URL` | **Session** pooler (port 5432): low connection limit, app uses pool size 1. **Transaction** pooler (port 6543): higher capacity; you **must** add `?prepareThreshold=0` to the URL (e.g. `jdbc:postgresql://HOST:6543/postgres?prepareThreshold=0`), or `&prepareThreshold=0` if the URL already has query params. Transaction mode does not support prepared statements; this parameter disables them for the JDBC driver. |
| `SPRING_DATASOURCE_USERNAME` | From Supabase (e.g. `postgres.PROJECT_REF`) |
| `SPRING_DATASOURCE_PASSWORD` | Supabase DB password (prefer Secret Manager, see below) |
| `HORAIN_API_KEY` | e.g. `openssl rand -hex 32` (same value as `VITE_API_KEY` in GitHub secrets) |

Optional (LLM):

| Key | Value |
|-----|-------|
| `OPENAI_API_KEY` or `LLM_API_KEY` | Your OpenAI/OpenRouter key |
| `LLM_BASE_URL` | (optional) `https://api.openai.com/v1` or `https://openrouter.ai/api/v1` |
| `LLM_MODEL` | (optional) `gpt-4o-mini` or OpenRouter model |

3. **Deploy**.

---

## 6. Secrets (recommended)

For `SPRING_DATASOURCE_PASSWORD` and API keys, use Secret Manager:

1. Create a secret in **Secret Manager** with the value.
2. In Cloud Run → **Edit & deploy new revision** → **Variables and secrets** → **Reference a secret**.
3. Select the secret and choose the env var name (e.g. `SPRING_DATASOURCE_PASSWORD`).

When using `gcloud run deploy` (e.g. in a custom build step), use `--set-secrets=SPRING_DATASOURCE_PASSWORD=db-password:latest`.

---

## 7. Backend URL and GitHub Actions

After the first successful deployment:

1. In Cloud Run, copy the **Service URL** (e.g. `https://horain-api-xxxxx-ew.a.run.app`).
2. In GitHub: **Settings** → **Secrets and variables** → **Actions** → set (or update) **VITE_API_URL** to this URL.
3. **VITE_API_KEY** must match **HORAIN_API_KEY** on Cloud Run.

The next frontend build and deploy will use the new backend URL.

---

## 8. Rollback / coexistence with Render

- As long as **VITE_API_URL** in GitHub points to Render, the app keeps using the Render backend.
- To switch: configure Cloud Run and the trigger, deploy once, set env vars, then update **VITE_API_URL** to the Cloud Run URL.
- To roll back: set **VITE_API_URL** back to the Render URL and redeploy the frontend (or leave Render running and just switch the secret).

---

## 9. Request timeout (streaming)

The chat stream endpoint (`POST /chat/message/stream`) keeps the connection open while the agent may run several tool-call rounds. Cloud Run allows configuring the **request timeout** (default is 5 minutes). If users hit timeouts on long conversations, in **Cloud Run** → your service → **Edit & deploy new revision** → **Container** → set **Request timeout** to e.g. 300 seconds (or leave the default). The backend uses a 5-minute timeout for the SSE emitter.

---

## 10. Troubleshooting: "Container failed to start and listen on PORT"

If the revision fails with *"The user-provided container failed to start and listen on the port defined by the PORT=8080 environment variable"*:

1. **Check Cloud Logging** (link in the error, or **Observability** → **Logs**): look for Java stack traces, Flyway errors, or database connection failures. The app may be crashing before binding to the port (e.g. missing `SPRING_PROFILES_ACTIVE=postgres`, wrong `SPRING_DATASOURCE_*`, or DB unreachable).

2. **Verify service env vars** (Cloud Run → your service → **Edit & deploy** → **Variables and secrets**): at least `SPRING_PROFILES_ACTIVE=postgres`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`. Without these, the app can fail on startup. If you see **"MaxClientsInSessionMode: max clients reached"**, switch to Supabase **Transaction** pooler (port 6543): in Supabase **Project Settings → Database**, choose **Transaction pooler**, copy the JDBC URL, and append `?prepareThreshold=0` (or `&prepareThreshold=0` if the URL already has params). Then set that full URL in `SPRING_DATASOURCE_URL`. You can then increase Hikari pool size if needed (default remains 1 for Session mode).

3. **Port and binding**: The repo is configured so the backend listens on `0.0.0.0` and `PORT` (default 8080). The build uses `--port=8080` and `--cpu-boost` when deploying via the repo `cloudbuild.yaml`. If you use **Cloud Run "Connect repository"** (source deploy), configure the service with port 8080 and CPU boost in the Cloud Run console (Edit & deploy → Container → Port, and enable startup CPU boost). Ensure the container listens on all interfaces.

4. **Startup timeout**: Cloud Run allows up to 240 seconds for the container to listen on the port. The app uses **lazy initialization** in the `postgres` profile so the HTTP server binds to the port before DataSource/Flyway run (they initialize on first request). If the container still fails, run the image locally to see the real error: `docker run --rm -e PORT=8080 -e SPRING_PROFILES_ACTIVE=postgres -e SPRING_DATASOURCE_URL=... -e SPRING_DATASOURCE_USERNAME=... -e SPRING_DATASOURCE_PASSWORD=... -p 8080:8080 YOUR_IMAGE` (use the same env as Cloud Run).

See [Cloud Run troubleshooting](https://cloud.google.com/run/docs/troubleshooting#container-failed-to-start) for more.

---

## 11. Troubleshooting: Cannot connect to Supabase

If Cloud Run fails to connect to the database (connection timeout, auth failure, or "Connection is not available"):

1. **Supabase status**  
   Check [status.supabase.com](https://status.supabase.com). If **Connection Pooler** and **Database** are operational, the global pool is not down. Your project can still be paused or misconfigured.

2. **Project not paused**  
   In **Supabase Dashboard** → your project: if it was inactive (free tier), the project may be **paused**. Click **Restore project** and wait a few minutes, then redeploy or retry.

3. **Test from your machine**  
   From your laptop, test the same connection so we know if the problem is Cloud Run or Supabase:
   - **Session pooler:** host `aws-1-eu-west-1.pooler.supabase.com`, port **5432** (replace with your project’s pooler host).
   - With `psql`:  
     `psql "postgresql://postgres.PROJECT_REF:YOUR_PASSWORD@aws-1-eu-west-1.pooler.supabase.com:5432/postgres"`  
     (get the exact URI from Supabase **Project Settings** → **Database** → **Connection string** → Session pooler.)
   - If this fails, the issue is credentials or Supabase (wrong password, project paused, or pooler URL). If it works, the issue is likely Cloud Run (env vars or Secret Manager).

4. **Secret Manager and password change**  
   If you changed the DB password and use a **secret** for `SPRING_DATASOURCE_PASSWORD`:
   - Update the secret in **Secret Manager** with the **new** password (new version).
   - Cloud Run references a secret by name (e.g. `projects/.../secrets/db-password/versions/latest`). Ensure **latest** points to the new version, or create a new version and redeploy so the new revision uses it.
   - **Edit & deploy new revision** after updating the secret so the new revision gets the new value.

5. **Exact env var names**  
   Must be set on the Cloud Run service (Variables and secrets):
   - `SPRING_PROFILES_ACTIVE` = `postgres`
   - `SPRING_DATASOURCE_URL` = `jdbc:postgresql://HOST:5432/postgres` (Session; no `?user=` or `?password=` in URL)
   - `SPRING_DATASOURCE_USERNAME` = from Supabase (e.g. `postgres.xxxxx`)
   - `SPRING_DATASOURCE_PASSWORD` = the actual DB password (or reference to Secret Manager)

   Typo or wrong case (e.g. `DATABASE_URL` instead of `SPRING_DATASOURCE_URL`) will prevent connection.

6. **"Connection is not available, request timed out" (no auth error)**  
   If logs show a **timeout** (e.g. HikariCP "request timed out after 15001ms") and not "password authentication failed", outbound traffic from Cloud Run to Supabase may be blocked:

   - **Cloud Run** → your service → **Edit & deploy new revision** → open **Networking** (or **Connections**, **Security**, **Container** depending on console layout).
   - Check **VPC / Egress**:
     - If a **VPC connector** is set or **Direct VPC egress** is enabled with **"Private ranges only"**, all egress goes through the VPC. Without a route to the internet (e.g. Cloud NAT), traffic to Supabase (public host) will hang and time out.
     - **Fix:** Either **remove the VPC connector** and use default egress (traffic goes to the internet directly), or set egress to **"All traffic"** so that only private IP ranges use the VPC and public IPs (Supabase) use the default internet path. Do not use "Private ranges only" if the only external service is Supabase on the public internet.
   - Redeploy after changing the setting.

---

## Reference

- [Continuous deployment from a repository (Cloud Run)](https://cloud.google.com/run/docs/continuous-deployment)
- [Deploying to Cloud Run using Cloud Build](https://cloud.google.com/build/docs/deploying-builds/deploy-cloud-run)
- [ENV_SETUP.md](ENV_SETUP.md) — Supabase, Cloud Run env vars, GitHub secrets
