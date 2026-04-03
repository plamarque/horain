import { test, expect } from '@playwright/test'
import { waitForChatInputReady } from './helpers/waitForChatRoundTrip'

/**
 * E2E: Memory — user can ask to remember a preference; agent may call store_memory.
 * Then user can ask to forget; agent may call forget_memory.
 * We only assert that the conversation completes and that the trace may show memory tools.
 */
test('user can ask to remember a preference and get a response', async ({ page }) => {
  test.setTimeout(120_000)
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await input.fill('Remember that when I say HatCast I mean HatCast V2.')
  await page.getByRole('button', { name: 'Send' }).click()

  await waitForChatInputReady(input)
  const lastBubble = page.locator('.bubble.assistant').last()
  await expect(lastBubble).toBeVisible()
  await expect(lastBubble).not.toBeEmpty()

  await expect(lastBubble.locator('.content')).toContainText(
    /remember|saved|stored|ok|got it|noted|will|sure|preference|HatCast/i,
    { timeout: 10000 }
  )
})

test('user can ask to forget and get a response', async ({ page }) => {
  test.setTimeout(120_000)
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await input.fill('Forget my default project.')
  await page.getByRole('button', { name: 'Send' }).click()

  await waitForChatInputReady(input)
  const lastBubble = page.locator('.bubble.assistant').last()
  await expect(lastBubble).toBeVisible()
  await expect(lastBubble).not.toBeEmpty()

  // Match EN/FR success wording; include oublié/effacé/projet (markdown may split nodes — toContainText is more reliable than getByText on .content)
  await expect(lastBubble.locator('.content')).toContainText(
    /forgot|forgotten|forget|oublier|oublié|effacé|cleared|removed|memory|souvenir|default|project|projet|défaut|preference|understood|longer|won't|don't|any more|anymore|ok|done|noted|\bplus\b/i,
    { timeout: 20000 }
  )
})
