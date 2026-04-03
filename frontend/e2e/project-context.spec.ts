import { test, expect } from '@playwright/test'

/**
 * E2E: Clicking a project card adds it to conversation context.
 * User can add projects from the Projects view; selected projects appear as chips
 * above the input when back on the conversation view.
 *
 * Expects seed project "Horain" from Playwright global setup (POST /dev/seed/reset).
 */
test('clicking project card adds project to context and shows chip in conversation', async ({
  page,
}) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  await page.getByRole('button', { name: 'Projects' }).click()
  await expect(page.getByRole('heading', { name: 'Projects' })).toBeVisible({ timeout: 5000 })

  // Click the first project card that contains "Horain" (from seed)
  const horainCard = page.locator('.project-card').filter({ hasText: 'Horain' }).first()
  await expect(horainCard).toBeVisible({ timeout: 5000 })
  await horainCard.click()

  // Card should show in-context state
  await expect(horainCard).toHaveClass(/project-card--in-context/)

  // Discussion bar should be visible on Projects view with project in context
  const discussionBar = page.locator('.discussion-bar')
  await expect(discussionBar).toBeVisible()
  await expect(discussionBar.getByText('Horain')).toBeVisible()
  await expect(discussionBar.getByText(/Discussion sur ce projet/)).toBeVisible()

  // Go back to conversation
  await page.getByRole('button', { name: 'Back' }).click()

  // Project chip should be visible above the input (scope to conversation input area to avoid strict mode: two .context-chips in DOM)
  const contextChips = page.locator('.input-area .context-chips')
  await expect(contextChips).toBeVisible()
  await expect(contextChips.getByText('Horain')).toBeVisible()
})
