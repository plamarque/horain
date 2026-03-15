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
| `SPRING_DATASOURCE_URL` | JDBC URL from Supabase (Session Pooler), e.g. `jdbc:postgresql://aws-1-eu-west-1.pooler.supabase.com:5432/postgres` |
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

2. **Verify service env vars** (Cloud Run → your service → **Edit & deploy** → **Variables and secrets**): at least `SPRING_PROFILES_ACTIVE=postgres`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`. Without these, the app can fail on startup.

3. **Port and binding**: The repo is configured so the backend listens on `0.0.0.0` and `PORT` (default 8080). The build uses `--port=8080` and `--cpu-boost` to speed startup. If you deploy with another method, ensure the container listens on all interfaces and on the port provided by Cloud Run.

4. **Startup timeout**: Cloud Run allows up to 240 seconds for the container to listen on the port. If the app is slow (e.g. many Flyway migrations, slow DB), ensure the Supabase Session Pooler URL is used and consider increasing CPU/memory for the service so startup finishes in time.

See [Cloud Run troubleshooting](https://cloud.google.com/run/docs/troubleshooting#container-failed-to-start) for more.

---

## Reference

- [Continuous deployment from a repository (Cloud Run)](https://cloud.google.com/run/docs/continuous-deployment)
- [Deploying to Cloud Run using Cloud Build](https://cloud.google.com/build/docs/deploying-builds/deploy-cloud-run)
- [ENV_SETUP.md](ENV_SETUP.md) — Supabase, Cloud Run env vars, GitHub secrets
