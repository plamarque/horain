# ADR-0007 : Backend on Cloud Run instead of Render

## Context

The Horain backend was initially hosted on Render (ADR-0002). We want to change the deployment platform to Google Cloud Run while keeping the same workflow: a simple git push to `main` triggers backend deployment. The database remains on Supabase.

## Decision

- **Backend hosting:** Google Cloud Run (replacing Render).
- **Deployment:** Cloud Build trigger on push to `main`; build uses the existing `backend/Dockerfile`, push to Artifact Registry, then `gcloud run deploy`. No GCP secrets in GitHub; credentials stay in the GCP project.
- **Database:** Unchanged; Supabase (PostgreSQL) remains the single data store.

## Consequences

- Push to `main` still deploys the backend (via Cloud Build trigger), and the frontend (via GitHub Actions) as before.
- Backend listens on `PORT` (Cloud Run injects it; default 8080) via `server.port: ${PORT:8080}` in Spring Boot.
- Environment variables (Supabase JDBC, `HORAIN_API_KEY`, LLM keys) are configured on the Cloud Run service (or Secret Manager); see [CLOUD_RUN_SETUP.md](../CLOUD_RUN_SETUP.md) and [ENV_SETUP.md](../ENV_SETUP.md).
- Cost and cold-start behaviour depend on GCP pricing and instance settings; region choice (e.g. `europe-west1`) should align with Supabase for latency.
- Render can be retired after switching `VITE_API_URL` to the Cloud Run service URL and verifying production.
