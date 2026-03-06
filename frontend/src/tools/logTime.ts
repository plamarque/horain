import { db } from '../db/database'
import { enqueueOperation } from '../sync/syncEngine'

/**
 * Generate a UUID v4 on the client.
 */
function uuid(): string {
  return crypto.randomUUID()
}

/**
 * Tool: Log time locally and enqueue for sync.
 */
export async function logTime(
  projectId: string,
  durationMinutes: number,
  note?: string
): Promise<{ id: string; projectId: string; durationMinutes: number; note?: string }> {
  const id = uuid()
  const now = new Date().toISOString()

  await db.time_logs.add({
    id,
    project_id: projectId,
    duration_minutes: durationMinutes,
    note: note || '',
    logged_at: now,
    sync_status: 'pending',
  })

  await enqueueOperation('time_log', id, 'create', {
    projectId,
    durationMinutes,
    note: note || null,
    loggedAt: now,
    userId: null,
  })

  return { id, projectId, durationMinutes, note }
}
