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
    page.getByText(/logged|created|minutes|EditTestProject/i)
  ).toBeVisible({ timeout: 5000 })

  // Ask to rename the project
  await input.fill('rename EditTestProject to EditTestProjectV2')
  await input.press('Enter')

  // Expect confirmation of rename/update in assistant's response (exclude user message)
  await expect(
    page.locator('.bubble.assistant').filter({ hasText: /renamed|updated|EditTestProjectV2/i })
  ).toBeVisible({ timeout: 5000 })
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
    page.getByText(/logged|created|minutes|DeleteTestProject/i)
  ).toBeVisible({ timeout: 5000 })

  // Turn 1: Ask to delete the project -> assistant cannot (has entries), asks for confirmation
  await input.fill('delete project DeleteTestProject')
  await input.press('Enter')

  // Expect assistant to mention entries / cannot delete / would you like to delete
  await expect(
    page.locator('.bubble.assistant').filter({
      hasText: /(entry|entries)|cannot|would you|delete.*first|confirm/i,
    })
  ).toBeVisible({ timeout: 5000 })

  // Turn 2: User confirms -> assistant deletes entry then project
  await input.fill('yes, delete the entry first')
  await input.press('Enter')

  // Expect confirmation of deletion
  await expect(
    page.locator('.bubble.assistant').filter({ hasText: /deleted|removed/i })
  ).toBeVisible({ timeout: 5000 })
})
