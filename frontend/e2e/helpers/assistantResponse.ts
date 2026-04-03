import type { Locator, Page } from '@playwright/test'
import { expect } from '@playwright/test'
import { waitForChatInputReady } from './waitForChatRoundTrip'

/** Matches generic empty-stream / empty-model fallbacks from frontend or backend. */
const FALLBACK_REPLY_RE =
  /Please try again|No response was displayed|The chat stream ended before a reply|couldn't generate|I couldn't generate a response/i

export function isAssistantFallbackOrEmptyReply(text: string | null | undefined): boolean {
  const t = (text ?? '').trim()
  if (t.length === 0) return true
  return FALLBACK_REPLY_RE.test(t)
}

/**
 * Sends a message and waits for a visible assistant bubble. Retries the same prompt when the reply
 * is empty or a known fallback (reduces flake when the streaming API returns no content).
 */
export async function sendChatWithAssistantBubble(
  page: Page,
  input: Locator,
  message: string,
  options?: { bubbleTimeoutMs?: number; maxAttempts?: number }
): Promise<Locator> {
  const bubbleTimeout = options?.bubbleTimeoutMs ?? 25_000
  const maxAttempts = options?.maxAttempts ?? 2

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    await input.fill(message)
    await page.getByRole('button', { name: 'Send' }).click()
    // Wait for stream to finish before reading `.bubble.assistant.last()`. Otherwise the locator
    // can match the *previous* turn's bubble (new turn may have no text bubble until onDone).
    await waitForChatInputReady(input)
    const bubble = page.locator('.bubble.assistant').last()
    await expect(bubble).toBeVisible({ timeout: bubbleTimeout })
    const text = await bubble.textContent()
    if (!isAssistantFallbackOrEmptyReply(text) || attempt === maxAttempts) {
      return bubble
    }
  }
  return page.locator('.bubble.assistant').last()
}
