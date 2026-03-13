import { test, expect } from '@playwright/test'

/**
 * E2E: Log time via text input (demo workflow without microphone).
 * User types "30 minutes on HatCast V1 working on the algorithm",
 * system creates project, logs time, shows confirmation.
 */
test('log time via text input', async ({ page }) => {
  await page.goto('/')

  // Wait for app to load
  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  // Type the time log phrase (input is always visible)
  const input = page.getByPlaceholder('Ask anything')
  await expect(input).toBeVisible()
  await input.fill('30 minutes on HatCast V1 working on the selection algorithm')
  await page.getByRole('button', { name: 'Send' }).click()

  // Expect confirmation in last assistant message (not project cards)
  await expect(
    page.locator('.bubble.assistant').last().locator('.content').getByText(/logged|created.*HatCast|minutes.*HatCast/i)
  ).toBeVisible({ timeout: 5000 })
})

/**
 * E2E: Send button appears when typing and can submit via click.
 */
test('send button appears when typing and submits on click', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await expect(input).toBeVisible()

  // Send button should not be visible when input is empty
  await expect(page.getByRole('button', { name: 'Send' })).not.toBeVisible()

  // Type text - Send button should appear
  await input.fill('30 minutes on HatCast V1')
  await expect(page.getByRole('button', { name: 'Send' })).toBeVisible()

  // Submit via Send button click instead of Enter
  await page.getByRole('button', { name: 'Send' }).click()

  // Expect confirmation in last assistant message (not project cards)
  await expect(
    page.locator('.bubble.assistant').last().locator('.content').getByText(/logged|created|minutes|HatCast/i)
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
  await input.fill("J'ai passé 30 minutes sur HatCast V1 à travailler sur l'algo.")
  await page.getByRole('button', { name: 'Send' }).click()

  await expect(
    page.locator('.bubble.assistant').last().locator('.content').getByText(/logged|created|minutes|HatCast/i)
  ).toBeVisible({ timeout: 5000 })
})
