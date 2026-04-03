/**
 * Shared card background colors for projects and time-log entries (must stay aligned with backend ProjectService.CARD_PALETTE_SIZE).
 */
export const PROJECT_CARD_COLORS = [
  '#4a6edb',
  '#5a8a4a',
  '#c9a227',
  '#c45c3a',
  '#7b5fa2',
  '#00838f',
  '#b91c1c',
  '#047857',
  '#b45309',
  '#6d28d9',
  '#be185d',
  '#0e7490',
] as const

export const PROJECT_CARD_PALETTE_LENGTH = PROJECT_CARD_COLORS.length

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function isUuidLike(value: string): boolean {
  return UUID_RE.test(value.trim())
}

function hashStringToPaletteIndex(key: string): number {
  let h = 0
  for (let i = 0; i < key.length; i++) h = (h << 5) - h + key.charCodeAt(i)
  return Math.abs(h) % PROJECT_CARD_COLORS.length
}

/**
 * Palette index from project id (preferred, case-insensitive) or name; 0 if no usable key.
 */
export function hashPaletteIndex(projectId?: string | null, projectName?: string | null): number {
  const id = projectId?.trim() ?? ''
  if (id && isUuidLike(id)) return hashStringToPaletteIndex(id.toLowerCase())
  const name = projectName?.trim() ?? ''
  if (name) return hashStringToPaletteIndex(name)
  return 0
}

function normalizeStoredIndex(index: number): number {
  const n = PROJECT_CARD_COLORS.length
  return ((Math.trunc(index) % n) + n) % n
}

/**
 * Resolved palette index: explicit server index wins, else hash from id/name.
 */
export function effectivePaletteIndex(
  projectId?: string | null,
  projectName?: string | null,
  cardColorIndex?: number | null
): number {
  if (cardColorIndex != null && Number.isFinite(cardColorIndex)) {
    return normalizeStoredIndex(Number(cardColorIndex))
  }
  return hashPaletteIndex(projectId, projectName)
}

const NEUTRAL_CARD_COLOR = 'rgba(0,0,0,0.25)'

export function projectCardBackgroundColor(
  projectId?: string | null,
  projectName?: string | null,
  cardColorIndex?: number | null
): string {
  if (cardColorIndex != null && Number.isFinite(cardColorIndex)) {
    return PROJECT_CARD_COLORS[normalizeStoredIndex(Number(cardColorIndex))]
  }
  const id = projectId?.trim() ?? ''
  const name = projectName?.trim() ?? ''
  if (!id && !name) return NEUTRAL_CARD_COLOR
  if (id && isUuidLike(id)) {
    return PROJECT_CARD_COLORS[hashStringToPaletteIndex(id.toLowerCase())]
  }
  if (name) return PROJECT_CARD_COLORS[hashStringToPaletteIndex(name)]
  return NEUTRAL_CARD_COLOR
}

export function nextCardColorIndex(
  projectId: string,
  projectName: string,
  cardColorIndex?: number | null
): number {
  const current = effectivePaletteIndex(projectId, projectName, cardColorIndex ?? null)
  return (current + 1) % PROJECT_CARD_PALETTE_LENGTH
}
