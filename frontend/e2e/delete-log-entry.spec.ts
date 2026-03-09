import { test, expect } from '@playwright/test'

/**
 * E2E: Delete a time log entry via the edit modal.
 * User creates an entry, double-clicks on the row to open the edit modal,
 * clicks Delete, confirms, and the entry is removed.
 */
test('delete log entry via edit modal', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await expect(input).toBeVisible()

  // Create a time log (requires backend with LLM)
  await input.fill('15 minutes on DeleteLogEntryTest working on e2e delete')
  await input.press('Enter')

  await expect(
    page.getByText(/logged|created|minutes|DeleteLogEntryTest/i)
  ).toBeVisible({ timeout: 5000 })

  // Find the table row with DeleteLogEntryTest and double-click to open edit modal
  const projectCell = page.getByRole('cell', { name: 'DeleteLogEntryTest' }).first()
  await expect(projectCell).toBeVisible({ timeout: 5000 })
  await projectCell.dblclick()

  // Edit modal should open
  await expect(page.getByRole('heading', { name: 'Edit entry' })).toBeVisible()

  // Click Delete to show confirmation
  await page.getByRole('button', { name: 'Delete' }).first().click()

  // Confirm deletion
  await expect(page.getByText('Delete this entry permanently?')).toBeVisible()
  await page.getByRole('button', { name: 'Delete' }).click()

  // Modal closes, entry removed from table
  await expect(page.getByRole('heading', { name: 'Edit entry' })).not.toBeVisible()
  await expect(page.getByRole('cell', { name: 'DeleteLogEntryTest' })).not.toBeVisible()
})
