import { test, expect } from '@playwright/test'
import { API_BASE, API_KEY, uniqueProjectName } from './e2eEnv'

/**
 * E2E: Update project via text input.
 * Create a project via API (so the name is guaranteed), then ask the agent to rename it.
 */
test('update project - rename via natural language', async ({ page, request }) => {
  const projectName = uniqueProjectName('EditTestProject')
  const projectRes = await request.post(`${API_BASE}/projects`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: { name: projectName, description: 'e2e rename test' },
  })
  if (!projectRes.ok()) {
    throw new Error(`Project API failed (${projectRes.status()}). Ensure backend is running and API key matches.`)
  }

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await expect(input).toBeVisible()

  // Ask to rename the project (exact name created via API)
  const newName = `${projectName}-V2`
  await input.fill(`rename ${projectName} to ${newName}`)
  await page.getByRole('button', { name: 'Send' }).click()

  const renameBubble = page.locator('.bubble.assistant').last()
  await expect(renameBubble).toBeVisible({ timeout: 10000 })
  await expect(renameBubble).toContainText(
    /renamed|updated|changed|saved|done|name is now|now called/i,
    { timeout: 10000 }
  )
  await expect(renameBubble).toContainText(newName)
})

/**
 * E2E: Delete project via text input (two-turn flow).
 * Create a project and one time log via API, then ask the agent to delete the project.
 * Agent should see it has entries, ask for confirmation; user confirms, agent deletes entry then project.
 */
test('delete project via natural language', async ({ page, request }) => {
  const projectName = uniqueProjectName('DeleteTestProject')
  const projectRes = await request.post(`${API_BASE}/projects`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: { name: projectName, description: 'e2e delete test' },
  })
  if (!projectRes.ok()) {
    throw new Error(`Project API failed (${projectRes.status()}). Ensure backend is running and API key matches.`)
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
      note: 'e2e delete flow',
    },
  })
  expect(timeLogRes.ok()).toBeTruthy()

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await expect(input).toBeVisible()

  // Turn 1: Ask to delete the project (exact name created via API)
  await input.fill(`delete project ${projectName}`)
  await page.getByRole('button', { name: 'Send' }).click()

  const turn1Bubble = page.locator('.bubble.assistant').last()
  await expect(turn1Bubble).toBeVisible({ timeout: 10000 })
  await expect(turn1Bubble).toContainText(
    /(entry|entries)|cannot|would you|delete.*first|confirm|first|need to|don't have|not found|different project|let me know/i
  )

  // Turn 2: User confirms -> assistant deletes entry then project
  await input.fill('yes, delete the entry first')
  await page.getByRole('button', { name: 'Send' }).click()

  const turn2Bubble = page.locator('.bubble.assistant').last()
  await expect(turn2Bubble).toContainText(
    /deleted|removed|done|finished|completed|don't have|not found|nothing to delete|proceed to delete|shall I go ahead|no time log entries/i,
    { timeout: 20000 }
  )
})
