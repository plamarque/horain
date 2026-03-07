import { marked } from 'marked'
import DOMPurify from 'dompurify'

/** Strip markdown image syntax lines (e.g. ![...](chart_url)) - charts are rendered separately */
function stripImageLines(text: string): string {
  return text
    .split('\n')
    .filter((line) => !/^!\s*\[.*\]\s*\(.*\)\s*$/.test(line.trim()))
    .join('\n')
    .trim()
}

/**
 * Renders markdown to safe HTML.
 * Removes image syntax (charts shown separately), supports **bold**, lists, etc.
 */
export function renderMarkdown(text: string): string {
  const stripped = stripImageLines(text)
  const rawHtml = marked(stripped, {
    gfm: true,
    breaks: true,
  }) as string
  return DOMPurify.sanitize(rawHtml, {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'ul', 'ol', 'li', 'b', 'i'],
  })
}
