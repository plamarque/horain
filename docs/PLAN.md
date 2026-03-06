# Plan

## Current phase

Slice 1 — Bootstrap front and minimal conversation UI.

## Slices

| Slice | Objective | Status |
|-------|-----------|--------|
| 0 | Documentation governance | Done |
| 1 | Bootstrap front (Vue 3, PrimeVue, Vite) + minimal conversation UI | To do |
| 2 | MCP Server + tools + Supabase | To do |
| 3 | Backend agent + MCP client integration | To do |
| 4 | Voice (push-to-talk, STT) | To do |
| 5 | Full flow + e2e tests + CI/CD | To do |
| 6 | PWA + stores (optional) | To do |

## Tasks (Slice 1)

- [ ] Create Vite + Vue 3 + PrimeVue project
- [ ] Implement minimal conversation UI (thread, messages)
- [ ] Push-to-talk button (mock/placeholder)
- [ ] Layout and styling (mobile-first)

## Tasks (Slice 2)

- [ ] MCP Server implementation with tools
- [ ] Supabase setup (projects, time_logs)
- [ ] list_projects, search_project, create_project, log_time, list_recent_logs

## Tasks (Slice 3)

- [ ] Backend agent runtime (e.g. Spring AI)
- [ ] MCP client integration
- [ ] Agent orchestration and tool calling

## Tasks (Slice 4)

- [ ] Speech-to-text integration
- [ ] Push-to-talk wiring
- [ ] Transcript → agent flow

## Tasks (Slice 5)

- [ ] End-to-end flow: voice → transcript → agent → tools → confirmation
- [ ] Playwright e2e tests
- [ ] GitHub Actions CI/CD (build, test, deploy)

## Tasks (Slice 6)

- [ ] PWA manifest and service worker
- [ ] Store packaging (optional, see PUBLISHING_STORES.md)
