import { test, expect } from '@playwright/test'
import { API_BASE, API_KEY } from './e2eEnv'

/**
 * E2E: Clicking a project card adds it to conversation context.
 * User can add projects from the Projects view; selected projects appear as chips
 * above the input when back on the conversation view.
 */
test('clicking project card adds project to context and shows chip in conversation', async ({
  page,
  request,
}) => {
  const seedRes = await request.post(`${API_BASE}/dev/seed`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: {},
  })
  if (!seedRes.ok()) {
    const body = await seedRes.text()
    const hint =
      seedRes.status() === 401
        ? ' API key mismatch: ensure backend/.env HORAIN_API_KEY matches (or set VITE_API_KEY).'
        : ' Ensure backend is running on 8080.'
    throw new Error(`Seed API failed (${seedRes.status()}).${hint} Response: ${body}`)
  }

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

  // Go back to conversation
  await page.getByRole('button', { name: 'Back' }).click()

  // Project chip should be visible above the input
  const contextChips = page.locator('.context-chips')
  await expect(contextChips).toBeVisible()
  await expect(contextChips.getByText('Horain')).toBeVisible()
})
