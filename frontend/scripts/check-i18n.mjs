// Fails when the Chinese and English locale files drift apart.
// Run: pnpm check:i18n
import { readFileSync } from 'node:fs'

const DIR = new URL('../src/shared/i18n/locales/', import.meta.url)
const LOCALES = { 'zh-CN': 'zh-CN.json', 'en-US': 'en-US.json' }

function flatten(value, prefix = '') {
  if (value === null || typeof value !== 'object') {
    return [[prefix, value]]
  }
  return Object.entries(value).flatMap(([key, child]) =>
    flatten(child, prefix ? `${prefix}.${key}` : key),
  )
}

const entries = Object.fromEntries(
  Object.entries(LOCALES).map(([locale, file]) => [
    locale,
    new Map(flatten(JSON.parse(readFileSync(new URL(file, DIR), 'utf8')))),
  ]),
)

const [zh, en] = [entries['zh-CN'], entries['en-US']]
const problems = []

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
