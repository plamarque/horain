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

const ALLOWED_TAGS = [
  'p', 'br', 'strong', 'em', 'ul', 'ol', 'li', 'b', 'i',
]
const ALLOWED_ATTR = ['class', 'aria-hidden']

/**
 * LLM markdown often places **bold** flush against digits or the next word
 * (e.g. **vos**51, avec**36h**), which renders with no visible gap. Insert
 * a normal space at those boundaries after sanitization.
 */
function insertSpacesAroundEmphasis(html: string): string {
  let out = html.replace(
    /<\/(strong|b|em|i)>(?=[\p{L}\p{N}«(])/gu,
    '</$1> ',
  )
  out = out.replace(
    /([\p{L}\p{N}])(<(?:strong|b|em|i)>)([\p{L}\p{N}])/gu,
    '$1 $2$3',
  )
  return out
}

/**
 * Renders markdown to safe HTML.
 * Removes image syntax (charts shown separately), supports **bold**, lists.
 */
export function renderMarkdown(text: string): string {
  const stripped = stripImageLines(text)
  const rawHtml = marked(stripped, {
    gfm: true,
    breaks: true,
  }) as string

  const safe = DOMPurify.sanitize(rawHtml, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
  })
  return insertSpacesAroundEmphasis(safe)
}
