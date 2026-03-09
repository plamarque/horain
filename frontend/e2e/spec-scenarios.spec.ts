import { test, expect } from '@playwright/test'

/**
 * E2E: SPEC expected behaviors — ambiguous project, unknown project, missing duration.
 * See docs/SPEC.md "Expected Behaviors".
 */

test.describe('SPEC scenarios', () => {
  test('ambiguous project name - agent asks which one', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

    const input = page.getByPlaceholder('Ask anything')
    await expect(input).toBeVisible()

    // Create two similar projects
    await input.fill('15 minutes on HatCast V1')
    await input.press('Enter')
    await expect(
      page.locator('.bubble.assistant').last()
    ).toContainText(/logged|created|HatCast V1/i, { timeout: 5000 })

    await input.fill('15 minutes on HatCast V2')
    await input.press('Enter')
    await expect(
      page.locator('.bubble.assistant').last()
    ).toContainText(/logged|created|HatCast V2/i, { timeout: 5000 })

    // Ambiguous: "HatCast" matches both
    await input.fill('30 minutes on HatCast')
    await input.press('Enter')

    // Assistant should mention both projects and ask which one
    const assistantBubble = page.locator('.bubble.assistant').last()
    await expect(assistantBubble).toBeVisible({ timeout: 5000 })
    await expect(assistantBubble).toContainText(/HatCast V1|HatCast V2|similar|which one|which project/i)
  })

  test('unknown project - agent offers to create', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

    const input = page.getByPlaceholder('Ask anything')
    await expect(input).toBeVisible()

    // Use a unique name that won't exist
    const uniqueName = `WeatherStation${Date.now()}`
    await input.fill(`40 minutes on ${uniqueName}`)
    await input.press('Enter')

    const assistantBubble = page.locator('.bubble.assistant').last()
    await expect(assistantBubble).toBeVisible({ timeout: 5000 })
    await expect(assistantBubble).toContainText(
      /don't know|unknown|create|should i create|couldn't find|not found|no project|doesn't exist/i
    )
  })

  test('missing duration - agent asks for estimate', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

    const input = page.getByPlaceholder('Ask anything')
    await expect(input).toBeVisible()

    // Create project first
    const projectName = `Meeds${Date.now()}`
    await input.fill(`15 minutes on ${projectName}`)
    await input.press('Enter')
    await expect(
      page.locator('.bubble.assistant').last()
    ).toContainText(new RegExp(`logged|created|${projectName}`, 'i'), { timeout: 5000 })

    // Missing duration: no minutes specified
    await input.fill(`I worked on ${projectName} all morning`)
    await input.press('Enter')

    const assistantBubble = page.locator('.bubble.assistant').last()
    await expect(assistantBubble).toBeVisible({ timeout: 5000 })
    await expect(assistantBubble).toContainText(/duration|estimate|how long|minutes|hours/i)
  })
})
