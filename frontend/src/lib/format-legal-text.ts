/**
 * Converts plain-text legal documents (stored in DB) to readable Markdown.
 *
 * Detects:
 *  - ALL-CAPS lines  → ## section heading
 *  - Short lines after a line ending in ":"  → - list item
 *  - Consecutive list items  → continued list
 *  - Everything else  → paragraph (double-newline separated)
 */
export function formatLegalText(raw: string): string {
  const lines = raw.split(/\r?\n/)
  const out: string[] = []

  const isAllCaps = (s: string) =>
    s.length > 5 && s === s.toUpperCase() && /[A-ZÁÉÍÓÚÑ]/.test(s)

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim()
    const prev = out.at(-1) ?? ''

    if (!line) {
      if (prev !== '') out.push('')
      continue
    }

    if (isAllCaps(line)) {
      if (prev !== '') out.push('')
      out.push(`## ${line}`)
      out.push('')
      continue
    }

    const isAfterColon = prev.endsWith(':')
    const isAfterList  = prev.startsWith('- ')
    if ((isAfterColon || isAfterList) && line.length < 200 && !line.endsWith(':')) {
      out.push(`- ${line}`)
      continue
    }

    out.push(line)
  }

  return out.join('\n')
}
