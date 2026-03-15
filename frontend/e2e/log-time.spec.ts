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

  const lastBubble = page.locator('.bubble.assistant').last()
  await expect(lastBubble).toBeVisible({ timeout: 25000 })
  await expect(
    lastBubble.locator('.content').getByText(/logged|created|recorded|minutes|HatCast|added|saved|30|min|enregistré|fait|done/i)
  ).toBeVisible({ timeout: 15000 })
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

  const lastBubble = page.locator('.bubble.assistant').last()
  await expect(lastBubble).toBeVisible({ timeout: 25000 })
  await expect(
    lastBubble.locator('.content').getByText(/logged|created|recorded|minutes|HatCast|added|saved|30|min|enregistré|fait|done/i)
  ).toBeVisible({ timeout: 15000 })
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

  const lastBubble = page.locator('.bubble.assistant').last()
  await expect(lastBubble).toBeVisible({ timeout: 30000 })
  await expect(
    lastBubble.locator('.content').getByText(/logged|created|recorded|minutes|HatCast|enregistré|added|saved|30|min|fait|done/i)
  ).toBeVisible({ timeout: 15000 })
})
