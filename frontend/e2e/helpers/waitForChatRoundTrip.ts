import type { Locator } from '@playwright/test'
import { expect } from '@playwright/test'

/**
 * After Send, the input is disabled until the LLM stream completes. o4-mini + tool rounds
 * can exceed 30s; waiting on `.bubble.assistant` alone races the default Playwright test timeout.
 */
export async function waitForChatInputReady(input: Locator, timeoutMs = 90_000): Promise<void> {
  await expect(input).toBeEnabled({ timeout: timeoutMs })
}
