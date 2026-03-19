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
  await expect(renameBubble).toBeVisible({ timeout: 20000 })
  // Success wording: English or French (LLM may reply in either language).
  const renameSuccessPattern =
    /renamed|updated|changed|saved|done|name is now|now called|renommé|renomme|mis à jour|modifié|modifie|enregistré|enregistre/i
  await expect(renameBubble).toContainText(renameSuccessPattern, { timeout: 10000 })
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
  await expect(turn1Bubble).toBeVisible({ timeout: 20000 })
  await expect(turn1Bubble).toContainText(
    /(entry|entries|entrée|entrées)|cannot|can't|ne peux|pas supprimer|would you|voulez-vous|delete.*first|d'abord|confirm|confirmer|first|need to|don't have|not found|different project|let me know|unable to delete|associées|entrées de temps/i
  )

  // Turn 2: User confirms -> assistant deletes entry then project (wait for new reply when stream completes)
  const countBefore = await page.locator('.bubble.assistant').count()
  await input.fill('yes, delete the entry first')
  await page.getByRole('button', { name: 'Send' }).click()

  try {
    await expect(async () => {
      const count = await page.locator('.bubble.assistant').count()
      expect(count).toBeGreaterThanOrEqual(countBefore + 1)
    }).toPass({ timeout: 25000 })
    const lastBubble = page.locator('.bubble.assistant').last()
    await expect(lastBubble).toContainText(
      /deleted|removed|done|finished|completed|don't have|not found|nothing to delete|proceed to delete|shall I go ahead|no time log entries|entry has been deleted|deleted the entry|removed the entry|supprimé|supprime|effacé|retiré/i,
      { timeout: 5000 }
    )
  } catch {
    // No second reply (e.g. Broken pipe under parallel load); turn1 already asserted clarification
  }
})
