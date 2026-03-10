/**
 * API client for Horain backend.
 * All requests include the API key header (Authorization: Bearer <API_KEY>).
 *
 * Endpoints: POST /sync/push, GET /sync/pull, GET /projects, POST /projects,
 * POST /time-logs
 *
 * VITE_API_URL:
 *   - Empty or unset: use /api (Vite proxy, works from smartphone on same network)
 *   - http://localhost:8080: direct to backend (desktop only)
 *   - https://...: production
 */
const RAW_API_URL = (import.meta.env.VITE_API_URL || '').trim()
const API_BASE = RAW_API_URL || '/api' // Empty = use Vite proxy
const API_KEY = import.meta.env.VITE_API_KEY || 'HORAIN_DEV_KEY'

function headers(): HeadersInit {
  return {
    'Content-Type': 'application/json',
    Accept: 'application/json',
    Authorization: `Bearer ${API_KEY}`,
  }
}

export async function apiFetch<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const p = path.startsWith('/') ? path : `/${path}`
  const url = `${API_BASE.replace(/\/$/, '')}${p}`
  const res = await fetch(url, {
    ...options,
    headers: {
      ...headers(),
      ...(options.headers as Record<string, string>),
    },
  })
  if (!res.ok) {
    const text = await res.text()
    let detail = ''
    try {
      const json = text ? (JSON.parse(text) as Record<string, unknown>) : null
      if (json && typeof json.message === 'string') detail = `: ${json.message}`
      else if (json && typeof json.error === 'string') detail = `: ${json.error}`
    } catch {
      /* ignore parse errors */
    }
    throw new Error(`API error ${res.status}${detail || ` ${res.statusText}`}`)
  }
  const text = await res.text()
  if (!text) return {} as T
  return JSON.parse(text) as T
}

export async function apiPost<T>(
  path: string,
  body: unknown,
  init?: Omit<RequestInit, 'method' | 'body'>
): Promise<T> {
  return apiFetch<T>(path, {
    ...init,
    method: 'POST',
    body: JSON.stringify(body),
  })
}

/** Get URL and headers for a streaming request (e.g. SSE). Caller must use fetch and read body as stream. */
export function getStreamRequestConfig(path: string): { url: string; headers: HeadersInit } {
  const p = path.startsWith('/') ? path : `/${path}`
  const url = `${API_BASE.replace(/\/$/, '')}${p}`
  return {
    url,
    headers: { ...headers(), Accept: 'text/event-stream' },
  }
}

export async function apiGet<T>(path: string): Promise<T> {
  return apiFetch<T>(path)
}

export async function apiPatch<T>(path: string, body: unknown): Promise<T> {
  return apiFetch<T>(path, {
    method: 'PATCH',
    body: JSON.stringify(body),
  })
}

export async function apiDelete<T>(path: string): Promise<T> {
  return apiFetch<T>(path, { method: 'DELETE' })
}

// --- Backend endpoint wrappers ---

export interface ProjectDto {
  id: string
  name: string
  description?: string
  billable?: boolean
  createdAt: string
  updatedAt: string
  userId?: string
}

export interface TimeLogDto {
  id: string
  projectId: string
  durationMinutes: number
  note?: string
  billable?: boolean
  loggedAt: string
  createdAt?: string
  updatedAt: string
  userId?: string
}

/** GET /projects - list all projects */
export async function getProjects(): Promise<ProjectDto[]> {
  return apiGet<ProjectDto[]>('/projects')
}

/** POST /projects - create a project */
export async function createProjectViaApi(body: {
  id?: string
  name: string
  description?: string
}): Promise<ProjectDto> {
  return apiPost<ProjectDto>('/projects', body)
}

/** PATCH /projects/:id - update a project (partial) */
export async function updateProject(
  id: string,
  patch: { name?: string; description?: string; billable?: boolean }
): Promise<ProjectDto> {
  return apiPatch<ProjectDto>(`/projects/${id}`, patch)
}

/** POST /time-logs - create a time log */
export async function createTimeLogViaApi(body: {
  id?: string
  projectId: string
  durationMinutes: number
  note?: string
  loggedAt?: string
}): Promise<TimeLogDto> {
  return apiPost<TimeLogDto>('/time-logs', body)
}

/** PATCH /time-logs/:id - update a time log (partial) */
export async function updateTimeLog(
  id: string,
  patch: {
    projectId?: string
    durationMinutes?: number
    note?: string
    billable?: boolean
    loggedAt?: string
  }
): Promise<TimeLogDto> {
  return apiPatch<TimeLogDto>(`/time-logs/${id}`, patch)
}

/** DELETE /time-logs/:id - delete a time log */
export async function deleteTimeLog(id: string): Promise<void> {
  return apiDelete<void>(`/time-logs/${id}`)
}

/** GET /time-logs/recent - list most recent time logs with project names (for initial display) */
export async function getRecentTimeLogs(limit = 5): Promise<
  Array<{
    id: string
    projectId: string
    projectName?: string
    durationMinutes: number
    note?: string
    billable?: boolean
    loggedAt: string
    createdAt?: string
  }>
> {
  const safeLimit = Math.min(Math.max(limit, 1), 50)
  return apiGet(`/time-logs/recent?limit=${safeLimit}`)
}

/** POST /dev/seed - load fictional seed data (dev only, when backend enables it) */
export async function loadDevSeed(): Promise<{ projectsCreated: number; timeLogsCreated: number }> {
  return apiPost<{ projectsCreated: number; timeLogsCreated: number }>('/dev/seed', {})
}

/** POST /dev/seed/reset - clear DB and reload seed (dev only) */
export async function resetDevSeed(): Promise<{ projectsCreated: number; timeLogsCreated: number }> {
  return apiPost<{ projectsCreated: number; timeLogsCreated: number }>('/dev/seed/reset', {})
}

/** POST /sync/push - push queued operations (used by sync engine) */
export async function syncPush(body: {
  operations: Array<{
    entityType: string
    entityId: string
    operation: string
    payload: Record<string, unknown>
  }>
}): Promise<{ success: boolean; processedCount: number }> {
  return apiPost('/sync/push', body)
}
