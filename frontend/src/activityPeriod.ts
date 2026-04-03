/**
 * Shared activity window [activityFrom, activityTo) for projects list and recent logs (half-open, matches backend).
 */

export type ActivityPeriodPreset =
  | 'rolling_28d'
  | 'rolling_7d'
  | 'calendar_month'
  | 'calendar_prev_month'
  | 'custom'

export interface ActivityPeriodCustom {
  fromYmd: string
  toYmd: string
}

const DAY_MS = 86400000

function startOfLocalDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate(), 0, 0, 0, 0)
}

function addLocalDays(d: Date, days: number): Date {
  const x = new Date(d.getTime())
  x.setDate(x.getDate() + days)
  return x
}

/** Parse yyyy-mm-dd as local midnight. */
export function parseLocalYmd(ymd: string): Date | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(ymd.trim())
  if (!m) return null
  const y = Number(m[1])
  const mo = Number(m[2])
  const d = Number(m[3])
  const dt = new Date(y, mo - 1, d, 0, 0, 0, 0)
  if (dt.getFullYear() !== y || dt.getMonth() !== mo - 1 || dt.getDate() !== d) return null
  return dt
}

export function computeActivityRange(
  preset: ActivityPeriodPreset,
  custom: ActivityPeriodCustom | null
): { activityFrom: string; activityTo: string } {
  const now = new Date()

  switch (preset) {
    case 'rolling_28d': {
      const from = new Date(now.getTime() - 28 * DAY_MS)
      return { activityFrom: from.toISOString(), activityTo: now.toISOString() }
    }
    case 'rolling_7d': {
      const from = new Date(now.getTime() - 7 * DAY_MS)
      return { activityFrom: from.toISOString(), activityTo: now.toISOString() }
    }
    case 'calendar_month': {
      const start = new Date(now.getFullYear(), now.getMonth(), 1, 0, 0, 0, 0)
      return { activityFrom: start.toISOString(), activityTo: now.toISOString() }
    }
    case 'calendar_prev_month': {
      const y = now.getFullYear()
      const m = now.getMonth()
      const start = new Date(y, m - 1, 1, 0, 0, 0, 0)
      const end = new Date(y, m, 1, 0, 0, 0, 0)
      return { activityFrom: start.toISOString(), activityTo: end.toISOString() }
    }
    case 'custom': {
      const c = custom ?? { fromYmd: '', toYmd: '' }
      const a = parseLocalYmd(c.fromYmd)
      const b = parseLocalYmd(c.toYmd)
      if (!a || !b) {
        const from = new Date(now.getTime() - 28 * DAY_MS)
        return { activityFrom: from.toISOString(), activityTo: now.toISOString() }
      }
      let fromDay = startOfLocalDay(a)
      let toExclusive = addLocalDays(startOfLocalDay(b), 1)
      if (fromDay.getTime() >= toExclusive.getTime()) {
        toExclusive = addLocalDays(fromDay, 1)
      }
      return { activityFrom: fromDay.toISOString(), activityTo: toExclusive.toISOString() }
    }
    default:
      return computeActivityRange('rolling_28d', null)
  }
}

export function formatActivityPeriodSummary(
  preset: ActivityPeriodPreset,
  custom: ActivityPeriodCustom | null,
  locale = 'en-GB'
): string {
  const { activityTo } = computeActivityRange(preset, custom)
  const endDate = new Date(activityTo)
  const endStr = endDate.toLocaleDateString(locale, { day: 'numeric', month: 'short' })

  switch (preset) {
    case 'rolling_28d':
      return `28d · ${endStr}`
    case 'rolling_7d':
      return `7d · ${endStr}`
    case 'calendar_month':
      return `Month · ${endStr}`
    case 'calendar_prev_month':
      return 'Prev. month'
    case 'custom': {
      const c = custom
      if (c?.fromYmd && c?.toYmd) return `${c.fromYmd} → ${c.toYmd}`
      return `28d · ${endStr}`
    }
    default:
      return endStr
  }
}
