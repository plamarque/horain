import { test, expect } from '@playwright/test'

/**
 * E2E: Agent trace — after an assistant response that used tools,
 * the trace block is present, can be expanded, and shows at least one tool name.
 */
test('agent trace shows tool names after response', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  // Send a message that typically triggers a tool (e.g. get_current_datetime or list)
  const input = page.getByPlaceholder('Ask anything')
  await input.fill("What's the time?")
  await page.getByRole('button', { name: 'Send' }).click()

  // Wait for last assistant bubble to be visible and not streaming
  const lastBubble = page.locator('.bubble.assistant').last()
  await expect(lastBubble).toBeVisible({ timeout: 15000 })
  await expect(lastBubble).not.toBeEmpty()

  // Trace block: toggle button "Outils utilisés" (may be collapsed)
  const traceToggle = page.getByRole('region', { name: 'Agent execution trace' }).getByRole('button', { name: /Outils utilisés/ })
  await expect(traceToggle).toBeVisible({ timeout: 5000 })

  // Expand the trace
  await traceToggle.click()

  // At least one tool name should be visible (e.g. get_current_datetime, list_projects, create_time_log)
  const traceDetail = page.getByRole('region', { name: 'Agent execution trace' })
  await expect(traceDetail).toContainText(/get_current_datetime|list_projects|create_time_log|search_project|get_recent_logs/i, { timeout: 3000 })
})
