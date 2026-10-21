// Run: node --test scripts/__tests__/evolve.test.mjs
import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  decide, grade, handledIds, parseArgs, parseVerdict, pathViolations, pickSignals, removedLines, reviewPrompt,
} from '../evolve.mjs'

const reply = (lines, verdict = 'revise') => `prose\n\n\`\`\`verdict\nVERDICT: ${verdict}\nBLOCKING: 1\nADVISORY: 0\n${lines.join('\n')}\n\`\`\`\n`

test('parseVerdict reads the last verdict block', () => {
  const text = `${reply(['- [A] old.java:1 — x — y'], 'approve')}\n${reply(['- [B] api/src/A.java:12 — swallows the exception — rethrow', '- [A] b.js:3 - style - fix'])}`
  const v = parseVerdict(text)
  assert.equal(v.verdict, 'revise')
  assert.deepEqual(v.findings.map((f) => [f.level, f.location]), [['B', 'api/src/A.java:12'], ['A', 'b.js:3']])
  assert.equal(parseVerdict('no block'), null)
})

test('grade needs a blocking finding on an expected file matching a pattern', () => {
  const expect = { files: ['jobs/AppScheduler.java'], patterns: ['swallow', 'never (re)?throw'] }
  const v = (lines) => parseVerdict(reply(lines))
  assert.equal(grade(v(['- [B] api/src/main/java/x/jobs/AppScheduler.java:40 — catch block swallows errors — rethrow']), expect), true)
  assert.equal(grade(v(['- [A] api/src/main/java/x/jobs/AppScheduler.java:40 — swallows errors — rethrow']), expect), false, 'advisory only')
  assert.equal(grade(v(['- [B] api/src/main/java/x/jobs/Other.java:40 — swallows errors — rethrow']), expect), false, 'wrong file')
  assert.equal(grade(v(['- [B] api/src/main/java/x/jobs/AppScheduler.java:40 — naming — rename']), expect), false, 'wrong finding')
  assert.equal(grade(v(['- [B] api\\src\\main\\java\\x\\jobs\\AppScheduler.java — Never rethrows — fix']), expect), true, 'windows path, case')
  assert.equal(grade(null, expect), false)
})

test('pathViolations enforces the ADR-0027 D2 allowlist', () => {
  const ok = [
    { status: 'M', path: '.claude/skills/swarm-review/SKILL.md' },
    { status: 'A', path: '.claude/skills/autopilot/resources/review-checklist.md' },
    { status: 'M', path: '.claude/rules/code-quality.md' },
    { status: 'M', path: '.claude/agents/worker-reviewer.md' },
    { status: 'A', path: 'evals/seeded/2026-10-19-x/case.json' },
  ]
  assert.deepEqual(pathViolations(ok), [])
  const bad = pathViolations([
    { status: 'M', path: 'scripts/agent-metrics.mjs' },
    { status: 'M', path: '.github/workflows/agent-evolve.yml' },
    { status: 'M', path: '.claude/settings.json' },
    { status: 'M', path: '.claude/hooks/pre-tool-use-validator.sh' },
    { status: 'M', path: 'evals/seeded/old/case.json' },
    { status: 'D', path: '.claude/rules/security.md' },
    { status: 'M', path: 'api/.coverage-threshold' },
    { status: 'M', path: 'CLAUDE.md' },
  ])
  assert.equal(bad.length, 8)
})

test('pathViolations: rule files may only gain lines, except evolve-marked ones', () => {
  const f = [{ status: 'M', path: '.claude/rules/code-quality.md' }]
  assert.deepEqual(pathViolations(f, { '.claude/rules/code-quality.md': ['- old rule <!-- evolve:case-1 -->', ''] }), [])
  assert.equal(pathViolations(f, { '.claude/rules/code-quality.md': ['- Tests pass'] }).length, 1)
  const a = [{ status: 'M', path: '.claude/agents/worker-reviewer.md' }]
  assert.equal(pathViolations(a, {}, { '.claude/agents/worker-reviewer.md': ['---\nmodel: sonnet\n---', '---\nmodel: opus\n---'] }).length, 1)
})

test('removedLines parses a -U0 patch', () => {
  const patch = [
    'diff --git a/x.md b/x.md', '--- a/x.md', '+++ b/x.md', '@@ -3 +3,2 @@', '-old line', '+new line', '+more',
    'diff --git a/n.md b/n.md', '--- /dev/null', '+++ b/n.md', '@@ -0,0 +1 @@', '+added',
  ].join('\n')
  assert.deepEqual(removedLines(patch), { 'x.md': ['old line'] })
})

test('signals already handled by an evolve PR or issue are skipped', () => {
  const bodies = ['x <!-- evolve:signal=defect:62526c97 --> y', null, '<!-- evolve:signal=blocked:run-a -->']
  assert.deepEqual([...handledIds(bodies)], ['defect:62526c97', 'blocked:run-a'])
  const sig = ['defect:62526c97', 'defect:1', 'defect:2', 'defect:3', 'defect:4'].map((id) => ({ id }))
  assert.deepEqual(pickSignals(sig, bodies, 3).map((s) => s.id), ['defect:1', 'defect:2', 'defect:3'])
})

const r = (caught, costUsd = 0.1, error = null) => ({ caught, costUsd, error })
const t = (base, head) => ({ base, head })

test('decide: a rule change passes only when all four rules hold', () => {
  const miss3 = [r(false), r(false), r(false)]
  const hit3 = [r(true), r(true), r(true)]
  assert.equal(decide({ rulesChanged: true, target: t([r(false), r(false), r(true)], [r(true), r(true), r(false)]), regress: { old: [r(true)] } }).pass, true)
  assert.equal(decide({ rulesChanged: true, target: t([r(true), r(true), r(false)], hit3), regress: {} }).pass, false, 'rule 1: main already catches it')
  assert.equal(decide({ rulesChanged: true, target: t(miss3, [r(true), r(false), r(false)]), regress: {} }).pass, false, 'rule 2')
  assert.equal(decide({ rulesChanged: true, target: t(miss3, hit3), regress: { old: [r(false), r(false)] } }).pass, false, 'rule 3')
  assert.equal(decide({ rulesChanged: true, target: t(miss3, hit3), regress: { old: [r(false), r(true)] } }).pass, true, 'retry catch counts')
  const cheap = [r(false, 0.1), r(false, 0.1), r(false, 0.1)]
  const costly = [r(true, 0.2), r(true, 0.2), r(true, 0.2)]
  assert.equal(decide({ rulesChanged: true, target: t(cheap, costly), regress: {} }).pass, false, 'rule 4')
  assert.equal(decide({ rulesChanged: true, target: t([r(false, 0, 'timeout'), r(false), r(false)], hit3), regress: {} }).pass, false, 'reviewer errors fail closed')
})

test('decide: a case-only proposal needs main to catch it', () => {
  assert.equal(decide({ rulesChanged: false, target: t([r(true), r(true), r(false)], []), regress: {} }).pass, true)
  assert.equal(decide({ rulesChanged: false, target: t([r(false), r(true), r(false)], []), regress: {} }).pass, false)
})

test('reviewPrompt names the diff and the verdict contract', () => {
  const p = reviewPrompt({ base: 'abc^', head: 'abc' })
  assert.match(p, /git diff abc\^ abc/)
  assert.match(p, /verdict\.md/)
})

test('parseArgs validates input', () => {
  assert.equal(parseArgs(['gate', '--base', 'origin/main', '--head', 'HEAD', '--case', 'a-1', '--runs', '3']).runs, 3)
  assert.throws(() => parseArgs(['gate', '--case', 'a b']), /case/)
  assert.throws(() => parseArgs(['gate', '--runs', '0']), /positive/)
  assert.throws(() => parseArgs(['gate', '--base']), /needs a value/)
  assert.throws(() => parseArgs(['gate', '--rm']), /unknown/)
})
