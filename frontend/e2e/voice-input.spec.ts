import { test, expect } from '@playwright/test'

// #region agent log
const DEBUG_LOG = (message: string, data: Record<string, unknown>, hypothesisId: string) => {
  fetch('http://127.0.0.1:7511/ingest/cb0e9a9d-fd58-4c4c-a176-9369ea09a8a5', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': 'c843fd' },
    body: JSON.stringify({ sessionId: 'c843fd', location: 'voice-input.spec.ts', message, data, hypothesisId, timestamp: Date.now() }),
  }).catch(() => {})
}
// #endregion

/**
 * Mock Web Speech API so voice tests run without real microphone/speech.
 * Injects before page load. Simulates: start() -> (user clicks Confirm) -> stop() -> onTranscript with canned text.
 */
function installSpeechRecognitionMock(mockTranscript: string) {
  const MockSpeechRecognition = class extends EventTarget {
    start() {
      this.dispatchEvent(new Event('audiostart'))
    }

    stop() {
      if ((this as unknown as { onresult?: (e: unknown) => void }).onresult) {
        const fakeEvent = {
          resultIndex: 0,
          results: [
            {
              isFinal: true,
              0: { transcript: mockTranscript },
              length: 1,
            },
          ],
          length: 1,
        }
        ;(this as unknown as { onresult: (e: unknown) => void }).onresult(fakeEvent)
      }
      ;(this as unknown as { onend?: () => void }).onend?.()
    }
  }

  ;(window as unknown as { SpeechRecognition: typeof MockSpeechRecognition }).SpeechRecognition =
    MockSpeechRecognition
  ;(window as unknown as { webkitSpeechRecognition: typeof MockSpeechRecognition }).webkitSpeechRecognition =
    MockSpeechRecognition
}

test.describe('voice input', () => {
  test.beforeEach(async ({ page, context }) => {
    await context.grantPermissions(['microphone'])
    await page.addInitScript(installSpeechRecognitionMock, '30 minutes on HatCast V1')
    await page.goto('/')
  })

  test('click mic shows waveform and Cancel/Confirm buttons, Cancel returns to input', async ({
    page,
  }) => {
    await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

    const input = page.getByPlaceholder('Ask anything')
    await expect(input).toBeVisible()

    const micBtn = page.getByRole('button', { name: 'Click to speak' })
    await expect(micBtn).toBeVisible()
    await micBtn.click()

    const cancelBtn = page.getByRole('button', { name: 'Cancel' })
    await expect(cancelBtn).toBeVisible({ timeout: 3000 })
    await expect(page.getByRole('button', { name: 'Confirm' })).toBeVisible()

    await cancelBtn.click()

    await expect(input).toBeVisible()
    await expect(input).toHaveValue('')
  })

  test('click mic then Confirm inserts transcript at caret, user can send manually', async ({
    page,
  }) => {
    await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

    const input = page.getByPlaceholder('Ask anything')
    await expect(input).toBeVisible()

    const micBtn = page.getByRole('button', { name: 'Click to speak' })
    await micBtn.click()

    const confirmBtn = page.getByRole('button', { name: 'Confirm' })
    await expect(confirmBtn).toBeVisible({ timeout: 3000 })
    await confirmBtn.click()

    await expect(input).toHaveValue('30 minutes on HatCast V1', { timeout: 5000 })
    await page.getByRole('button', { name: 'Send' }).click()

    // #region agent log
    const bubbleCountVoice = await page.locator('.bubble.assistant').count()
    const matchingCount = await page.locator('.bubble.assistant').filter({ hasText: /logged|created|recorded|minutes|HatCast|enregistré/i }).count()
    const lastBubbleVoice = page.locator('.bubble.assistant').last()
    const lastBubbleTextVoice = await lastBubbleVoice.innerText().catch(() => '') ?? ''
    DEBUG_LOG('voice-input: before confirmation assert', {
      bubbleCountVoice,
      matchingCount,
      lastBubbleTextVoice: typeof lastBubbleTextVoice === 'string' ? lastBubbleTextVoice.slice(0, 400) : String(lastBubbleTextVoice).slice(0, 400),
    }, 'H4')
    // #endregion
    // Assert confirmation in the latest assistant bubble (align with log-time.spec wording; .last() = reply to this send)
    await expect(
      page.locator('.bubble.assistant').filter({ hasText: /logged|created|recorded|minutes|HatCast|enregistré/i }).last()
    ).toBeVisible({ timeout: 10000 })
  })

  test('transcript is inserted at caret, appending to existing text', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

    const input = page.getByPlaceholder('Ask anything')
    await expect(input).toBeVisible()
    await input.fill('Hello ')
    await input.press('End')

    const micBtn = page.getByRole('button', { name: 'Click to speak' })
    await micBtn.click()

    const confirmBtn = page.getByRole('button', { name: 'Confirm' })
    await expect(confirmBtn).toBeVisible({ timeout: 3000 })
    await confirmBtn.click()

    await expect(input).toHaveValue('Hello 30 minutes on HatCast V1', { timeout: 5000 })
  })
})
