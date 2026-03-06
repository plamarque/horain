import Dexie from 'dexie'

/**
 * Project record in IndexedDB.
 */
export interface ProjectRecord {
  id: string
  name: string
  description?: string
  created_at: string
  updated_at: string
  sync_status: 'pending' | 'synced' | 'error'
}

/**
 * Time log record in IndexedDB.
 */
export interface TimeLogRecord {
  id: string
  project_id: string
  duration_minutes: number
  note?: string
  logged_at: string
  sync_status: 'pending' | 'synced' | 'error'
}

/**
 * Sync queue entry for outgoing operations.
 */
export interface SyncQueueRecord {
  id?: number
  entity_type: 'project' | 'time_log'
  entity_id: string
  operation: 'create' | 'update'
  payload: Record<string, unknown>
  created_at: string
  retry_count: number
}

/**
 * Sync metadata (e.g. lastPullTimestamp) persisted across sessions.
 */
export interface SyncMetaRecord {
  key: string
  value: number | string
}

/**
 * Dexie database for Horain local-first storage.
 */
export class HorainDb extends Dexie {
  projects!: Dexie.Table<ProjectRecord, string>
  time_logs!: Dexie.Table<TimeLogRecord, string>
  sync_queue!: Dexie.Table<SyncQueueRecord, number>
  sync_meta!: Dexie.Table<SyncMetaRecord, string>

  constructor() {
    super('HorainDb')
    this.version(1).stores({
      projects: 'id, name, updated_at, sync_status',
      time_logs: 'id, project_id, logged_at, sync_status',
      sync_queue: '++id, entity_type, entity_id, created_at',
    })
    this.version(2).stores({
      projects: 'id, name, updated_at, sync_status',
      time_logs: 'id, project_id, logged_at, sync_status',
      sync_queue: '++id, entity_type, entity_id, created_at',
      sync_meta: 'key',
    })
  }
}

export const db = new HorainDb()
