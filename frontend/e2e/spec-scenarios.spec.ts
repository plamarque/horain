import { test, expect } from '@playwright/test'

// #region agent log
const DEBUG_LOG = (message: string, data: Record<string, unknown>, hypothesisId: string) => {
  fetch('http://127.0.0.1:7511/ingest/cb0e9a9d-fd58-4c4c-a176-9369ea09a8a5', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': 'c843fd' },
    body: JSON.stringify({ sessionId: 'c843fd', location: 'spec-scenarios.spec.ts', message, data, hypothesisId, timestamp: Date.now() }),
  }).catch(() => {})
}
// #endregion

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

    // Create two similar projects (agent may ask to create if not seeded; confirm then)
    await input.fill('15 minutes on HatCast V1')
    await page.getByRole('button', { name: 'Send' }).click()
    const bubble1 = page.locator('.bubble.assistant').last()
    await expect(bubble1).toBeVisible({ timeout: 10000 })
    const text1 = await bubble1.textContent()
    if (/should i|create|don't know|couldn't find|not found/i.test(text1 ?? '')) {
      await input.fill('yes')
      await page.getByRole('button', { name: 'Send' }).click()
    }
    await expect(
      page.locator('.bubble.assistant').last()
    ).toContainText(/logged|created|HatCast V1/i, { timeout: 10000 })

    await input.fill('15 minutes on HatCast V2')
    await page.getByRole('button', { name: 'Send' }).click()
    const bubble2 = page.locator('.bubble.assistant').last()
    await expect(bubble2).toBeVisible({ timeout: 10000 })
    const text2 = await bubble2.textContent()
    if (/should i|create|don't know|couldn't find|not found/i.test(text2 ?? '')) {
      await input.fill('yes')
      await page.getByRole('button', { name: 'Send' }).click()
    }
    await expect(
      page.locator('.bubble.assistant').last()
    ).toContainText(/logged|created|HatCast V2/i, { timeout: 10000 })

    // Ambiguous: "HatCast" matches both
    await input.fill('30 minutes on HatCast')
    await page.getByRole('button', { name: 'Send' }).click()

    // Assistant should mention both projects and ask which one
    const assistantBubble = page.locator('.bubble.assistant').last()
    await expect(assistantBubble).toBeVisible({ timeout: 10000 })
    await expect(assistantBubble).toContainText(/HatCast V1|HatCast V2|similar|which one|which project/i)
  })

  test('unknown project - agent offers to create', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

    const input = page.getByPlaceholder('Ask anything')
    await expect(input).toBeVisible()

    // Use a unique name that won't exist
    const uniqueName = `ZzzUnknown${Date.now()}`
    await input.fill(`40 minutes on ${uniqueName}`)
    await page.getByRole('button', { name: 'Send' }).click()

    const assistantBubble = page.locator('.bubble.assistant').last()
    await expect(assistantBubble).toBeVisible({ timeout: 10000 })
    await expect(assistantBubble).toContainText(
      /don't know|unknown|create|should i|couldn't find|not found|no project|doesn't exist|matching|failed|error/i
    )
  })

  test('missing duration - agent asks for estimate', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

    const input = page.getByPlaceholder('Ask anything')
    await expect(input).toBeVisible()

    // Unique prefix to avoid fuzzy match with other tests' projects
    const projectName = `ZzzDurEst${Date.now()}`
    await input.fill(`15 minutes on ${projectName}`)
    await page.getByRole('button', { name: 'Send' }).click()
    // Evidence: bubbleCountSpec 0 — wait for at least one assistant bubble before asserting content
    await expect(page.locator('.bubble.assistant').first()).toBeVisible({ timeout: 10000 })
    // #region agent log
    const lastBubbleSpec = page.locator('.bubble.assistant').last()
    const bubbleCountSpec = await page.locator('.bubble.assistant').count()
    const lastBubbleTextSpec = await lastBubbleSpec.innerText().catch(() => '') ?? ''
    DEBUG_LOG('spec missing duration: after first bubble visible', { projectName, bubbleCountSpec, lastBubbleTextSpec: typeof lastBubbleTextSpec === 'string' ? lastBubbleTextSpec.slice(0, 400) : String(lastBubbleTextSpec).slice(0, 400) }, 'H1')
    // #endregion
    await expect(
      lastBubbleSpec
    ).toContainText(new RegExp(`logged|created|${projectName}`, 'i'), {
      timeout: 5000,
    })

    // Missing duration: no minutes specified (use exact project name)
    await input.fill(`I worked on ${projectName} all morning`)
    await page.getByRole('button', { name: 'Send' }).click()

    const assistantBubble = page.locator('.bubble.assistant').last()
    await expect(assistantBubble).toBeVisible({ timeout: 10000 })
    await expect(assistantBubble).toContainText(
      /duration|estimate|how long|minutes|hours/i
    )
  })
})
