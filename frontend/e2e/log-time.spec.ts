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

  // Type the time log phrase (input is always visible)
  const input = page.getByPlaceholder('Ask anything')
  await expect(input).toBeVisible()
  await input.fill('30 minutes on HatCast working on the selection algorithm')

  // Submit with Enter
  await input.press('Enter')

  // Expect confirmation (project created or time logged)
  await expect(
    page.getByText(/logged|created.*HatCast|minutes.*HatCast/i)
  ).toBeVisible({ timeout: 5000 })
})

/**
 * E2E: Log time via French phrase.
 * "J'ai passé 30 minutes sur HatCast à travailler sur l'algo"
 */
test('log time via French phrase', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await expect(input).toBeVisible()
  await input.fill("J'ai passé 30 minutes sur HatCast à travailler sur l'algo.")

  await input.press('Enter')

  await expect(
    page.getByText(/logged|created|minutes|HatCast/i)
  ).toBeVisible({ timeout: 5000 })
})
