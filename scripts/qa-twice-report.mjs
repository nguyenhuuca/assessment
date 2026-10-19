// Compares two test runs written by qa-twice.sh and prints pass / flaky / fail per test.
// Reads <dir>/be-1, be-2 (surefire XML folders) and <dir>/fe-1.json, fe-2.json (vitest JSON), whichever exist.
// Exit 1 when any test failed in both runs.
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const dir = process.argv[2]

// Surefire: one TEST-*.xml per class; a <testcase> with a <failure>, <error> child failed, one with <skipped> is ignored.
export function surefireResults(folder) {
  const results = new Map()
  for (const file of readdirSync(folder).filter((f) => /^TEST-.*\.xml$/.test(f))) {
    const xml = readFileSync(join(folder, file), 'utf8')
    for (const m of xml.matchAll(/<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g)) {
      const attr = (name) => (m[1].match(new RegExp(`\\b${name}="([^"]*)"`)) || [])[1] || ''
      const body = m[3] || ''
      if (/<skipped\b/.test(body)) continue
      results.set(`be ${attr('classname')}#${attr('name')}`, !/<(failure|error)\b/.test(body))
    }
  }
  return results
}

// Vitest JSON reporter: testResults[].assertionResults[] with fullName + status.
export function vitestResults(file) {
  const results = new Map()
  const report = JSON.parse(readFileSync(file, 'utf8'))
  for (const suite of report.testResults || []) {
    const name = suite.name.replace(/\\/g, '/').replace(/^.*\/webapp\//, '')
    for (const t of suite.assertionResults || []) {
      if (t.status === 'skipped' || t.status === 'pending' || t.status === 'todo') continue
      results.set(`fe ${name} > ${t.fullName}`, t.status === 'passed')
    }
    // A file that fails to load has no assertions; count it as one failing entry.
    if ((suite.assertionResults || []).length === 0 && suite.status === 'failed') results.set(`fe ${name} (load)`, false)
  }
  return results
}

export function classify(run1, run2) {
  const rows = []
  for (const key of new Set([...run1.keys(), ...run2.keys()])) {
    const a = run1.get(key) ?? false
    const b = run2.get(key) ?? false
    rows.push({ key, status: a && b ? 'pass' : a || b ? 'flaky' : 'fail' })
  }
  return rows
}

function load(kind) {
  const p1 = join(dir, kind === 'be' ? 'be-1' : 'fe-1.json')
  const p2 = join(dir, kind === 'be' ? 'be-2' : 'fe-2.json')
  if (!existsSync(p1) || !existsSync(p2)) return []
  const read = kind === 'be' ? surefireResults : vitestResults
  return classify(read(p1), read(p2))
}

if (dir) {
  const rows = [...load('be'), ...load('fe')]
  const count = (s) => rows.filter((r) => r.status === s).length
  for (const r of rows.filter((r) => r.status !== 'pass')) console.log(`${r.status.toUpperCase().padEnd(5)}  ${r.key}`)
  console.log(`QA-TWICE: ${rows.length} tests | pass ${count('pass')} | flaky ${count('flaky')} | fail ${count('fail')}`)
  if (rows.length === 0) process.exit(2)
  process.exit(count('fail') > 0 ? 1 : 0)
}
