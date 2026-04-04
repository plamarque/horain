import type { Locator } from '@playwright/test'
import { expect } from '@playwright/test'

/**
 * After Send, the input is disabled until the chat stream completes (real LLM or e2e stub).
 */
export async function waitForChatInputReady(input: Locator, timeoutMs = 25_000): Promise<void> {
  await expect(input).toBeEnabled({ timeout: timeoutMs })
}
