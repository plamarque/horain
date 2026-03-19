import { test, expect } from '@playwright/test'
import { API_BASE, API_KEY, RECENT_LOGGED_AT, uniqueProjectName } from './e2eEnv'

/**
 * E2E: Edit a project via context menu on a log card.
 * Creates project and time log via API, then right-clicks on the card showing that project,
 * chooses "Edit project", changes the name, saves, and verifies the modal closes and list updates.
 */
test('edit project via context menu on card', async ({ page, request }) => {
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
      note: `e2e project modal test ${projectName}`,
      loggedAt: RECENT_LOGGED_AT,
    },
  })
  expect(timeLogRes.ok()).toBeTruthy()

  await page.goto('/')
  await page.waitForResponse((resp) => resp.url().includes('/time-logs/recent') && resp.status() === 200, { timeout: 15000 })

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()
  await expect(page.getByText('Dernières activités')).toBeVisible({ timeout: 5000 })

  // Find the card by note (visible when collapsed); project name is visible when expanded
  const cardWithProject = page
    .locator('.card-wrapper')
    .filter({ has: page.locator('.card-note').filter({ hasText: `e2e project modal test ${projectName}` }) })
    .first()
  await expect(cardWithProject).toBeVisible({ timeout: 10000 })

  // Right-click to open context menu, then click "Edit project"
  await cardWithProject.click({ button: 'right' })
  await page.getByRole('menuitem', { name: 'Edit project' }).click()

  // Project edit modal opens
  const projectModal = page.locator('.modal').filter({ has: page.getByRole('heading', { name: 'Edit project' }) })
  await expect(projectModal).toBeVisible()

  const nameInput = projectModal.getByLabel('Name')
  await expect(nameInput).toHaveValue(projectName)
  const updatedName = `${projectName}-Updated`
  await nameInput.fill(updatedName)
  await projectModal.getByRole('button', { name: 'Save' }).click()

  await expect(page.getByRole('heading', { name: 'Edit project' })).not.toBeVisible()

  // Updated project name is present on a card (recent logs are refreshed on save)
  await expect(
    page.locator('.card-project').filter({ hasText: updatedName })
  ).toHaveCount(1, { timeout: 5000 })
})

/**
 * E2E: Edit a project via double-click on project name on card (still supported).
 */
test('edit project via double-click on project name on card', async ({ page, request }) => {
  const projectName = uniqueProjectName('EditProjectDblClickE2E')
  const projectRes = await request.post(`${API_BASE}/projects`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: { name: projectName, description: 'e2e dblclick', billable: true },
  })
  if (!projectRes.ok()) {
    throw new Error(`Project API failed (${projectRes.status()}). Ensure backend is running and API key matches.`)
  }
  const project = (await projectRes.json()) as { id: string }

  await request.post(`${API_BASE}/time-logs`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: {
      projectId: project.id,
      durationMinutes: 10,
      note: `e2e dblclick ${projectName}`,
      loggedAt: RECENT_LOGGED_AT,
    },
  })

  await page.goto('/')
  await page.waitForResponse((resp) => resp.url().includes('/time-logs/recent') && resp.status() === 200, { timeout: 15000 })
  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()
  await expect(page.getByText('Dernières activités')).toBeVisible({ timeout: 5000 })

  // Find card by note, then expand (click) so project name is visible
  const cardWithProject = page
    .locator('.card-wrapper')
    .filter({ has: page.locator('.card-note').filter({ hasText: `e2e dblclick ${projectName}` }) })
    .first()
  await expect(cardWithProject).toBeVisible({ timeout: 10000 })
  await cardWithProject.click()
  await expect(cardWithProject).toHaveClass(/card-wrapper--expanded/, { timeout: 3000 })
  const projectNameEl = cardWithProject.locator('.card-project').filter({ hasText: projectName })
  await expect(projectNameEl).toBeVisible({ timeout: 8000 })
  await projectNameEl.dblclick({ delay: 80 })

  await expect(page.getByRole('heading', { name: 'Edit project' })).toBeVisible({ timeout: 10000 })
  const projectModal = page.locator('.modal').filter({ has: page.getByRole('heading', { name: 'Edit project' }) })
  await expect(projectModal).toBeVisible()
  await projectModal.getByRole('button', { name: 'Cancel' }).click()
  await expect(page.getByRole('heading', { name: 'Edit project' })).not.toBeVisible()
})

/**
 * E2E: Open Projects view from header, open project edit via pen icon on card, save and close.
 */
test('edit project via Projects view (cards + pen icon)', async ({ page, request }) => {
  const projectName = uniqueProjectName('EditViaListE2E')
  const projectRes = await request.post(`${API_BASE}/projects`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: { name: projectName, description: 'e2e list edit', billable: true },
  })
  if (!projectRes.ok()) {
    throw new Error(`Project API failed (${projectRes.status()}). Ensure backend is running and API key matches.`)
  }

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  await page.getByRole('button', { name: 'Projects' }).click()

  const projectCard = page.locator('.project-card').filter({ hasText: projectName }).first()
  await expect(projectCard).toBeVisible({ timeout: 5000 })
  await projectCard.click()
  await projectCard.getByRole('button', { name: 'Edit project' }).click()

  const projectModal = page.locator('.modal').filter({ has: page.getByRole('heading', { name: 'Edit project' }) })
  await expect(projectModal).toBeVisible()

  const updatedName = `${projectName}-FromList`
  await projectModal.getByLabel('Name').fill(updatedName)
  await projectModal.getByRole('button', { name: 'Save' }).click()

  await expect(projectModal).not.toBeVisible()
})
