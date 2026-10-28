// Fails when the Chinese and English locale files drift apart.
// Run: pnpm check:i18n
import { readFileSync } from 'node:fs'

const DIR = new URL('../src/shared/i18n/locales/', import.meta.url)
const LOCALES = { 'zh-CN': 'zh-CN.json', 'en-US': 'en-US.json' }

/**
 * Finds keys written twice inside the same JSON object.
 *
 * <p>{@code JSON.parse} keeps the last one and says nothing, so a duplicate is
 * invisible until the screen renders a raw key where a translation should be —
 * and it is always the *first* meaning that silently disappears. The parse tree
 * cannot show this, so scan the text: mask string contents (they are the only
 * place a brace or colon can hide), then walk the punctuation.
 */
function duplicateKeys(text) {
  // Every string literal becomes a run of NULs of the same length, so no brace,
  // colon or quote can hide inside one. The originals are queued in text order.
  const strings = []
  const masked = text.replace(/"(?:[^"\\]|\\.)*"/g, (match) => {
    strings.push(match.slice(1, -1))
    return '"' + '\u0000'.repeat(match.length - 2) + '"'
  })

  const found = []
  const scopes = [new Set()]

  for (let i = 0; i < masked.length; i += 1) {
    const char = masked[i]
    if (char === '{') {
      scopes.push(new Set())
      continue
    }
    if (char === '}') {
      scopes.pop()
      continue
    }
    if (char !== '"') {
      continue
    }
    const end = masked.indexOf('"', i + 1)
    const raw = strings.shift()
    if (/^\s*:/.test(masked.slice(end + 1, end + 3))) {
      const scope = scopes[scopes.length - 1]
      if (scope.has(raw)) {
        found.push(raw)
      }
      scope.add(raw)
    }
    i = end
  }
  return [...new Set(found)]
}

function flatten(value, prefix = '') {
  if (value === null || typeof value !== 'object') {
    return [[prefix, value]]
  }
  return Object.entries(value).flatMap(([key, child]) =>
    flatten(child, prefix ? `${prefix}.${key}` : key),
  )
}

const raw = Object.fromEntries(
  Object.entries(LOCALES).map(([locale, file]) => [
    locale,
    readFileSync(new URL(file, DIR), 'utf8'),
  ]),
)

const entries = Object.fromEntries(
  Object.entries(raw).map(([locale, text]) => [locale, new Map(flatten(JSON.parse(text)))]),
)

const [zh, en] = [entries['zh-CN'], entries['en-US']]
const problems = []

for (const [locale, text] of Object.entries(raw)) {
  for (const key of duplicateKeys(text)) {
    problems.push(`duplicate key in ${locale}: ${key}`)
  }
}
for (const key of zh.keys()) {
  if (!en.has(key)) problems.push(`missing in en-US: ${key}`)
}
for (const key of en.keys()) {
  if (!zh.has(key)) problems.push(`missing in zh-CN: ${key}`)
}
for (const [locale, map] of Object.entries(entries)) {
  for (const [key, value] of map) {
    if (typeof value !== 'string' || value.trim() === '') {
      problems.push(`empty or non-string value in ${locale}: ${key}`)
    }
  }
}

if (problems.length > 0) {
  console.error(`i18n check failed with ${problems.length} problem(s):`)
  for (const problem of problems) console.error(`  - ${problem}`)
  process.exit(1)
}

console.log(`i18n OK: ${zh.size} keys aligned across ${Object.keys(LOCALES).length} locales`)
