import { test, expect } from '@playwright/test'
import { API_BASE, API_KEY, uniqueProjectName } from './e2eEnv'

/**
 * E2E: Delete a time log entry via the edit modal.
 * Creates data via API (no LLM), then double-clicks on the card to open the edit modal,
 * clicks Delete, confirms, and the entry is removed.
 */
test('delete log entry via edit modal', async ({ page, request }) => {
  const projectName = uniqueProjectName('DeleteLogEntryTest')
  // Create project and time log via API (no LLM) — reliable
  const projectRes = await request.post(`${API_BASE}/projects`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: { name: projectName, description: 'e2e delete test' },
  })
  if (!projectRes.ok()) {
    const hint =
      projectRes.status() === 401
        ? ' API key mismatch: ensure backend/.env HORAIN_API_KEY matches (or set VITE_API_KEY).'
        : ' Ensure backend is running on 8080.'
    throw new Error(`Project API failed (${projectRes.status()}).${hint}`)
  }
  const project = (await projectRes.json()) as { id: string }

  const timeLogRes = await request.post(`${API_BASE}/time-logs`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: {
      projectId: project.id,
      durationMinutes: 15,
      note: 'e2e delete test',
    },
  })
  expect(timeLogRes.ok()).toBeTruthy()

  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  await expect(page.getByText('Dernières activités')).toBeVisible({
    timeout: 5000,
  })
  const card = page
    .locator('.card-wrapper')
    .filter({ has: page.locator('.card-note').filter({ hasText: 'e2e delete test' }) })
    .first()
  await expect(card).toBeVisible({ timeout: 5000 })
  await card.click()
  await card.getByRole('button', { name: 'Edit entry' }).click()

  const entryEditScreen = page.locator('.entry-edit-screen')
  await expect(entryEditScreen).toBeVisible({ timeout: 5000 })
  await expect(entryEditScreen.getByRole('heading', { name: 'Edit entry' })).toBeVisible()

  await entryEditScreen.getByRole('button', { name: 'Delete' }).click()

  await expect(page.getByText('Delete this entry permanently?')).toBeVisible()
  await page.getByRole('button', { name: 'Delete' }).click()

  await expect(page.getByRole('heading', { name: 'Edit entry' })).not.toBeVisible()
  // Wait for the deleted entry (unique note) to disappear from the list
  await expect(
    page.locator('.card-wrapper').filter({ has: page.locator('.card-note').filter({ hasText: 'e2e delete test' }) })
  ).toHaveCount(0, { timeout: 10000 })
})
