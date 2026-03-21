# ADR-0007 : Backend on Cloud Run instead of Render

## Context

The Horain backend was initially hosted on Render (ADR-0002). We want to change the deployment platform to Google Cloud Run while keeping a single, test-gated deployment: push to `main` runs tests, and only if they pass do we deploy both backend and frontend. The database remains on Supabase.

## Decision

- **Backend hosting:** Google Cloud Run (replacing Render).
- **Deployment:** Backend is deployed **from GitHub Actions** ([deploy.yml](../../.github/workflows/deploy.yml)) **only after** backend unit tests and e2e tests pass. The workflow runs `gcloud builds submit` with the repo [cloudbuild.yaml](../../cloudbuild.yaml), which builds the image from `backend/Dockerfile`, pushes to Artifact Registry, and runs `gcloud run deploy`. Authentication from GitHub to GCP uses Workload Identity Federation (no long-lived keys in GitHub). Any Cloud Build trigger that ran on push to `main` must be **disabled** so that the backend is only deployed via this workflow.
- **Database:** Unchanged; Supabase (PostgreSQL) remains the single data store.

## Consequences

- A single pipeline (deploy.yml) decides deployment: tests pass → backend (Cloud Run) and frontend build run; **GitHub Pages is published only after** the Cloud Run deployment succeeds. If the frontend build or Pages deploy fails after a successful backend deploy, **Cloud Run traffic is rolled back** to the previous revision so production does not keep a new API with an old UI. If tests fail, nothing is deployed.
- Backend listens on `PORT` (Cloud Run injects it; default 8080) via `server.port: ${PORT:8080}` in Spring Boot.
- Environment variables (Supabase JDBC, `HORAIN_API_KEY`, LLM keys) are configured on the Cloud Run service (or Secret Manager); see [CLOUD_RUN_SETUP.md](../CLOUD_RUN_SETUP.md) and [ENV_SETUP.md](../ENV_SETUP.md).
- Cost and cold-start behaviour depend on GCP pricing and instance settings; region choice (e.g. `europe-west1`) should align with Supabase for latency.
- Render can be retired after switching `VITE_API_URL` to the Cloud Run service URL and verifying production.
