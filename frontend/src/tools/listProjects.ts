import { db } from '../db/database'

/**
 * Tool: List all projects from local IndexedDB.
 * Used by conversation agent to match project names and check for existing projects.
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
