import { db } from '../db/database'

/**
 * Tool: List all projects from local IndexedDB.
 * Used by sync and any local-first flows that need project lookup.
 */
export async function listProjects(): Promise<
  Array<{ id: string; name: string; description?: string }>
> {
  const projects = await db.projects.toArray()
  return projects.map((p) => ({
    id: p.id,
    name: p.name,
    description: p.description,
  }))
}
