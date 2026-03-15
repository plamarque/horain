import { test, expect } from '@playwright/test'

/**
 * E2E: Memory — user can ask to remember a preference; agent may call store_memory.
 * Then user can ask to forget; agent may call forget_memory.
 * We only assert that the conversation completes and that the trace may show memory tools.
 */
test('user can ask to remember a preference and get a response', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await input.fill('Remember that when I say HatCast I mean HatCast V2.')
  await page.getByRole('button', { name: 'Send' }).click()

  const lastBubble = page.locator('.bubble.assistant').last()
  await expect(lastBubble).toBeVisible({ timeout: 25000 })
  await expect(lastBubble).not.toBeEmpty()

  const content = lastBubble.locator('.content')
  await expect(
    content.getByText(/remember|saved|stored|ok|got it|noted|will|sure|preference|HatCast/i)
  ).toBeVisible({ timeout: 10000 })
})

test('user can ask to forget and get a response', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await input.fill('Forget my default project.')
  await page.getByRole('button', { name: 'Send' }).click()

  const lastBubble = page.locator('.bubble.assistant').last()
  await expect(lastBubble).toBeVisible({ timeout: 25000 })
  await expect(lastBubble).not.toBeEmpty()

  const content = lastBubble.locator('.content')
  await expect(
    content.getByText(/forgot|forgotten|oublier|ok|done|removed|cleared|memory|souvenir|default|project/i)
  ).toBeVisible({ timeout: 12000 })
})
