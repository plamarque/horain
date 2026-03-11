import { test, expect } from '@playwright/test'
import { API_BASE, API_KEY, uniqueProjectName } from './e2eEnv'

/**
 * E2E: Edit a project via double-click on project name in the entry table.
 * Creates project and time log via API, then double-clicks on the project name cell
 * to open the project edit modal, changes the name, saves, and verifies the modal closes.
 */
test('edit project via double-click on project name in table', async ({ page, request }) => {
  const projectName = uniqueProjectName('EditProjectModalE2E')
  const projectRes = await request.post(`${API_BASE}/projects`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: { name: projectName, description: 'e2e project edit', billable: true },
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
      durationMinutes: 20,
      note: 'e2e project modal test',
    },
  })
  expect(timeLogRes.ok()).toBeTruthy()

  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  await expect(page.getByText('Dernières activités')).toBeVisible({ timeout: 5000 })

  // Double-click on the project name cell (not the whole row) to open project edit modal
  const projectNameCell = page
    .locator('.log-table .log-project')
    .filter({ hasText: projectName })
    .first()
  await expect(projectNameCell).toBeVisible({ timeout: 5000 })
  await projectNameCell.dblclick()

  // Project edit modal opens (not entry edit modal) — scope to this modal to avoid matching entry modal
  const projectModal = page.locator('.modal').filter({ has: page.getByRole('heading', { name: 'Edit project' }) })
  await expect(projectModal).toBeVisible()

  const nameInput = projectModal.getByLabel('Name')
  await expect(nameInput).toHaveValue(projectName)
  const updatedName = `${projectName}-Updated`
  await nameInput.fill(updatedName)
  await projectModal.getByRole('button', { name: 'Save' }).click()

  await expect(page.getByRole('heading', { name: 'Edit project' })).not.toBeVisible()

  // Table should show the updated project name (recent logs are refreshed on save)
  await expect(
    page.locator('.log-table .log-project').filter({ hasText: updatedName })
  ).toBeVisible({ timeout: 5000 })
})
