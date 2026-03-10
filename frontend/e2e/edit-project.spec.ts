import { test, expect } from '@playwright/test'

/**
 * E2E: Update project via text input.
 * Create a project, then ask to rename it.
 */
test('update project - rename via natural language', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await expect(input).toBeVisible()

  // Create project first
  await input.fill('30 minutes on EditTestProject working on features')
  await input.press('Enter')

  await expect(
    page.locator('.bubble.assistant').last()
  ).toContainText(/logged|created|minutes|EditTestProject/i, { timeout: 10000 })

  // Ask to rename the project
  await input.fill('rename EditTestProject to EditTestProjectV2')
  await input.press('Enter')

  // Expect confirmation of rename in the latest assistant response (wording may vary by LLM)
  const renameBubble = page.locator('.bubble.assistant').last()
  await expect(renameBubble).toBeVisible({ timeout: 10000 })
  await expect(renameBubble).toContainText(
    /renamed|updated|changed|saved|done|name is now|now called|EditTestProjectV2/i
  )
})

/**
 * E2E: Delete project via text input (two-turn flow).
 * Create a project with an entry, ask to delete it. Assistant refuses (has entries),
 * asks for confirmation. User confirms, assistant deletes entry then project.
 */
test('delete project via natural language', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await expect(input).toBeVisible()

  // Create project first (creates project + 1 time log entry)
  await input.fill('15 minutes on DeleteTestProject')
  await input.press('Enter')

  await expect(
    page.locator('.bubble.assistant').last()
  ).toContainText(/logged|created|minutes|DeleteTestProject/i, { timeout: 10000 })

  // Turn 1: Ask to delete the project -> assistant cannot (has entries), asks for confirmation
  await input.fill('delete project DeleteTestProject')
  await input.press('Enter')

  // Expect latest assistant message to mention entries / cannot delete / confirm
  const turn1Bubble = page.locator('.bubble.assistant').last()
  await expect(turn1Bubble).toBeVisible({ timeout: 10000 })
  await expect(turn1Bubble).toContainText(
    /(entry|entries)|cannot|would you|delete.*first|confirm|first|need to/i
  )

  // Turn 2: User confirms -> assistant deletes entry then project
  await input.fill('yes, delete the entry first')
  await input.press('Enter')

  // Expect latest assistant message to confirm deletion
  const turn2Bubble = page.locator('.bubble.assistant').last()
  await expect(turn2Bubble).toBeVisible({ timeout: 10000 })
  await expect(turn2Bubble).toContainText(/deleted|removed|done|finished|completed/i)
})
