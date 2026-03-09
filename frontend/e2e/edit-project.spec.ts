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
 * E2E: Delete project via text input.
 * Create a project, then ask to delete it.
 */
test('delete project via natural language', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await expect(input).toBeVisible()

  // Create project first
  await input.fill('15 minutes on DeleteTestProject')
  await input.press('Enter')

  await expect(
    page.getByText(/logged|created|minutes|DeleteTestProject/i)
  ).toBeVisible({ timeout: 5000 })

  // Ask to delete the project
  await input.fill('delete project DeleteTestProject')
  await input.press('Enter')

  // Expect confirmation of deletion in assistant's response (exclude user message and table)
  await expect(
    page.locator('.bubble.assistant').filter({ hasText: /deleted|removed/i })
  ).toBeVisible({ timeout: 5000 })
})
