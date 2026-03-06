import { db } from '../db/database'
import { apiGet, apiPost } from '../services/apiClient'

/**
 * Local-first sync engine.
 * Operations are stored locally in IndexedDB and pushed to the backend when possible.
 * Triggers: app start, network online, after local writes, manual sync.
 */

const META_LAST_PULL = 'lastPullTimestamp'
const MAX_RETRIES = 5

async function getLastPullTimestamp(): Promise<number> {
  const row = await db.sync_meta.get(META_LAST_PULL)
  return row && typeof row.value === 'number' ? row.value : 0
}

async function setLastPullTimestamp(value: number): Promise<void> {
  await db.sync_meta.put({ key: META_LAST_PULL, value })
}

/**
 * Enqueue an operation for sync.
 * Called by createProject and logTime tools when data is written locally.
 */
export async function enqueueOperation(
  entityType: 'project' | 'time_log',
  entityId: string,
  operation: 'create' | 'update',
  payload: Record<string, unknown>
): Promise<void> {
  await db.sync_queue.add({
    entity_type: entityType,
    entity_id: entityId,
    operation,
    payload,
    created_at: new Date().toISOString(),
    retry_count: 0,
  })
}

/**
 * Push queued operations to the server.
 * Projects are sent before time_logs (FK dependency).
 * On success: remove from queue, update local sync_status.
 * On failure: increment retry_count; drop items exceeding MAX_RETRIES.
 */
export async function pushOperations(): Promise<void> {
  let items = await db.sync_queue.orderBy('created_at').toArray()

  // Remove items that exceeded max retries
  const toDelete = items.filter((i) => i.retry_count >= MAX_RETRIES)
  for (const item of toDelete) {
    if (item.id != null) await db.sync_queue.delete(item.id)
  }
  items = items.filter((i) => i.retry_count < MAX_RETRIES)
  if (items.length === 0) return

  // Projects must be pushed before time_logs (FK dependency)
  const sorted = [...items].sort((a, b) => {
    if (a.entity_type === b.entity_type) return 0
    return a.entity_type === 'project' ? -1 : 1
  })

  const operations = sorted.map((item) => ({
    entityType: item.entity_type,
    entityId: item.entity_id,
    operation: item.operation,
    payload: item.payload,
  }))

  try {
    const res = await apiPost<{ success: boolean; processedCount: number }>(
      '/sync/push',
      { operations }
    )
    if (res.success) {
      await db.sync_queue.clear()
      for (const op of sorted) {
        if (op.entity_type === 'project') {
          await db.projects.where('id').equals(op.entity_id).modify({ sync_status: 'synced' })
        } else if (op.entity_type === 'time_log') {
          await db.time_logs.where('id').equals(op.entity_id).modify({ sync_status: 'synced' })
        }
      }
    }
  } catch (e) {
    await incrementRetryCounts()
    throw e
  }
}

/**
 * Pull updates from the server since last pull.
 */
export async function pullUpdates(): Promise<void> {
  const since = await getLastPullTimestamp()
  const res = await apiGet<{
    projects: Array<{
      id: string
      name: string
      description?: string
      createdAt: string
      updatedAt: string
      userId?: string
    }>
    timeLogs: Array<{
      id: string
      projectId: string
      durationMinutes: number
      note?: string
      loggedAt: string
      updatedAt: string
      userId?: string
    }>
  }>(`/sync/pull?since=${since}`)

  let maxUpdated = since
  const projects = res.projects ?? []
  const timeLogs = res.timeLogs ?? []
  for (const p of projects) {
    await db.projects.put({
      id: p.id,
      name: p.name,
      description: p.description,
      created_at: p.createdAt,
      updated_at: p.updatedAt,
      sync_status: 'synced',
    })
    const ts = new Date(p.updatedAt).getTime()
    if (ts > maxUpdated) maxUpdated = ts
  }

  for (const t of timeLogs) {
    await db.time_logs.put({
      id: t.id,
      project_id: t.projectId,
      duration_minutes: t.durationMinutes,
      note: t.note,
      logged_at: t.loggedAt,
      sync_status: 'synced',
    })
    const ts = new Date(t.updatedAt).getTime()
    if (ts > maxUpdated) maxUpdated = ts
  }

  await setLastPullTimestamp(Math.max(maxUpdated, Date.now()))
}

/**
 * Process the sync queue: push then pull.
 */
export async function processQueue(): Promise<void> {
  try {
    await pushOperations()
    await pullUpdates()
  } catch (e) {
    console.warn('Sync failed:', e)
  }
}

/**
 * Increment retry count for all queued items after a push failure.
 */
async function incrementRetryCounts(): Promise<void> {
  const items = await db.sync_queue.toArray()
  for (const item of items) {
    if (item.id) {
      await db.sync_queue.update(item.id, { retry_count: item.retry_count + 1 })
    }
  }
}

/**
 * Initialize sync engine: process queue on startup.
 */
export async function initSyncEngine(): Promise<void> {
  await processQueue()
}
