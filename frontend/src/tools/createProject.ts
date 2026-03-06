import { db } from '../db/database'
import { enqueueOperation } from '../sync/syncEngine'

/**
 * Generate a UUID v4 on the client.
 */
function uuid(): string {
  return crypto.randomUUID()
}

/**
 * Tool: Create a project locally and enqueue for sync.
 */
export async function createProject(
  name: string,
  description?: string
): Promise<{ id: string; name: string }> {
  const id = uuid()
  const now = new Date().toISOString()

  await db.projects.add({
    id,
    name,
    description: description || '',
    created_at: now,
    updated_at: now,
    sync_status: 'pending',
  })

  await enqueueOperation('project', id, 'create', {
    name,
    description: description || null,
    userId: null,
  })

  return { id, name }
}
