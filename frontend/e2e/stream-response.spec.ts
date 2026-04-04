import { test, expect } from '@playwright/test'

/**
 * E2E: Streaming response — message is sent to POST /chat/message/stream,
 * assistant reply appears (progressively or as a whole) and is visible.
 */
test('stream response shows assistant message', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await input.fill("What's the time?")
  await page.getByRole('button', { name: 'Send' }).click()

  // With streaming, the assistant bubble appears as chunks arrive (or at once if fallback).
  const assistantBubble = page.locator('.bubble.assistant').last()
  await expect(assistantBubble).toBeVisible({ timeout: 10_000 })
  await expect(assistantBubble).not.toBeEmpty()

  // Final content should be substantive (e.g. time or a short reply)
  await expect(assistantBubble).toContainText(/.+/, { timeout: 5000 })
})
