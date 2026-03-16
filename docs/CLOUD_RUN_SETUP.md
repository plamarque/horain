# Google Cloud Run — Backend deployment guide

This guide explains how to deploy the Horain backend to Cloud Run. Deployment is **triggered from GitHub Actions** (workflow [deploy.yml](../.github/workflows/deploy.yml)) **only after tests pass** (backend unit tests + e2e). The database stays on Supabase; only the backend runtime runs on GCP.

**Unified pipeline:** Push to `main` runs tests; if they pass, both backend (Cloud Run) and frontend (GitHub Pages) are deployed. No separate Cloud Build trigger on push — that trigger must be disabled (see below).

---

## Prerequisites

- A Google Cloud project
- GitHub repository (Actions used for deploy; Workload Identity Federation for GCP auth)
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

When GitHub Actions runs `gcloud builds submit`, the build runs on Cloud Build. Ensure the **Cloud Build default service account** can deploy to Cloud Run and push to Artifact Registry:

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

## 4. Workload Identity Federation (GitHub Actions → GCP)

GitHub Actions needs to authenticate to GCP to run `gcloud builds submit`. We use **Workload Identity Federation** (no long-lived keys in GitHub).

### 4.1 Create a dedicated service account for GitHub Actions

1. **IAM & Admin** → **Service Accounts** → **Create**.
2. **Name:** e.g. `github-actions-deploy`
3. **Grant this service account access to project:** skip (we will grant roles next).

Then grant the following roles to the GitHub Actions service account (at project level):

- **Cloud Build Editor** (`roles/cloudbuild.builds.editor`) — create and submit builds.
- **Service Account User** (`roles/iam.serviceAccountUser`) — so Cloud Build can run as the default Cloud Build SA.
- **Storage Admin** (`roles/storage.admin`) — required so the SA can upload source to the default Cloud Build bucket (`PROJECT_ID_cloudbuild`) when running `gcloud builds submit`.
- **Service Usage Consumer** (`roles/serviceusage.serviceUsageConsumer`) — required for `serviceusage.services.use`; without it you get "The user is forbidden from accessing the bucket [***_cloudbuild]".

Optional: if you prefer the GitHub Actions SA to trigger builds without the default Cloud Build SA having broad roles, you can use **Cloud Build Service Account** and ensure that account has Run Admin + Artifact Registry Writer. The standard setup is: GitHub SA can submit builds; the **default Cloud Build SA** (used when the build runs) has Run Admin + Artifact Registry Writer (section 3).

### 4.2 Create Workload Identity Pool and Provider

1. **IAM & Admin** → **Workload Identity Federation** → **Create pool**.
2. **Pool name:** e.g. `github-pool`
3. **Provider:** Add provider → **OpenID Connect (OIDC)**.
   - **Provider name:** e.g. `github`
   - **Issuer (URL):** `https://token.actions.githubusercontent.com`
   - **Audience:** leave default or set to your repo URL if you use a custom audience.
4. **Attribute mapping:** add:
   - `google.subject` = `assertion.sub`
   - `attribute.actor` = `assertion.actor`
   - `attribute.repository` = `assertion.repository`
5. **Attribute condition (obligatoire dans la console):** Le champ ne peut pas rester vide. La condition **doit référencer au moins un claim** du jeton OIDC avec le préfixe `assertion.`. Deux possibilités :
   - **Accepter tous les jetons GitHub (pas de restriction)** : utilisez une condition toujours vraie qui référence un claim présent dans tout jeton (ex. `sub`) :
     ```text
     assertion.sub != ''
     ```
   - **Restreindre par org ou branche** : ex. `assertion.repository_owner=='YOUR_GITHUB_ORG'` ou `assertion.repository_owner=='YOUR_GITHUB_ORG' && assertion.ref=='refs/heads/main'`.
   Claims utiles du [jeton OIDC GitHub](https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/about-security-hardening-with-openid-connect#understanding-the-oidc-token) : `assertion.sub`, `assertion.repository`, `assertion.repository_owner`, `assertion.ref`.
   **Erreur fréquente :** "The attribute condition must reference one of the provider's claims" → n’utiliser que `assertion.<claim>` avec un nom de claim réel.
6. Save the pool and provider.

### 4.3 Allow GitHub repo to impersonate the service account

1. **IAM & Admin** → **IAM**.
2. **Grant access** → **Add principal**: the service account email (e.g. `github-actions-deploy@PROJECT_ID.iam.gserviceaccount.com`).
3. **Role:** **Workload Identity User** is not a role for the SA; we need to grant the **pool’s identity** the right to impersonate the SA.
4. Run in Cloud Shell (replace placeholders):

```bash
gcloud iam service-accounts add-iam-policy-binding \
  github-actions-deploy@PROJECT_ID.iam.gserviceaccount.com \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/attribute.repository/OWNER/REPO"
```

Use your GitHub org/user as `OWNER` and repo name as `REPO`. For a single branch (e.g. `main` only), you can use `attribute.repository_owner` or a more restrictive principal set; see [Google Cloud docs](https://cloud.google.com/iam/docs/workload-identity-federation#restrict).

### 4.4 GitHub secrets and variables

In the repo: **Settings** → **Secrets and variables** → **Actions**. Add:

| Name | Kind | Value |
|------|------|--------|
| `GCP_PROJECT_ID` | Secret or variable | Your GCP project ID |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | Secret | Full provider resource name, e.g. `projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/providers/github` |
| `GCP_SERVICE_ACCOUNT` | Secret | Service account email, e.g. `github-actions-deploy@PROJECT_ID.iam.gserviceaccount.com` |
| `GCP_REGION` | Secret or variable | e.g. `europe-west1` |
| `GCP_SERVICE_NAME` | Secret or variable | Cloud Run service name, e.g. `horain-api` |
| `GCP_ARTIFACT_REPOSITORY` | Secret or variable | Artifact Registry repo name, e.g. `horain` |

The workflow [deploy.yml](../.github/workflows/deploy.yml) uses these to authenticate and pass substitutions to [cloudbuild.yaml](../cloudbuild.yaml).

---

## 5. Disable the old “push to main” trigger

If you previously had a Cloud Build trigger that ran on push to `main`, **disable or delete it** so that the backend is only deployed from GitHub Actions after tests pass.

1. Go to [Cloud Build Triggers](https://console.cloud.google.com/cloud-build/triggers).
2. Find the trigger that runs on branch `^main$` (e.g. `horain-backend-deploy`).
3. **Disable** it (or delete it). From now on, deployment is only triggered by the **Deploy to Production** workflow on push to `main` (tests run first, then `gcloud builds submit`).

---

## 6. First deployment and environment variables

If you created the service from the console (Option A), the first revision may run with no env vars. Add them before or right after the first deploy.

1. **Cloud Run** → your service (`horain-api`) → **Edit & deploy new revision**.
2. **Variables and secrets** → add:

| Key | Value |
|-----|-------|
| `SPRING_PROFILES_ACTIVE` | `postgres` |
| `SPRING_DATASOURCE_URL` | **Session** (port 5432), copy and replace host with yours: `jdbc:postgresql://aws-1-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require` (host from Supabase → Project Settings → Database). **Transaction** (port 6543): `jdbc:postgresql://HOST:6543/postgres?sslmode=require&prepareThreshold=0`. Do not omit `:5432` or `/postgres`; `?sslmode=require` must be after the path. |
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

## 7. Secrets (recommended)

For `SPRING_DATASOURCE_PASSWORD` and API keys, use Secret Manager:

1. Create a secret in **Secret Manager** with the value.
2. In Cloud Run → **Edit & deploy new revision** → **Variables and secrets** → **Reference a secret**.
3. Select the secret and choose the env var name (e.g. `SPRING_DATASOURCE_PASSWORD`).

When using `gcloud run deploy` (e.g. in a custom build step), use `--set-secrets=SPRING_DATASOURCE_PASSWORD=db-password:latest`.

---

## 8. Backend URL and GitHub Actions

After the first successful deployment:

1. In Cloud Run, copy the **Service URL** (e.g. `https://horain-api-xxxxx-ew.a.run.app`).
2. In GitHub: **Settings** → **Secrets and variables** → **Actions** → set (or update) **VITE_API_URL** to this URL.
3. **VITE_API_KEY** must match **HORAIN_API_KEY** on Cloud Run.

The next run of the **Deploy to Production** workflow (push to `main` after tests pass) will build the frontend with this backend URL.

---

## 9. Rollback / coexistence with Render

- As long as **VITE_API_URL** in GitHub points to Render, the app keeps using the Render backend.
- To switch: configure Cloud Run and WIF (section 4), add GitHub secrets, run the deploy workflow once, set env vars on Cloud Run, then update **VITE_API_URL** to the Cloud Run URL.
- To roll back: set **VITE_API_URL** back to the Render URL and redeploy the frontend (or leave Render running and just switch the secret).

---

## 10. Request timeout (streaming)

The chat stream endpoint (`POST /chat/message/stream`) keeps the connection open while the agent may run several tool-call rounds. Cloud Run allows configuring the **request timeout** (default is 5 minutes). If users hit timeouts on long conversations, in **Cloud Run** → your service → **Edit & deploy new revision** → **Container** → set **Request timeout** to e.g. 300 seconds (or leave the default). The backend uses a 5-minute timeout for the SSE emitter.

---

## 11. Troubleshooting: "Container failed to start and listen on PORT"

If the revision fails with *"The user-provided container failed to start and listen on the port defined by the PORT=8080 environment variable"*:

1. **Check Cloud Logging** (link in the error, or **Observability** → **Logs**): look for Java stack traces, Flyway errors, or database connection failures. The app may be crashing before binding to the port (e.g. missing `SPRING_PROFILES_ACTIVE=postgres`, wrong `SPRING_DATASOURCE_*`, or DB unreachable).

2. **Verify service env vars** (Cloud Run → your service → **Edit & deploy** → **Variables and secrets**): at least `SPRING_PROFILES_ACTIVE=postgres`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`. Without these, the app can fail on startup. If you see **"MaxClientsInSessionMode: max clients reached"**, switch to Supabase **Transaction** pooler (port 6543): in Supabase **Project Settings → Database**, choose **Transaction pooler**, copy the JDBC URL, and append `?prepareThreshold=0` (or `&prepareThreshold=0` if the URL already has params). Then set that full URL in `SPRING_DATASOURCE_URL`. You can then increase Hikari pool size if needed (default remains 1 for Session mode).

3. **Port and binding**: The repo is configured so the backend listens on `0.0.0.0` and `PORT` (default 8080). The build uses `--port=8080` and `--cpu-boost` when deploying via the repo `cloudbuild.yaml`. If you use **Cloud Run "Connect repository"** (source deploy), configure the service with port 8080 and CPU boost in the Cloud Run console (Edit & deploy → Container → Port, and enable startup CPU boost). Ensure the container listens on all interfaces.

4. **Startup timeout**: Cloud Run allows up to 240 seconds for the container to listen on the port. The app uses **lazy initialization** in the `postgres` profile so the HTTP server binds to the port before DataSource/Flyway run (they initialize on first request). If the container still fails, run the image locally to see the real error: `docker run --rm -e PORT=8080 -e SPRING_PROFILES_ACTIVE=postgres -e SPRING_DATASOURCE_URL=... -e SPRING_DATASOURCE_USERNAME=... -e SPRING_DATASOURCE_PASSWORD=... -p 8080:8080 YOUR_IMAGE` (use the same env as Cloud Run).

See [Cloud Run troubleshooting](https://cloud.google.com/run/docs/troubleshooting#container-failed-to-start) for more.

---

## 12. Troubleshooting: Cannot connect to Supabase

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

   Names must be exactly **`SPRING_DATASOURCE_URL`** (with the `SPRING_` prefix). A typo or wrong name (e.g. `DATABASE_URL`) means the app falls back to the default in code, which is **localhost**. In the container there is no Postgres on localhost, so you get a connection timeout instead of an auth error. Check the revision’s **Variables and secrets** and fix the names if needed. If you see **EOFException** or "Pool is empty, failed to create/setup connection" during auth, add **`?sslmode=require`** to the JDBC URL (Supabase pooler requires SSL). If you see **"Circuit breaker open: Too many authentication errors"**, Supabase has temporarily blocked connections after too many failed logins; fix the username (use `postgres.PROJECT_REF` from Supabase, not just `postgres`) and password, then wait 15–30 minutes before retrying.

6. **See which URL is actually used**  
   The app logs the resolved datasource URL at startup (password redacted), e.g. `Datasource URL (postgres profile): jdbc:postgresql://aws-1-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require`. In Cloud Run → **Observability** → **Logs**, check the first lines of a new revision: if you see `localhost:5432`, the env var `SPRING_DATASOURCE_URL` is not set or not applied. If you see the Supabase host but still get EOFException or connection errors, the issue is SSL, credentials, or Supabase-side.

7. **Enable debug for one deploy**  
   To get the full "condition evaluation report" and stack trace when the context fails to start, add a variable **`DEBUG`** = **`true`** to the Cloud Run revision (Variables and secrets). Redeploy, then check the logs for the detailed report. Remove `DEBUG` after troubleshooting.

8. **"Connection is not available, request timed out" (no auth error)**  
   If logs show a **timeout** (e.g. HikariCP "request timed out after 15001ms") and not "password authentication failed", outbound traffic from Cloud Run to Supabase may be blocked:

   - **Cloud Run** → your service → **Edit & deploy new revision** → open **Networking** (or **Connections**, **Security**, **Container** depending on console layout).
   - Check **VPC / Egress**:
     - If a **VPC connector** is set or **Direct VPC egress** is enabled with **"Private ranges only"**, all egress goes through the VPC. Without a route to the internet (e.g. Cloud NAT), traffic to Supabase (public host) will hang and time out.
     - **Fix:** Either **remove the VPC connector** and use default egress (traffic goes to the internet directly), or set egress to **"All traffic"** so that only private IP ranges use the VPC and public IPs (Supabase) use the default internet path. Do not use "Private ranges only" if the only external service is Supabase on the public internet.
   - Redeploy after changing the setting.

---

## Reference

- [.github/workflows/deploy.yml](../.github/workflows/deploy.yml) — single entry point for production deploy (tests then backend + frontend)
- [Continuous deployment from a repository (Cloud Run)](https://cloud.google.com/run/docs/continuous-deployment)
- [Deploying to Cloud Run using Cloud Build](https://cloud.google.com/build/docs/deploying-builds/deploy-cloud-run)
- [Workload Identity Federation with GitHub Actions](https://cloud.google.com/iam/docs/workload-identity-federation-with-other-providers#github-actions)
- [ENV_SETUP.md](ENV_SETUP.md) — Supabase, Cloud Run env vars, GitHub secrets
