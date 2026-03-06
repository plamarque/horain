import { test, expect } from '@playwright/test'

/**
 * E2E: Log time via text input (demo workflow without microphone).
 * User types "30 minutes on HatCast working on the algorithm",
 * system creates project, logs time, shows confirmation.
 */
test('log time via text input', async ({ page }) => {
  await page.goto('/')

  // Wait for app to load
  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  // Click "Type" to reveal text input
  await page.getByRole('button', { name: 'Type' }).click()

  // Type the time log phrase
  const input = page.getByPlaceholder(/30 minutes on HatCast/)
  await expect(input).toBeVisible()
  await input.fill('30 minutes on HatCast working on the selection algorithm')

  // Submit
  await page.getByRole('button', { name: 'Send' }).click()

  // Expect confirmation (project created or time logged)
  await expect(
    page.getByText(/logged|created.*HatCast|minutes.*HatCast/i)
  ).toBeVisible({ timeout: 5000 })
})
