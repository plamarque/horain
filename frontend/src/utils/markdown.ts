import { marked } from 'marked'
import DOMPurify from 'dompurify'
import katex from 'katex'

/** Strip markdown image syntax lines (e.g. ![...](chart_url)) - charts are rendered separately */
function stripImageLines(text: string): string {
  return text
    .split('\n')
    .filter((line) => !/^!\s*\[.*\]\s*\(.*\)\s*$/.test(line.trim()))
    .join('\n')
    .trim()
}

/** True if the string looks like LaTeX (contains backslash-commands), not a markdown link */
function looksLikeLatex(s: string): boolean {
  if (/\]\s*\(/.test(s)) return false // markdown link
  return /\\[a-zA-Z]|\\[\[\]()]/.test(s)
}

/**
 * Extract display-math blocks in [ ... ] or \[ ... \], replace with placeholders.
 * Returns modified text and list of LaTeX strings to render.
 */
function extractMathBlocks(text: string): { text: string; blocks: string[] } {
  const blocks: string[] = []

  // 1) Standard \[ ... \] display math
  let out = text.replace(/\\\[\s*([\s\S]*?)\s*\\\]/g, (_, math) => {
    blocks.push(math.trim())
    return `\n\n___MATH${blocks.length - 1}___\n\n`
  })

  // 2) Single-bracket [ ... ] blocks that look like LaTeX (LLM output)
  out = out.replace(/\[\s*([\s\S]*?)\s*\]/g, (_, raw) => {
    const content = raw.trim()
    if (!content || !looksLikeLatex(content)) return `[${raw}]`
    blocks.push(content)
    return `\n\n___MATH${blocks.length - 1}___\n\n`
  })

  return { text: out, blocks }
}

function renderLatexBlock(latex: string): string {
  try {
    return katex.renderToString(latex, {
      displayMode: true,
      throwOnError: false,
      output: 'html',
    })
  } catch {
    return escapeHtml(latex)
  }
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

const ALLOWED_TAGS = [
  'p', 'br', 'strong', 'em', 'ul', 'ol', 'li', 'b', 'i',
  'span', // KaTeX output
]
const ALLOWED_ATTR = ['class', 'aria-hidden']

/**
 * Renders markdown to safe HTML.
 * Removes image syntax (charts shown separately), supports **bold**, lists, and LaTeX math.
 */
export function renderMarkdown(text: string): string {
  const stripped = stripImageLines(text)
  const { text: withPlaceholders, blocks } = extractMathBlocks(stripped)
  const rawHtml = marked(withPlaceholders, {
    gfm: true,
    breaks: true,
  }) as string

  let html = rawHtml
  blocks.forEach((latex, i) => {
    const placeholder = `___MATH${i}___`
    const rendered = renderLatexBlock(latex)
    html = html.replace(placeholder, rendered)
  })

  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
  })
}
