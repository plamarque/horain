import { test, expect } from '@playwright/test'

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

    // First assistant bubble can appear ~8–14s after Send under parallel load; 25s timeout avoids flakiness
    await expect(page.locator('.bubble.assistant').first()).toBeVisible({ timeout: 25000 })
    await expect(
      page.locator('.bubble.assistant').filter({ hasText: /logged|created|recorded|minutes|HatCast|enregistré/i }).last()
    ).toBeVisible({ timeout: 5000 })
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
