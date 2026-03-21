# ADR: Optional external agent observability (LangSmith, OTLP)

## Status

Accepted

## Context

Horain persists agent turns in PostgreSQL (`agent_turn`) and exposes feedback via REST. Product evaluation uses Promptfoo in-repo. We want optional export to third-party platforms (starting with LangSmith) without replacing the built-in trace.

## Decision

1. **Source of truth:** Database tables `agent_turn` and `agent_feedback` remain authoritative for product behavior and the eval pipeline.
2. **Pluggable sink:** `AgentTraceSink` receives `TurnCompletedEvent` and `FeedbackEvent` after successful persistence. Default implementation is a no-op; LangSmith sends runs and feedback asynchronously (best-effort).
3. **External reference:** `agent_turn.external_trace_id` stores the platform run id when export succeeds (e.g. LangSmith run UUID).
4. **OpenTelemetry:** Micrometer `Tracer` + OTLP export (when enabled) adds spans tagged with turn/conversation/model for comparison with REST-based export; sampling defaults to 0.

## Consequences

- Operators must set `HORAIN_OBSERVABILITY_PROVIDER` and `LANGCHAIN_API_KEY` (and project/endpoint) to enable LangSmith; otherwise no outbound calls.
- Sensitive message content may leave the deployment when LangSmith is enabled; restrict by environment and review payload fields periodically.
