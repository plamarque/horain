import { test, expect } from '@playwright/test'

/**
 * E2E: Agent trace UX.
 *
 * What we test (deterministic enough):
 * - Trace block appears after a response that used tools; main toggle "Outils utilisés" works.
 * - Turn/section hierarchy: we can expand a turn then a section.
 * - Natural-language descriptions are shown (e.g. "Récupération de la date", "Chargement...").
 * - Tool names remain visible in the detail (debug).
 * - Trace block can be collapsed (detail hidden).
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

  const traceRegion = page.getByRole('region', { name: 'Agent execution trace' })
  const traceToggle = traceRegion.getByRole('button', { name: /Outils utilisés/ })
  await expect(traceToggle).toBeVisible({ timeout: 5000 })

  await traceToggle.click()

  const turnToggle = traceRegion.locator('.agent-trace-turn-toggle').first()
  await expect(turnToggle).toBeVisible({ timeout: 3000 })
  await turnToggle.click()

  const sectionToggle = traceRegion.locator('.agent-trace-section-toggle').first()
  await expect(sectionToggle).toBeVisible({ timeout: 3000 })
  await sectionToggle.click()

  // New UX: at least one natural-language description visible (from agentTraceDescriptions)
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
  const traceToggle = traceRegion.getByRole('button', { name: /Outils utilisés/ })
  await expect(traceToggle).toBeVisible({ timeout: 15000 })

  await traceToggle.click()
  await expect(traceRegion.locator('.agent-trace-turn-toggle').first()).toBeVisible({ timeout: 3000 })

  await traceToggle.click()
  await expect(traceRegion.locator('.agent-trace-detail')).toBeHidden()
})
