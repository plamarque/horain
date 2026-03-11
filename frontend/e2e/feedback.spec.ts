import { test, expect } from '@playwright/test'

/**
 * E2E: Feedback (thumb up/down) on assistant messages.
 * After an assistant reply, the user can rate it; the buttons update and the rating is sent to the backend.
 */
test('feedback thumbs appear after assistant reply and thumb up can be clicked', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await input.fill("What's the time?")
  await input.press('Enter')

  const assistantBubble = page.locator('.bubble.assistant').last()
  await expect(assistantBubble).toBeVisible({ timeout: 15000 })
  await expect(assistantBubble).not.toBeEmpty()

  const feedbackRow = page.getByRole('group', { name: 'Feedback on this response' })
  await expect(feedbackRow).toBeVisible({ timeout: 5000 })

  const thumbUp = page.getByRole('button', { name: 'Good response' })
  const thumbDown = page.getByRole('button', { name: 'Bad response' })
  await expect(thumbUp).toBeVisible()
  await expect(thumbDown).toBeVisible()
  await expect(thumbUp).toBeEnabled()
  await expect(thumbDown).toBeEnabled()

  await thumbUp.click()

  await expect(thumbUp).toHaveClass(/active/)
  await expect(thumbUp).toBeDisabled()
  await expect(thumbDown).toBeDisabled()
})

test('feedback thumb down can be clicked', async ({ page }) => {
  await page.goto('/')

  const input = page.getByPlaceholder('Ask anything')
  await input.fill('Hello')
  await input.press('Enter')

  const assistantBubble = page.locator('.bubble.assistant').last()
  await expect(assistantBubble).toBeVisible({ timeout: 15000 })

  const feedbackRow = page.getByRole('group', { name: 'Feedback on this response' })
  await expect(feedbackRow).toBeVisible({ timeout: 5000 })

  const thumbDown = page.getByRole('button', { name: 'Bad response' })
  await thumbDown.click()

  await expect(thumbDown).toHaveClass(/active/)
  await expect(thumbDown).toBeDisabled()
})
