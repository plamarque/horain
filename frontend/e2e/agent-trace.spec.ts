import { test, expect } from '@playwright/test'

/**
 * E2E: Agent trace UX.
 *
 * What we test (deterministic enough):
 * - Trace block appears after a response that used tools; we can expand tool detail.
 * - Natural-language descriptions are shown (e.g. "Récupération de la date", "Chargement...").
 * - Tool names remain visible in the detail (debug).
 * - Trace block tool row can be expanded then collapsed.
 *
 * What we do not test (non-deterministic or brittle):
 * - "Thinking..." during streaming (timing and visibility depend on LLM latency).
 * - Exact list of tools or order (model-dependent).
 * - Layout (full width, pixel dimensions).
 */

test('agent trace shows natural-language descriptions and tool detail after response', async ({
  page,
}) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await input.fill("What's the time?")
  await page.getByRole('button', { name: 'Send' }).click()

  const lastBubble = page.locator('.bubble.assistant').last()
  await expect(lastBubble).toBeVisible({ timeout: 15000 })
  await expect(lastBubble).not.toBeEmpty()

  // Trace region (aria-label "Agent execution trace"); then tool row button (single call or "N appels")
  const traceRegion = page.getByRole('region', { name: 'Agent execution trace' })
  await expect(traceRegion).toBeVisible({ timeout: 10000 })
  const traceToggle = traceRegion.getByRole('button', {
    name: /Récupération de la date|1 appel|\d+ appels/,
  }).first()
  await expect(traceToggle).toBeVisible({ timeout: 5000 })
  await traceToggle.click()

  // Detail (per-call expand) becomes visible
  await expect(traceRegion.locator('.agent-trace-item-detail').first()).toBeVisible({ timeout: 3000 })

  // Optional: expand turn if multiple calls (no-op for single call)
  const turnToggle = traceRegion.locator('.agent-trace-turn-toggle').first()
  if (await turnToggle.isVisible()) {
    await turnToggle.click({ force: true })
  }

  // Natural-language description visible (from agentTraceDescriptions)
  await expect(traceRegion).toContainText(
    /Récupération de la date|Chargement|Enregistrement|Préparation|Lecture|Écriture|Exploration|Suppression/i,
    { timeout: 3000 }
  )

  // Debug detail: at least one tool name still visible
  await expect(traceRegion).toContainText(
    /get_current_datetime|list_projects|create_time_log|search_project|get_recent_logs/i,
    { timeout: 2000 }
  )
})

test('agent trace block can be collapsed', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await input.fill("What's the time?")
  await page.getByRole('button', { name: 'Send' }).click()

  const traceRegion = page.getByRole('region', { name: 'Agent execution trace' })
  await expect(traceRegion).toBeVisible({ timeout: 15000 })
  const traceToggle = traceRegion.getByRole('button', {
    name: /Récupération de la date|1 appel|\d+ appels/,
  }).first()
  await expect(traceToggle).toBeVisible({ timeout: 5000 })

  await traceToggle.click()
  await expect(traceRegion.locator('.agent-trace-item-detail').first()).toBeVisible({ timeout: 3000 })

  await traceToggle.click()
  await expect(traceRegion.locator('.agent-trace-item-detail').first()).toBeHidden()
})
