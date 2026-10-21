// Evolve loop helpers (ADR-0027). The headless workflow runs a copy of this file taken from `main` before the
// agent step, so nothing the agent writes changes how its proposal is checked.
//
// Usage: node scripts/evolve.mjs <command> [options]
//   signals     [--from metrics.json] [--since YYYY-MM-DD] [--limit 3] [--out file]
//               new signals from agent-metrics --json, minus ids that already have an `evolve` PR or issue or a seeded case
//   check-paths --base <ref> --head <ref> [--kind rule|case-only|not-eval-proven|compress]
//               ADR D2 allowlist; rule files may only gain lines (or lose `<!-- evolve:` lines, the prune path);
//               frontmatter is frozen; not-eval-proven may not touch the reviewer's rules;
//               compress (D6) may also edit CLAUDE.md and remove lines, but never protected files or lines
//   lint-rules  [--rules <ref>] [--out file]   duplicate lines, dead path references, always-loaded context size (D6)
//   manifest    --signals <trusted signals.json> --manifest <file> --rows <out.tsv> --unhandled <out.txt>
//               validate the agent-written manifest (headless ship job)
//   eval        --case <id> --rules <ref> [--case-ref <ref>] [--runs 1] [--budget 1]   (case from the working tree unless --case-ref)
//               run the reviewer on the case diff with the .claude/ of <ref>, grade each run
//   gate        --base <ref> --head <ref> --case <id> [--runs 3] [--budget 1] [--out file.md]
//               ADR D3 rules 1-4; exit 0 = pass, 1 = fail
//               --kind compress (no --case): D6 rules (smaller, no new lint findings, every case still caught)
// Reviewer runs use `claude -p --agent worker-reviewer` (needs the claude CLI and a login or CLAUDE_CODE_OAUTH_TOKEN).
import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const CASES_DIR = 'evals/seeded'
const CASE_ID = /^[\w.-]+$/
const SIGNAL_MARK = /<!-- evolve:signal=([^\s>]+) -->/g
const RULE_MARK = '<!-- evolve:'
const RULES_PATHS = ['.claude', 'CLAUDE.md', 'AGENTS.md', '.mcp.json']
const SIGNAL_ID = /^[\w:.-]+$/
const KINDS = ['rule', 'case-only', 'not-eval-proven', 'compress']
// Files that change what the code reviewer does; a rule there must be eval-proven
const REVIEWER_RULES = [/^\.claude\/agents\/worker-reviewer\.md$/, /^\.claude\/skills\/swarm-review\//, /^\.claude\/skills\/autopilot\/resources\/verdict\.md$/]
const NEW_SKILL_KEYS = new Set(['name', 'description', 'argument-hint', 'disable-model-invocation'])

// ADR-0027 D2
const ALLOWED = [
  /^\.claude\/skills\/.+\/SKILL\.md$/,
  /^\.claude\/skills\/.+\/resources\/[^/]+\.md$/,
  /^\.claude\/rules\/[^/]+\.md$/,
  /^\.claude\/agents\/[^/]+\.md$/,
]
const NEW_CASE = /^evals\/seeded\/[\w.-]+\/(case\.json|notes\.md)$/

// ADR-0027 D6: compress proposals remove duplicate, dead or contradicting text. Wider paths and removals are allowed,
// but protected files and lines that carry a safety rule are never removed or edited.
const COMPRESS_ALLOWED = [...ALLOWED, /^CLAUDE\.md$/]
const PROTECTED_FILES = [/^\.claude\/rules\/security\.md$/, /^\.claude\/skills\/evolve\//, /^\.claude\/skills\/autopilot\/resources\/verdict\.md$/]
const PROTECTED_LINE = /git push|force-push|force push|ask before|\bgate [12]\b|never merge|auto-merge|permissions\.deny|\bdeny\b|denylist|secret|token|credential|untrusted|injection|\bprod\b|production|smoke|security|core principle|\bnever\b/i
const RULE_FILES = (f) => /^(CLAUDE|AGENTS)\.md$/.test(f) || /^\.claude\/(rules|agents)\/[^/]+\.md$/.test(f) || /^\.claude\/skills\/.+\.md$/.test(f)
const MIN_ALWAYS_CUT = 0.05
const MIN_BYTES_CUT = 1024

export function parseArgs(argv) {
  const [command, ...rest] = argv
  const args = { command, limit: 3, runs: null, budget: 1 }
  for (let i = 0; i < rest.length; i++) {
    const a = rest[i]
    const key = a.replace(/^--/, '').replace(/-(\w)/g, (_, c) => c.toUpperCase())
    if (!['--from', '--since', '--limit', '--out', '--base', '--head', '--case', '--rules', '--case-ref', '--runs', '--budget',
      '--kind', '--signals', '--manifest', '--rows', '--unhandled'].includes(a)) {
      throw new Error(`unknown argument: ${a}`)
    }
    if (rest[i + 1] === undefined) throw new Error(`${a} needs a value`)
    args[key] = rest[++i]
  }
  for (const k of ['limit', 'runs', 'budget']) {
    if (args[k] != null) {
      args[k] = Number(args[k])
      if (!(args[k] > 0)) throw new Error(`--${k} must be a positive number`)
    }
  }
  if (args.case && !CASE_ID.test(args.case)) throw new Error('--case must match [\\w.-]+')
  if (args.kind && !KINDS.includes(args.kind)) throw new Error(`--kind must be one of ${KINDS.join(', ')}`)
  for (const k of ['base', 'head', 'rules', 'caseRef']) if (args[k] && args[k].startsWith('-')) throw new Error(`--${k} cannot start with "-"`)
  return args
}

// ---- signals ----

export function handledIds(bodies) {
  return new Set(bodies.flatMap((b) => [...(b ?? '').matchAll(SIGNAL_MARK)].map((m) => m[1])))
}

// caseSignals: the `signal` of every seeded case already on main (a proposal merged without a PR marker)
export function pickSignals(signals, bodies, limit, caseSignals = []) {
  const done = new Set([...handledIds(bodies), ...caseSignals])
  return signals.filter((s) => !done.has(s.id)).slice(0, limit)
}

// ---- check-paths ----

const frontmatter = (text) => (/^---\r?\n[\s\S]*?\r?\n---/.exec(text ?? '') ?? [''])[0]

const frontKeys = (fm) => fm.split('\n').slice(1, -1).map((l) => (/^([\w-]+)\s*:/.exec(l) ?? [])[1]).filter(Boolean)

// files: [{ status, path }]; removed: { path: [removed lines] }; front: { path: [before, after] } (frontmatter of rule files)
export function pathViolations(files, removed = {}, front = {}, kind = 'rule') {
  const v = []
  if (kind === 'compress') return compressViolations(files, removed, front)
  if (kind === 'not-eval-proven') {
    for (const { path } of files) if (REVIEWER_RULES.some((re) => re.test(path))) v.push(`${path}: changes the code reviewer, so it must be eval-proven`)
  }
  for (const { status, path } of files) {
    if (NEW_CASE.test(path)) {
      if (status !== 'A') v.push(`${path}: existing eval cases cannot be changed or deleted (status ${status})`)
      continue
    }
    if (!ALLOWED.some((re) => re.test(path))) { v.push(`${path}: outside the Evolve allowlist`); continue }
    if (status === 'D') { v.push(`${path}: rule files cannot be deleted`); continue }
    if (status === 'A' && /^\.claude\/agents\//.test(path)) { v.push(`${path}: new agents cannot be added`); continue }
    const lost = (removed[path] ?? []).filter((l) => l.trim() && !l.includes(RULE_MARK))
    if (lost.length) v.push(`${path}: ${lost.length} existing line(s) removed or edited; only additions (or removal of ${RULE_MARK} lines) are allowed`)
    const [before, after] = front[path] ?? ['', '']
    if (status === 'A') {
      const extra = frontKeys(after).filter((k) => !NEW_SKILL_KEYS.has(k))
      if (extra.length) v.push(`${path}: frontmatter keys not allowed in a new file: ${extra.join(', ')}`)
    } else if (before !== after) v.push(`${path}: frontmatter cannot change`)
  }
  return v
}

// D6: removals allowed, protected files and lines are not; frontmatter frozen; no new files or cases
export function compressViolations(files, removed = {}, front = {}) {
  const v = []
  for (const { status, path } of files) {
    if (!COMPRESS_ALLOWED.some((re) => re.test(path))) { v.push(`${path}: outside the compress allowlist`); continue }
    if (PROTECTED_FILES.some((re) => re.test(path))) { v.push(`${path}: protected file`); continue }
    if (status !== 'M') { v.push(`${path}: compress may only edit existing files (status ${status})`); continue }
    const hit = (removed[path] ?? []).filter((l) => PROTECTED_LINE.test(l))
    if (hit.length) v.push(`${path}: ${hit.length} protected line(s) removed or edited, e.g. "${hit[0].trim().slice(0, 80)}"`)
    const [before, after] = front[path] ?? ['', '']
    if (before !== after) v.push(`${path}: frontmatter cannot change`)
  }
  return v
}

// ---- lint-rules (D6) ----

const normLine = (l) => l.replace(/^[\s>#*|-]*(\d+\.\s+)?/, '').replace(/[`*_]/g, '').replace(/\s+/g, ' ').trim().toLowerCase()
const PATHISH = /^[\w.-]+(\/[\w.-]+)*\/?$/
const SKIP_REF = /^(scratchpad|evolve-out|node_modules|target|dist)\/|^~|^\.\.?\//

// files: { path: text }; tracked: [paths in the tree]; alwaysLoaded: [paths]
export function lintRules(files, tracked, alwaysLoaded) {
  const seen = new Map()
  const deadRefs = []
  const exists = (t) => {
    const x = t.replace(/\/$/, '')
    return tracked.some((f) => f === x || f.startsWith(`${x}/`) || f.endsWith(`/${x}`) || f.includes(`/${x}/`))
  }
  for (const [path, text] of Object.entries(files)) {
    text.split('\n').forEach((line, i) => {
      if (/^\s*\|?\s*-{3,}/.test(line) || /^\s*```/.test(line)) return
      const n = normLine(line)
      if (n.length >= 40) { if (!seen.has(n)) seen.set(n, []); seen.get(n).push(`${path}:${i + 1}`) }
      for (const m of line.matchAll(/`([^`\s]+)`/g)) {
        const t = m[1].replace(/:\d+(-\d+)?$/, '')
        if (!t.includes('/') || !PATHISH.test(t) || SKIP_REF.test(t) || /(NNNN|YYYY|<|\{|\*)/.test(t)) continue
        if (!/\.\w{1,5}$/.test(t) && !t.endsWith('/')) continue
        if (!exists(t)) deadRefs.push({ at: `${path}:${i + 1}`, ref: t })
      }
    })
  }
  const duplicates = [...seen.entries()].filter(([, at]) => at.length > 1).map(([text, at]) => ({ text: text.slice(0, 100), at }))
  const alwaysLoadedBytes = alwaysLoaded.reduce((t, f) => t + Buffer.byteLength(files[f] ?? '', 'utf8'), 0)
  return { duplicates, deadRefs, alwaysLoadedBytes }
}

function lintAt(root, ref) {
  const tracked = git(root, ['ls-tree', '-r', '--name-only', ref]).split('\n').filter(Boolean)
  const files = Object.fromEntries(tracked.filter(RULE_FILES).map((f) => [f, show(root, ref, f).replace(/\r/g, '')]))
  // Always loaded = CLAUDE.md + rules without a `paths:` scope
  const always = Object.keys(files).filter((f) => f === 'CLAUDE.md' || (/^\.claude\/rules\//.test(f) && !/^paths:/m.test(frontmatter(files[f]))))
  return { ...lintRules(files, tracked, always), files }
}

// size: { touchedBase, touchedHead, alwaysBase, alwaysHead }; lint: { base, head } counts; regress: { caseId: [results] }
export function decideCompress({ size, lint, regress }) {
  const cut = size.touchedBase - size.touchedHead
  const alwaysCut = size.alwaysBase ? (size.alwaysBase - size.alwaysHead) / size.alwaysBase : 0
  const errors = Object.values(regress).flat().filter((r) => r.error)
  const lost = Object.entries(regress).filter(([, rs]) => !rs.some((r) => r.caught)).map(([id]) => id)
  const checks = [
    { rule: 'C1. smaller: always-loaded context -5% or 1 KiB less in the touched files', ok: cut > 0 && (alwaysCut >= MIN_ALWAYS_CUT || cut >= MIN_BYTES_CUT),
      detail: `touched ${size.touchedBase} → ${size.touchedHead} B, always-loaded ${size.alwaysBase} → ${size.alwaysHead} B (${(alwaysCut * 100).toFixed(1)}%)` },
    { rule: 'C2. no new duplicate lines or dead references', ok: lint.head.duplicates <= lint.base.duplicates && lint.head.deadRefs <= lint.base.deadRefs,
      detail: `duplicates ${lint.base.duplicates} → ${lint.head.duplicates}, dead refs ${lint.base.deadRefs} → ${lint.head.deadRefs}` },
    { rule: 'C3. every seeded case still caught', ok: lost.length === 0 && Object.keys(regress).length > 0,
      detail: lost.length ? `missed: ${lost.join(', ')}` : `${Object.keys(regress).length} case(s)` },
  ]
  if (errors.length) checks.push({ rule: 'runs completed', ok: false, detail: `${errors.length} reviewer run(s) failed` })
  return { pass: checks.every((c) => c.ok), kind: 'compress', checks }
}

function compressGate(root, args) {
  const { files, violations } = checkPaths(root, args.base, args.head, 'compress')
  if (violations.length) throw new Error(`check-paths failed:\n${violations.join('\n')}`)
  if (!files.length) throw new Error('the branch changes nothing')
  const lb = lintAt(root, args.base)
  const lh = lintAt(root, args.head)
  const bytes = (l, ps) => ps.reduce((t, p) => t + Buffer.byteLength(l.files[p] ?? '', 'utf8'), 0)
  const touched = files.map((f) => f.path)
  const size = { touchedBase: bytes(lb, touched), touchedHead: bytes(lh, touched), alwaysBase: lb.alwaysLoadedBytes, alwaysHead: lh.alwaysLoadedBytes }
  const regress = {}
  for (const id of listCases(root, args.base)) {
    const c = loadCase(root, args.base, id)
    regress[id] = evalCase(root, c, args.head, 1, args.budget)
    if (!regress[id][0].caught) regress[id].push(...evalCase(root, c, args.head, 1, args.budget).map((r) => ({ ...r, run: 2 })))
  }
  const d = decideCompress({
    size, regress,
    lint: { base: { duplicates: lb.duplicates.length, deadRefs: lb.deadRefs.length }, head: { duplicates: lh.duplicates.length, deadRefs: lh.deadRefs.length } },
  })
  return { d, md: renderGate('compress', d, { base: [], head: [] }, regress) }
}

// `git diff -U0` output → removed lines per file
export function removedLines(patch) {
  const out = {}
  let file = null
  for (const line of patch.split('\n')) {
    const m = /^--- (?:a\/(.+)|\/dev\/null)$/.exec(line)
    if (m) { file = m[1] ?? null; continue }
    if (line.startsWith('+++ ')) { if (!file) file = line.slice(6); continue }
    if (file && line.startsWith('-') && !line.startsWith('---')) (out[file] ??= []).push(line.slice(1))
  }
  return out
}

function checkPaths(root, base, head, kind = 'rule') {
  const names = git(root, ['diff', '--name-status', '--no-renames', `${base}...${head}`]).split('\n').filter(Boolean)
  const files = names.map((l) => { const [status, path] = l.split('\t'); return { status: status[0], path } })
  const removed = removedLines(git(root, ['diff', '-U0', '--no-renames', `${base}...${head}`, '--', ...files.map((f) => f.path)]))
  const front = {}
  for (const f of files.filter((x) => /^\.claude\/.+\.md$/.test(x.path) && x.status !== 'D')) {
    front[f.path] = [frontmatter(show(root, base, f.path)), frontmatter(show(root, head, f.path))].map((x) => x.replace(/\r/g, ''))
  }
  return { files, violations: pathViolations(files, removed, front, kind) }
}

// ---- manifest (headless ship job) ----

// rows: agent-written manifest; signalIds: from the trusted copy taken before the agent step
export function validateManifest(rows, signalIds) {
  const signals = new Set(signalIds.filter((id) => SIGNAL_ID.test(id)))
  const ok = []
  const seen = new Set()
  for (const r of Array.isArray(rows) ? rows : []) {
    if (!r || typeof r !== 'object') continue
    const caseOk = r.kind === 'compress' ? r.case === '-' : CASE_ID.test(r.case ?? '') && r.case !== '-'
    const valid = signals.has(r.signal) && !seen.has(r.signal) && /^evolve\/[\w.-]+$/.test(r.branch ?? '') && caseOk
      && KINDS.includes(r.kind) && /^evolve-out\/[\w.-]+\.md$/.test(r.body ?? '')
    if (valid) { seen.add(r.signal); ok.push({ signal: r.signal, branch: r.branch, case: r.case, kind: r.kind, body: r.body }) }
  }
  return { rows: ok, unhandled: [...signals].filter((x) => !seen.has(x)) }
}

// ---- eval ----

export function parseVerdict(text) {
  const blocks = [...(text ?? '').matchAll(/```verdict\s*\n([\s\S]*?)```/g)]
  if (!blocks.length) return null
  const body = blocks.at(-1)[1]
  const verdict = (/^\s*VERDICT:\s*(\w+)/im.exec(body) ?? [])[1]?.toLowerCase() ?? null
  const findings = body.split('\n').map((l) => /^\s*-\s*\[(B|A)\]\s*(\S+?)\s+[—-]+\s+(.*)$/.exec(l)).filter(Boolean)
    .map((m) => ({ level: m[1], location: m[2], text: m[3] }))
  return { verdict, findings }
}

// caught = a blocking finding on an expected file whose text matches an expected pattern (ADR-0027 D3)
export function grade(verdict, expect) {
  if (!verdict) return false
  const res = (expect.patterns ?? []).map((p) => new RegExp(p, 'i'))
  return verdict.findings.some((f) => {
    if (f.level !== 'B') return false
    const path = f.location.replace(/:\d+(-\d+)?$/, '').replace(/\\/g, '/')
    const onFile = (expect.files ?? []).some((e) => path === e || path.endsWith(`/${e}`) || e.endsWith(`/${path}`))
    return onFile && res.some((re) => re.test(f.text))
  })
}

export function reviewPrompt(c) {
  return [
    `Code review (focus quality) of the change \`git diff ${c.base} ${c.head}\`. The working tree is checked out at ${c.head}.`,
    'Read the changed files and whatever existing code they depend on. Follow your agent instructions and the review method in',
    '`.claude/skills/swarm-review/SKILL.md`. Do not edit files.',
    'End your reply with the verdict block from `.claude/skills/autopilot/resources/verdict.md`, one line per finding,',
    'with the file path relative to the repo root.',
  ].join('\n')
}

// ref null = the working tree (authoring a case locally)
function loadCase(root, ref, id) {
  const rel = `${CASES_DIR}/${id}/case.json`
  const raw = ref ? show(root, ref, rel) : readFileSync(join(root, rel), 'utf8')
  if (!raw) throw new Error(`case ${id} not found at ${ref ?? 'the working tree'}`)
  const c = JSON.parse(raw)
  for (const k of ['base', 'head']) if (typeof c[k] !== 'string' || !/^\w[\w^~./-]*$/.test(c[k])) throw new Error(`case ${id}: bad ${k}`)
  if (!c.expect?.files?.length || !c.expect?.patterns?.length) throw new Error(`case ${id}: expect.files and expect.patterns are required`)
  return { id, ...c }
}

function listCases(root, ref) {
  try {
    return git(root, ['ls-tree', '--name-only', `${ref}:${CASES_DIR}`]).split('\n').filter((x) => CASE_ID.test(x))
  } catch { return [] }
}

function runReviewer(root, c, rulesRef, budget) {
  const dir = mkdtempSync(join(tmpdir(), 'evolve-'))
  try {
    git(root, ['worktree', 'add', '--detach', '--force', dir, c.head])
    for (const p of RULES_PATHS) {
      rmSync(join(dir, p), { recursive: true, force: true })
      if (p === '.claude' || show(root, rulesRef, p)) git(dir, ['checkout', rulesRef, '--', p])
    }
    // The reviewer never sees GitHub tokens
    const env = Object.fromEntries(Object.entries(process.env).filter(([k]) => !/^(GH_TOKEN|GITHUB_TOKEN|GH_ENTERPRISE_TOKEN)$/.test(k)))
    const out = execFileSync('claude', ['-p', '--agent', 'worker-reviewer', '--output-format', 'json',
      '--setting-sources', 'project', '--max-budget-usd', String(budget),
      '--allowedTools', 'Read', 'Grep', 'Glob', 'Bash(git diff:*)', 'Bash(git show:*)', 'Bash(git log:*)'],
    { cwd: dir, env, input: reviewPrompt(c), encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe'] })
    const r = JSON.parse(out)
    return { text: r.result ?? '', costUsd: r.total_cost_usd ?? 0, error: r.is_error ? (r.subtype ?? 'error') : null }
  } catch (e) {
    return { text: '', costUsd: 0, error: e.message.split('\n')[0] }
  } finally {
    try { git(root, ['worktree', 'remove', '--force', dir]) } catch { rmSync(dir, { recursive: true, force: true }) }
  }
}

function evalCase(root, c, rulesRef, runs, budget, log = console.error) {
  const results = []
  for (let i = 1; i <= runs; i++) {
    const r = runReviewer(root, c, rulesRef, budget)
    const v = parseVerdict(r.text)
    const caught = !r.error && grade(v, c.expect)
    results.push({
      case: c.id, rules: rulesRef, run: i, caught, costUsd: r.costUsd, verdict: v?.verdict ?? null,
      error: r.error ?? (v ? null : 'no verdict block'),
      findings: (v?.findings ?? []).map((f) => `[${f.level}] ${f.location} ${f.text.slice(0, 140)}`),
    })
    log(`eval ${c.id} rules=${rulesRef} run ${i}/${runs}: ${r.error ? `error ${r.error}` : caught ? 'caught' : 'missed'} ($${r.costUsd.toFixed(3)})`)
  }
  return results
}

// ---- gate ----

const mean = (xs) => (xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : 0)

// target: { base: [results], head: [results] }; regress: { caseId: [results] }; rulesChanged: boolean
export function decide({ target, regress, rulesChanged }) {
  const caughtBase = target.base.filter((r) => r.caught).length
  const caughtHead = target.head.filter((r) => r.caught).length
  const errors = [...target.base, ...target.head, ...Object.values(regress).flat()].filter((r) => r.error)
  const checks = []
  if (errors.length) checks.push({ rule: 'runs completed', ok: false, detail: `${errors.length} reviewer run(s) failed` })
  if (!rulesChanged) {
    checks.push({ rule: 'case-only: main already catches the case (2 of 3)', ok: caughtBase >= 2, detail: `${caughtBase}/${target.base.length}` })
  } else {
    checks.push({ rule: '1. missed with main rules', ok: caughtBase <= 1, detail: `caught ${caughtBase}/${target.base.length}` })
    checks.push({ rule: '2. caught with branch rules (2 of 3)', ok: caughtHead >= 2, detail: `caught ${caughtHead}/${target.head.length}` })
    const costBase = mean(target.base.map((r) => r.costUsd))
    const costHead = mean(target.head.map((r) => r.costUsd))
    checks.push({ rule: '4. reviewer cost +25% at most', ok: costBase === 0 || costHead <= costBase * 1.25, detail: `$${costBase.toFixed(3)} → $${costHead.toFixed(3)}` })
  }
  const lost = Object.entries(regress).filter(([, rs]) => !rs.some((r) => r.caught)).map(([id]) => id)
  checks.push({ rule: '3. existing cases still caught', ok: lost.length === 0, detail: lost.length ? `missed: ${lost.join(', ')}` : `${Object.keys(regress).length} case(s)` })
  checks.sort((a, b) => a.rule.localeCompare(b.rule))
  return { pass: checks.every((c) => c.ok), kind: rulesChanged ? 'rule' : 'case-only', checks }
}

export function renderGate(caseId, d, target, regress) {
  const L = [`### Eval gate: \`${caseId}\` (${d.kind}) — ${d.pass ? 'PASS' : 'FAIL'}`, '', '| Check | Result | Detail |', '|---|---|---|']
  for (const c of d.checks) L.push(`| ${c.rule} | ${c.ok ? '✅' : '❌'} | ${c.detail} |`)
  L.push('', '| Case | Rules | Run | Caught | Cost |', '|---|---|---|---|---|')
  for (const r of [...target.base, ...target.head, ...Object.values(regress).flat()]) {
    L.push(`| \`${r.case}\` | ${r.rules} | ${r.run} | ${r.error ? `error: ${r.error}` : r.caught ? 'yes' : 'no'} | $${r.costUsd.toFixed(3)} |`)
  }
  return L.join('\n') + '\n'
}

function gate(root, args) {
  const runs = args.runs ?? 3
  const c = loadCase(root, args.head, args.case)
  const { files, violations } = checkPaths(root, args.base, args.head, args.kind ?? 'rule')
  if (violations.length) throw new Error(`check-paths failed:\n${violations.join('\n')}`)
  if (!files.some((f) => f.path === `${CASES_DIR}/${c.id}/case.json` && f.status === 'A')) throw new Error(`case ${c.id} must be added by this branch`)
  for (const k of ['base', 'head']) {
    try { git(root, ['merge-base', '--is-ancestor', c[k].replace(/[\^~].*$/, ''), args.base]) } catch {
      throw new Error(`case ${c.id}: ${k} ${c[k]} is not a commit on ${args.base}`)
    }
  }
  const rulesChanged = files.some((f) => !NEW_CASE.test(f.path))
  const target = {
    base: evalCase(root, c, args.base, runs, args.budget),
    head: rulesChanged ? evalCase(root, c, args.head, runs, args.budget) : [],
  }
  const regress = {}
  for (const id of listCases(root, args.base).filter((x) => x !== c.id)) {
    const old = loadCase(root, args.base, id)
    regress[id] = evalCase(root, old, args.head, 1, args.budget)
    if (!regress[id][0].caught) regress[id].push(...evalCase(root, old, args.head, 1, args.budget).map((r) => ({ ...r, run: 2 })))
  }
  const d = decide({ target, regress, rulesChanged })
  return { d, md: renderGate(c.id, d, target, regress) }
}

// ---- plumbing ----

function git(cwd, argv) {
  return execFileSync('git', ['-C', cwd, ...argv], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'] })
}
function show(root, ref, path) {
  try { return git(root, ['show', `${ref}:${path}`]) } catch { return '' }
}
function ghBodies(kind) {
  try {
    return JSON.parse(execFileSync('gh', [kind, 'list', '--label', 'evolve', '--state', 'all', '--limit', '500', '--json', 'body'],
      { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })).map((x) => x.body)
  } catch (e) {
    throw new Error(`gh ${kind} list failed (needed to skip handled signals): ${e.message.split('\n')[0]}`)
  }
}
const emit = (args, text) => (args.out ? writeFileSync(args.out, text) : process.stdout.write(text))

function main() {
  const args = parseArgs(process.argv.slice(2))
  const root = git(process.cwd(), ['rev-parse', '--show-toplevel']).trim()
  const need = (...ks) => ks.forEach((k) => { if (!args[k]) throw new Error(`${args.command} needs --${k.replace(/[A-Z]/g, (x) => `-${x.toLowerCase()}`)}`) })
  switch (args.command) {
    case 'signals': {
      const metrics = args.from
        ? JSON.parse(readFileSync(args.from, 'utf8'))
        : JSON.parse(execFileSync('node', [join(root, 'scripts/agent-metrics.mjs'), '--json', ...(args.since ? ['--since', args.since] : [])],
          { cwd: root, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }))
      const caseSignals = listCases(root, 'HEAD').map((id) => { try { return loadCase(root, 'HEAD', id).signal } catch { return null } })
      const lint = lintAt(root, 'HEAD')
      const compress = { id: `compress:${new Date().toISOString().slice(0, 7)}`, kind: 'compress',
        duplicates: lint.duplicates.slice(0, 20), deadRefs: lint.deadRefs.slice(0, 20), alwaysLoadedBytes: lint.alwaysLoadedBytes }
      const all = [...(metrics.signals ?? []), compress]  // defects first; at most one compress proposal a month
      const picked = pickSignals(all, [...ghBodies('pr'), ...ghBodies('issue')], args.limit, caseSignals.filter(Boolean))
      emit(args, `${JSON.stringify({ signals: picked }, null, 2)}\n`)
      break
    }
    case 'check-paths': {
      need('base', 'head')
      const { violations } = checkPaths(root, args.base, args.head, args.kind ?? 'rule')
      if (violations.length) { console.error(violations.join('\n')); process.exit(1) }
      console.log('check-paths: ok')
      break
    }
    case 'manifest': {
      need('signals', 'manifest', 'rows', 'unhandled')
      const ids = JSON.parse(readFileSync(args.signals, 'utf8')).signals.map((x) => x.id)
      let rows = []
      try { rows = JSON.parse(readFileSync(args.manifest, 'utf8')) } catch { rows = [] }
      const res = validateManifest(rows, ids)
      writeFileSync(args.rows, res.rows.map((r) => `${[r.signal, r.branch, r.case, r.kind, r.body].join('\t')}\n`).join(''))
      writeFileSync(args.unhandled, res.unhandled.map((x) => `${x}\n`).join(''))
      console.log(`manifest: ${res.rows.length} proposal(s), ${res.unhandled.length} signal(s) without one`)
      break
    }
    case 'lint-rules': {
      const l = lintAt(root, args.rules ?? 'HEAD')
      delete l.files
      emit(args, `${JSON.stringify(l, null, 2)}\n`)
      break
    }
    case 'eval': {
      need('case', 'rules')
      const c = loadCase(root, args.caseRef ?? null, args.case)
      emit(args, `${JSON.stringify(evalCase(root, c, args.rules, args.runs ?? 1, args.budget), null, 2)}\n`)
      break
    }
    case 'gate': {
      need('base', 'head')
      if (args.kind !== 'compress') need('case')
      const { d, md } = args.kind === 'compress' ? compressGate(root, args) : gate(root, args)
      emit(args, md)
      if (!d.pass) process.exit(1)
      break
    }
    default:
      throw new Error('command must be one of: signals, check-paths, manifest, lint-rules, eval, gate')
  }
}

if (process.argv[1] && resolve(fileURLToPath(import.meta.url)).toLowerCase() === resolve(process.argv[1]).toLowerCase()) {
  try { main() } catch (e) { console.error(e.message); process.exit(2) }
}
