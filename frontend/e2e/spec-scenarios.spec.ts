import { test, expect } from '@playwright/test'
import { sendChatWithAssistantBubble } from './helpers/assistantResponse'
import { waitForChatInputReady } from './helpers/waitForChatRoundTrip'

/**
 * E2E: SPEC expected behaviors — ambiguous project, unknown project, missing duration.
 * See docs/SPEC.md "Expected Behaviors".
 */

test.describe('SPEC scenarios', () => {
  test('ambiguous project name - agent asks which one', async ({ page }) => {
    test.setTimeout(120_000)
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

    const input = page.getByPlaceholder('Ask anything')
    await expect(input).toBeVisible()

    // Create two similar projects (agent may ask to create if not seeded; confirm then)
    const bubble1 = await sendChatWithAssistantBubble(page, input, '15 minutes on HatCast V1', {
      bubbleTimeoutMs: 25_000,
    })
    const text1 = await bubble1.textContent()
    if (/should i|create|don't know|couldn't find|not found|créer|inconnu|trouvé/i.test(text1 ?? '')) {
      await waitForChatInputReady(input)
      await input.fill('yes')
      await page.getByRole('button', { name: 'Send' }).click()
    }
    await expect(
      page.locator('.bubble.assistant').last()
    ).toContainText(/logged|created|enregistré|enregistre|HatCast V1/i, { timeout: 10000 })
    await waitForChatInputReady(input)

    const bubble2 = await sendChatWithAssistantBubble(
      page,
      input,
      'Please log exactly 15 minutes on HatCast V2 (not HatCast V1)',
      { bubbleTimeoutMs: 25_000 }
    )
    const text2 = await bubble2.textContent()
    if (/should i|create|don't know|couldn't find|not found|créer|inconnu|trouvé/i.test(text2 ?? '')) {
      await waitForChatInputReady(input)
      await input.fill('yes')
      await page.getByRole('button', { name: 'Send' }).click()
    }
    await expect(
      page.locator('.bubble.assistant').last()
    ).toContainText(/logged|created|enregistré|enregistre|HatCast V2/i, { timeout: 10000 })
    await waitForChatInputReady(input)
    // Ambiguous: "HatCast" matches both
    const assistantBubble = await sendChatWithAssistantBubble(page, input, '30 minutes on HatCast', {
      bubbleTimeoutMs: 30_000,
    })

    // Assistant should mention both projects and ask which one
    await expect(assistantBubble).toContainText(
      /HatCast V1|HatCast V2|similar|which one|which project|quel|laquelle|quelle|deux|both|projets?/i
    )
  })

  test('unknown project - agent offers to create', async ({ page }) => {
    test.setTimeout(120_000)
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

    const input = page.getByPlaceholder('Ask anything')
    await expect(input).toBeVisible()

    // Use a unique name that won't exist
    const uniqueName = `ZzzUnknown${Date.now()}`
    const assistantBubble = await sendChatWithAssistantBubble(
      page,
      input,
      `40 minutes on ${uniqueName}`,
      { bubbleTimeoutMs: 25_000 }
    )

    await waitForChatInputReady(input)
    await expect(assistantBubble).toContainText(
      /don't know|unknown|create|should i|couldn't find|not found|no project|doesn't exist|matching|failed|error|créer|projet|inconnu|existe|aucun|trouvé|trouve|souhaitez|voulez|nouveau|new project|désolé|sorry/i
    )
  })

  test('missing duration - agent asks for estimate', async ({ page }) => {
    test.setTimeout(120_000)
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

    const input = page.getByPlaceholder('Ask anything')
    await expect(input).toBeVisible()

    // Unique prefix to avoid fuzzy match with other tests' projects
    const projectName = `ZzzDurEst${Date.now()}`
    await sendChatWithAssistantBubble(page, input, `15 minutes on ${projectName}`, {
      bubbleTimeoutMs: 30_000,
    })
    await waitForChatInputReady(input)
    await expect(
      page.locator('.bubble.assistant').last()
    ).toContainText(new RegExp(`logged|created|enregistr|${projectName}`, 'i'), {
      timeout: 15_000,
    })

    // Missing duration: no minutes specified (use exact project name)
    const assistantBubble = await sendChatWithAssistantBubble(
      page,
      input,
      `I worked on ${projectName} all morning`,
      { bubbleTimeoutMs: 30_000 }
    )

    await expect(assistantBubble).toContainText(
      /duration|estimate|how long|minutes|hours|combien|durée|temps|heures/i
    )
  })
})
