// Run: node --test scripts/__tests__/evolve.test.mjs
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  compressViolations, decide, decideCompress, grade, handledIds, lintRules, parseArgs, parseVerdict, pathViolations, pickSignals,
  removedLines, reviewPrompt, stagedFiles, validateManifest,
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
  assert.deepEqual(pickSignals(sig, bodies, 2, ['defect:1']).map((s) => s.id), ['defect:2', 'defect:3'], 'seeded case on main')
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

test('pathViolations freezes frontmatter and blocks new agents', () => {
  const skill = '.claude/skills/testing/SKILL.md'
  const fm = '---\nname: testing\ndescription: x\n---'
  assert.deepEqual(pathViolations([{ status: 'M', path: skill }], {}, { [skill]: [fm, fm] }), [])
  assert.equal(pathViolations([{ status: 'M', path: skill }], {}, { [skill]: [fm, '---\nname: testing\ndescription: x\nallowed-tools: Bash\n---'] }).length, 1)
  const fresh = '.claude/skills/new/SKILL.md'
  assert.deepEqual(pathViolations([{ status: 'A', path: fresh }], {}, { [fresh]: ['', '---\nname: new\ndescription: y\n---'] }), [])
  assert.match(pathViolations([{ status: 'A', path: fresh }], {}, { [fresh]: ['', '---\nname: new\nhooks: x\n---'] })[0], /hooks/)
  assert.match(pathViolations([{ status: 'A', path: '.claude/agents/worker-evil.md' }])[0], /new agents/)
})

test('pathViolations: not-eval-proven cannot touch the reviewer rules', () => {
  const f = [{ status: 'M', path: '.claude/agents/worker-reviewer.md' }]
  assert.deepEqual(pathViolations(f, {}, {}, 'rule'), [])
  assert.equal(pathViolations(f, {}, {}, 'not-eval-proven').length, 1)
  assert.deepEqual(pathViolations([{ status: 'M', path: '.claude/skills/testing/SKILL.md' }], {}, {}, 'not-eval-proven'), [])
})


test('parseArgs rejects refs that look like options', () => {
  assert.throws(() => parseArgs(['gate', '--base', '--upload-pack=x']), /cannot start/)
  assert.throws(() => parseArgs(['check-paths', '--kind', 'other']), /kind/)
})

test('compress may edit CLAUDE.md and remove lines, but not protected files or lines', () => {
  const f = (path, status = 'M') => [{ status, path }]
  assert.deepEqual(compressViolations(f('CLAUDE.md'), { 'CLAUDE.md': ['- Use Haiku for searches', '1. Use Haiku for: grep, find'] }), [])
  assert.match(compressViolations(f('CLAUDE.md'), { 'CLAUDE.md': ['6. Ship It — ask before every `git push`'] })[0], /protected line/)
  assert.match(compressViolations(f('.claude/rules/security.md'))[0], /protected file/)
  assert.match(compressViolations(f('.claude/skills/evolve/SKILL.md'))[0], /protected file/)
  assert.match(compressViolations(f('AGENTS.md'))[0], /allowlist/)
  assert.match(compressViolations(f('scripts/evolve.mjs'))[0], /allowlist/)
  assert.match(compressViolations(f('.claude/rules/new.md', 'A'))[0], /existing files/)
  assert.match(compressViolations(f('.claude/rules/code-quality.md'), {}, { '.claude/rules/code-quality.md': ['---\na: 1\n---', ''] })[0], /frontmatter/)
  assert.equal(pathViolations(f('CLAUDE.md'), {}, {}, 'rule').length, 1, 'CLAUDE.md stays out of rule proposals')
})

test('lintRules finds duplicate lines, dead path references and the always-loaded size', () => {
  const files = {
    'CLAUDE.md': 'Use the cheapest model for each task and batch similar operations\nSee `docs/adr/0001-x.md` and `scripts/gone.sh`\n',
    '.claude/rules/a.md': '- **Use the cheapest model** for each task and batch similar operations\n`api/src/A.java` `<id>/x.md` `scratchpad/x.md` `a@1.0/b.yml`\n',
    '.claude/skills/s/SKILL.md': 'short line\n',
  }
  const l = lintRules(files, ['docs/adr/0001-x.md', 'api/src/A.java', 'CLAUDE.md'], ['CLAUDE.md', '.claude/rules/a.md'])
  assert.equal(l.duplicates.length, 1)
  assert.deepEqual(l.duplicates[0].at, ['CLAUDE.md:1', '.claude/rules/a.md:1'])
  assert.deepEqual(l.deadRefs.map((d) => d.ref), ['scripts/gone.sh'])
  assert.equal(l.alwaysLoadedBytes, Buffer.byteLength(files['CLAUDE.md']) + Buffer.byteLength(files['.claude/rules/a.md']))
})

test('decideCompress needs a real cut, no new lint findings and every case caught', () => {
  const ok = { caught: true, error: null }
  const size = (tb, th, ab, ah) => ({ touchedBase: tb, touchedHead: th, alwaysBase: ab, alwaysHead: ah })
  const lint = (bd, hd, br = 0, hr = 0) => ({ base: { duplicates: bd, deadRefs: br }, head: { duplicates: hd, deadRefs: hr } })
  assert.equal(decideCompress({ size: size(9000, 7000, 37000, 35000), lint: lint(5, 3), regress: { a: [ok] } }).pass, true)
  assert.equal(decideCompress({ size: size(9000, 8900, 37000, 36900), lint: lint(5, 3), regress: { a: [ok] } }).pass, false, 'cut too small')
  assert.equal(decideCompress({ size: size(9000, 9500, 37000, 37500), lint: lint(5, 3), regress: { a: [ok] } }).pass, false, 'grew')
  assert.equal(decideCompress({ size: size(9000, 7000, 37000, 35000), lint: lint(5, 6), regress: { a: [ok] } }).pass, false, 'new duplicate')
  assert.equal(decideCompress({ size: size(9000, 7000, 37000, 35000), lint: lint(5, 3, 1, 2), regress: { a: [ok] } }).pass, false, 'new dead ref')
  assert.equal(decideCompress({ size: size(9000, 7000, 37000, 35000), lint: lint(5, 3), regress: { a: [{ caught: false }, { caught: false }] } }).pass, false, 'regression')
  assert.equal(decideCompress({ size: size(9000, 7000, 37000, 35000), lint: lint(5, 3), regress: {} }).pass, false, 'no eval cases')
  assert.equal(decideCompress({ size: size(9000, 7000, 37000, 35000), lint: lint(5, 3), regress: { a: [{ caught: true, error: 'x' }] } }).pass, false, 'errors')
})


test('validateManifest keeps well-formed rows; only a none row closes a signal', () => {
  const ok = { signal: 'defect:aaaa1111', slug: 'defect-aaaa1111', case: '2026-10-01-a', kind: 'rule', body: 'evolve-out/a.md', message: 'evolve: rule' }
  const rows = [
    ok,
    { ...ok, slug: 'dup' },
    { ...ok, signal: 'defect:bbbb2222', slug: 'b; rm -rf /' },
    { ...ok, signal: 'defect:cccc3333', slug: 'c', body: '../etc/passwd' },
    { ...ok, signal: 'defect:dddd4444', slug: 'defect-aaaa1111' },
    { ...ok, signal: 'defect:eeee5555', slug: 'e', message: 'two\nlines' },
    { signal: 'defect:ffff6666', kind: 'none', body: 'evolve-out/f.md' },
    { signal: 'compress:2026-10', slug: 'compress-2026-10', case: '-', kind: 'compress', body: 'evolve-out/c.md', message: 'evolve: compress' },
    { signal: 'defect:zzzz', slug: 'z', case: 'z', kind: 'rule', body: 'evolve-out/z.md', message: 'm' },
    null,
  ]
  const ids = ['defect:aaaa1111', 'defect:bbbb2222', 'defect:cccc3333', 'defect:dddd4444', 'defect:eeee5555', 'defect:ffff6666', 'compress:2026-10', 'defect:gggg7777', 'bad id\n']
  const res = validateManifest(rows, ids)
  assert.deepEqual(res.rows.map((r) => r.slug), ['defect-aaaa1111', 'compress-2026-10'])
  assert.deepEqual(res.none, [{ signal: 'defect:ffff6666', body: 'evolve-out/f.md' }])
  assert.deepEqual(res.unhandled, ['defect:bbbb2222', 'defect:cccc3333', 'defect:dddd4444', 'defect:eeee5555', 'defect:gggg7777'])
  assert.deepEqual(validateManifest('not an array', ['defect:a']).unhandled, ['defect:a'])
  assert.deepEqual(validateManifest([{ ...rows[7], case: 'x' }], ids).rows, [])
  for (const slug of ['.', '..', 'a..b', '.hidden', 'x.lock', '-x']) assert.deepEqual(validateManifest([{ ...ok, slug }], ids).rows, [], slug)
  assert.equal(validateManifest([{ ...ok, slug: 'defect-a.1' }], ids).rows.length, 1)
})

test('stagedFiles decodes allowed paths and rejects the rest', () => {
  const f = (rel, size = 10, file = true) => ({ rel, size, file })
  const res = stagedFiles([
    f('dot-claude/agents/worker-reviewer.md.staged'),
    f('CLAUDE.md.staged'),
    f('evals/seeded/2026-10-01-a/case.json.staged'),
    f('dot-claude/rules/x.md'),
    f('dot-claude/../.github/workflows/x.yml.staged'),
    f('.claude/rules/x.md.staged'),
    f('scripts/evolve.mjs.staged'),
    f('dot-claude/rules/big.md.staged', 70000),
    f('dot-claude/rules/link.md.staged', 10, false),
  ])
  assert.deepEqual(res.files.map((x) => x.path), ['.claude/agents/worker-reviewer.md', 'CLAUDE.md', 'evals/seeded/2026-10-01-a/case.json'])
  assert.equal(res.errors.length, 6)
  assert.deepEqual(stagedFiles([]).errors, ['no staged files'])
})

test('apply commits staged files on a new branch without touching the working tree', () => {
  const dir = mkdtempSync(join(tmpdir(), 'evolve-apply-'))
  const script = join(dirname(fileURLToPath(import.meta.url)), '..', 'evolve.mjs')
  const env = { ...process.env, GIT_AUTHOR_NAME: 't', GIT_AUTHOR_EMAIL: 't@t', GIT_COMMITTER_NAME: 't', GIT_COMMITTER_EMAIL: 't@t' }
  const g = (...a) => execFileSync('git', ['-C', dir, ...a], { env, encoding: 'utf8' }).trim()
  const run = (...a) => execFileSync('node', [script, ...a], { cwd: dir, env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
  try {
    g('init', '-q', '-b', 'main')
    mkdirSync(join(dir, '.claude/rules'), { recursive: true })
    writeFileSync(join(dir, '.claude/rules/x.md'), 'one\n')
    g('add', '.'); g('commit', '-qm', 'base')
    const stage = join(dir, 'evolve-out/p/tree/dot-claude/rules')
    mkdirSync(stage, { recursive: true })
    writeFileSync(join(stage, 'x.md.staged'), 'one\ntwo\n')
    run('apply', '--dir', 'evolve-out/p', '--base', 'main', '--branch', 'evolve/p', '--message', 'evolve: add two')
    assert.equal(g('show', 'evolve/p:.claude/rules/x.md'), 'one\ntwo')
    assert.equal(g('log', '-1', '--format=%s', 'evolve/p'), 'evolve: add two')
    assert.equal(g('rev-parse', 'evolve/p^'), g('rev-parse', 'main'))
    assert.equal(g('status', '--porcelain', '--untracked-files=no'), '')
    assert.throws(() => run('apply', '--dir', 'evolve-out/p', '--base', 'main', '--branch', 'evolve/p', '--message', 'again'))
    writeFileSync(join(stage, 'x.md.staged'), 'one\n')
    assert.throws(() => run('apply', '--dir', 'evolve-out/p', '--base', 'main', '--branch', 'evolve/q', '--message', 'noop'), /change nothing/)
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})
