// Quality of the agent's work over time, from git history, GitHub and the autopilot run files.
// An agent run is found by (1) the commit trailer `Autopilot-Run: <id>`, (2) a squash-merged headless PR
// ("Autopilot: ... (#N)"), or (3) for runs older than the trailer, the hashes on the "Result:" line of
// docs/reports/autopilot-<id>.md. Per-run pipeline data comes from docs/metrics/agent-runs/<id>.json
// (written by /autopilot at the end of every run).
//
// Escaped defect = a later `fix:` / `fix(...)` commit, not part of the run, within --window days after the
// run's last commit, touching a code file the run changed (docs, tests, TASKS.md and session.txt ignored).
//
// Usage: node scripts/agent-metrics.mjs [--since YYYY-MM-DD] [--until YYYY-MM-DD] [--window 7]
//                                       [--out docs/reports/agent-metrics-YYYY-MM.md] [--no-gh] [--json]
//   --json prints { summary, signals } for /evolve (ADR-0027) instead of markdown
//   default --since = 30 days ago; without --out the markdown goes to stdout; --no-gh skips GitHub calls
import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { isAbsolute, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const DAY = 86_400_000
const RUNS_DIR = 'docs/metrics/agent-runs'
const REPORTS_DIR = 'docs/reports'
const BUILD_WORKFLOW = 'Funny App Build and Deploy'
const AUTOPILOT_WORKFLOW = 'Autopilot (headless)'
const GH_LIMIT = 300

export function parseArgs(argv) {
  const args = { window: 7, gh: true, since: new Date(Date.now() - 30 * DAY).toISOString().slice(0, 10) }
  const value = (i, a) => {
    if (argv[i] === undefined) throw new Error(`${a} needs a value`)
    return argv[i]
  }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--no-gh') args.gh = false
    else if (a === '--json') args.json = true
    else if (a === '--window') args.window = Number(value(++i, a))
    else if (['--since', '--until', '--out'].includes(a)) args[a.slice(2)] = value(++i, a)
    else throw new Error(`unknown argument: ${a}`)
  }
  if (!(args.window > 0)) throw new Error('--window must be a positive number of days')
  for (const k of ['since', 'until']) {
    if (args[k] && !/^\d{4}-\d{2}-\d{2}$/.test(args[k])) throw new Error(`--${k} must be YYYY-MM-DD`)
  }
  return args
}

// `git log --name-only --format=%x1e%H%x1f%cI%x1f%s%x1f%b%x1f`: one record per commit, files after the last \x1f
export function parseGitLog(text) {
  return text.split('\x1e').filter((r) => r.trim()).map((rec) => {
    const [sha, date, subject, body, files = ''] = rec.split('\x1f')
    return {
      sha: sha.trim(), date: new Date(date), subject, body,
      files: files.split('\n').map((f) => f.trim()).filter(Boolean),
    }
  })
}

export function isCodeFile(f) {
  return !(/^docs\//.test(f) || /\.md$/i.test(f) || /(^|\/)(test|tests|__tests__|__mocks__)\//.test(f)
    || /\.(test|spec)\.[cm]?[jt]sx?$/.test(f) || /(Test|Tests|IT)\.java$/.test(f) || f === 'session.txt')
}

export const isFix = (c) => /^fix(\([^)]*\))?!?:/i.test(c.subject)
const prOf = (subject) => (/^Autopilot: .*\(#(\d+)\)\s*$/.exec(subject) ? Number(/\(#(\d+)\)\s*$/.exec(subject)[1]) : null)

// Short hashes on the "Result:" line of an old autopilot report
export function reportHashes(markdown) {
  const line = markdown.split('\n').find((l) => /\*\*Result:\*\*/.test(l)) ?? ''
  return [...line.matchAll(/`([0-9a-f]{7,40})`/g)].map((m) => m[1])
}

// runFiles: [{ id, pr, ... }] from docs/metrics/agent-runs; reports: [{ id, hashes }]
export function findRuns(commits, runFiles = [], reports = []) {
  const runs = new Map()
  const byPr = new Map(runFiles.filter((r) => r.pr).map((r) => [Number(r.pr), r.id]))
  const add = (id, c) => {
    if (!runs.has(id)) runs.set(id, { id, commits: [], meta: runFiles.find((r) => r.id === id) ?? null })
    const run = runs.get(id)
    if (!run.commits.includes(c)) run.commits.push(c)
  }
  for (const c of commits) {
    const trailer = /^Autopilot-Run:\s*([\w.-]+)\s*$/m.exec(c.body ?? '')
    if (trailer) { add(trailer[1], c); continue }
    const pr = prOf(c.subject)
    if (pr) add(byPr.get(pr) ?? `pr-${pr}`, c)
  }
  for (const rep of reports) {
    for (const h of rep.hashes) {
      const c = commits.find((x) => x.sha.startsWith(h))
      if (c && ![...runs.values()].some((r) => r.commits.includes(c))) add(rep.id, c)
    }
  }
  for (const r of runs.values()) {
    r.commits.sort((a, b) => a.date - b.date)
    r.end = r.commits.at(-1).date
    r.codeFiles = new Set(r.commits.flatMap((c) => c.files).filter(isCodeFile))
  }
  return [...runs.values()].sort((a, b) => a.end - b.end)
}

export function escapedDefects(runs, commits, windowDays) {
  const runShas = new Map(runs.flatMap((r) => r.commits.map((c) => [c.sha, r.id])))
  const out = []
  for (const run of runs) {
    for (const c of commits) {
      if (!isFix(c) || runShas.get(c.sha) === run.id) continue
      const age = c.date - run.end
      if (age <= 0 || age > windowDays * DAY) continue
      const shared = c.files.filter((f) => run.codeFiles.has(f))
      if (shared.length) out.push({ run: run.id, sha: c.sha, subject: c.subject, files: shared })
    }
  }
  return out
}

export function reverts(runs, commits) {
  const owner = new Map(runs.flatMap((r) => r.commits.map((c) => [c.sha, r.id])))
  return commits.flatMap((c) => {
    const m = /This reverts commit ([0-9a-f]{7,40})/.exec(c.body ?? '')
    if (!m) return []
    const hit = [...owner.keys()].find((s) => s.startsWith(m[1]) || m[1].startsWith(s))
    return hit ? [{ run: owner.get(hit), sha: c.sha, subject: c.subject }] : []
  })
}

export function weekKey(d) {
  const day = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()))
  day.setUTCDate(day.getUTCDate() - ((day.getUTCDay() + 6) % 7))
  return day.toISOString().slice(0, 10)
}

const pct = (a, b) => (b ? `${Math.round((100 * a) / b)}%` : 'n/a')
const median = (xs) => {
  if (!xs.length) return null
  const s = [...xs].sort((a, b) => a - b)
  return s.length % 2 ? s[(s.length - 1) / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2
}

export function summarize({ runs, defects, reverted, prs = null, builds = null, autopilotRuns = null }) {
  const metas = runs.map((r) => r.meta).filter(Boolean)
  const defectRuns = new Set(defects.map((d) => d.run))
  const agentShas = new Set(runs.flatMap((r) => r.commits.map((c) => c.sha)))
  const s = {
    runs: runs.length,
    runsWithMeta: metas.length,
    escapedRate: pct(defectRuns.size, runs.length),
    escapedRuns: defectRuns.size,
    reverts: reverted.length,
    firstRoundApprove: pct(metas.filter((m) => m.rounds?.plan === 1 && m.rounds?.code === 1).length, metas.length),
    blocked: metas.filter((m) => m.result && m.result !== 'landed').length,
    reviewSkipped: metas.filter((m) => m.codeReviewSkipped).length,
    flaky: metas.reduce((t, m) => t + (m.tests?.flaky ?? 0), 0),
    costUsd: metas.reduce((t, m) => t + (m.costUsd ?? 0), 0),
    costRuns: metas.filter((m) => m.costUsd != null).length,
  }
  if (prs) {
    const merged = prs.filter((p) => p.mergedAt)
    s.prs = prs.length
    s.prsClosedUnmerged = prs.filter((p) => p.state === 'CLOSED').length
    s.prsRevised = prs.filter((p) => (p.comments ?? []).some((c) => /^\/revise/.test(c.body ?? ''))).length
    const lead = median(merged.map((p) => (new Date(p.mergedAt) - new Date(p.createdAt)) / 60_000))
    s.leadMin = lead == null ? null : Math.round(lead)
  }
  if (builds) {
    const done = builds.filter((b) => ['success', 'failure'].includes(b.conclusion))
    const agent = done.filter((b) => agentShas.has(b.headSha))
    s.ciFailRate = pct(done.filter((b) => b.conclusion === 'failure').length, done.length)
    s.ciAgentFailRate = pct(agent.filter((b) => b.conclusion === 'failure').length, agent.length)
    s.ciAgentRuns = agent.length
  }
  if (autopilotRuns) {
    s.autopilotJobs = Object.entries(autopilotRuns.reduce((t, r) => ({ ...t, [r.conclusion || 'running']: (t[r.conclusion || 'running'] ?? 0) + 1 }), {}))
      .map(([k, v]) => `${k} ${v}`).join(', ')
  }
  return s
}

// Bad signals with stable ids, for /evolve (ADR-0027 D1). base/head = the run's diff (first commit's parent .. last commit)
export function signals({ runs, defects, reverted, prs = null }) {
  const span = (id) => {
    const r = runs.find((x) => x.id === id)
    return r ? { run: id, base: `${r.commits[0].sha}^`, head: r.commits.at(-1).sha } : { run: id }
  }
  const out = []
  for (const d of defects) {
    out.push({ id: `defect:${d.sha.slice(0, 8)}`, kind: 'defect', ...span(d.run), fix: d.sha, subject: d.subject, files: d.files })
  }
  for (const r of reverted) out.push({ id: `revert:${r.sha.slice(0, 8)}`, kind: 'revert', ...span(r.run), revert: r.sha, subject: r.subject })
  for (const r of runs) {
    const m = r.meta
    if (!m) continue
    if (m.result && m.result !== 'landed') out.push({ id: `blocked:${r.id}`, kind: 'blocked', ...span(r.id), result: m.result })
    if ((m.tests?.flaky ?? 0) > 0) out.push({ id: `flaky:${r.id}`, kind: 'flaky', ...span(r.id), flaky: m.tests.flaky })
    if (m.codeReviewSkipped) out.push({ id: `review-skipped:${r.id}`, kind: 'review-skipped', ...span(r.id) })
  }
  for (const p of prs ?? []) {
    if ((p.comments ?? []).some((c) => /^\/revise/.test(c.body ?? ''))) out.push({ id: `revise:pr-${p.number}`, kind: 'revise', pr: p.number })
  }
  return out
}

export function weekly(runs, defects, builds) {
  const rows = new Map()
  const row = (k) => rows.get(k) ?? rows.set(k, { week: k, runs: 0, escaped: 0, ciRuns: 0, ciFail: 0 }).get(k)
  for (const r of runs) row(weekKey(r.end)).runs++
  const runWeek = new Map(runs.map((r) => [r.id, weekKey(r.end)]))
  for (const id of new Set(defects.map((d) => d.run))) row(runWeek.get(id)).escaped++
  for (const b of builds ?? []) {
    if (!['success', 'failure'].includes(b.conclusion)) continue
    const w = row(weekKey(new Date(b.createdAt)))
    w.ciRuns++
    if (b.conclusion === 'failure') w.ciFail++
  }
  return [...rows.values()].sort((a, b) => a.week.localeCompare(b.week))
}

export function render({ args, s, weeks, runs, defects, reverted }) {
  const L = []
  const period = `${args.since} → ${args.until ?? new Date().toISOString().slice(0, 10)}`
  L.push(`# Agent Quality Metrics (${period})`, '')
  L.push(`Generated by \`node scripts/agent-metrics.mjs\` on ${new Date().toISOString().slice(0, 16).replace('T', ' ')} UTC.`)
  L.push(`Escaped defect = a later \`fix:\` commit within ${args.window} days touching a code file the run changed.`, '')
  L.push('## Summary', '', '| Metric | Value |', '|---|---|')
  const rows = [
    ['Agent runs found', `${s.runs} (${s.runsWithMeta} with a run-metrics file)`],
    ['Escaped defect rate', `${s.escapedRate} (${s.escapedRuns} of ${s.runs} runs needed a follow-up fix)`],
    ['Reverts of agent commits', s.reverts],
    ['Plan + code review approved in round 1', `${s.firstRoundApprove} of runs with metrics`],
    ['Runs blocked or stopped', s.blocked],
    ['Code review skipped', s.reviewSkipped],
    ['Flaky tests seen in QA ×2', s.flaky],
    ['Cost (local runs with a figure)', s.costRuns ? `$${s.costUsd.toFixed(2)} over ${s.costRuns} runs, $${(s.costUsd / s.costRuns).toFixed(2)} per run` : 'n/a'],
  ]
  if (s.prs != null) {
    rows.push(['Headless PRs', `${s.prs} (closed unmerged ${s.prsClosedUnmerged}, with /revise ${s.prsRevised})`])
    rows.push(['Median PR open → merge', s.leadMin == null ? 'n/a' : `${s.leadMin} min`])
  }
  if (s.ciFailRate != null) {
    rows.push(['CI build failure rate (all pushes)', s.ciFailRate])
    rows.push(['CI build failure rate (agent head commits)', `${s.ciAgentFailRate} of ${s.ciAgentRuns}`])
  }
  if (s.autopilotJobs) rows.push(['Headless autopilot jobs', s.autopilotJobs])
  for (const [k, v] of rows) L.push(`| ${k} | ${v} |`)
  L.push('', '## By week', '', '| Week (Mon) | Agent runs | Runs with escaped defect | CI builds | CI failures |', '|---|---|---|---|---|')
  for (const w of weeks) L.push(`| ${w.week} | ${w.runs} | ${w.escaped} | ${w.ciRuns} | ${w.ciFail} |`)
  L.push('', '## Escaped defects', '')
  if (!defects.length) L.push('- none')
  for (const d of defects) L.push(`- \`${d.run}\` → \`${d.sha.slice(0, 8)}\` ${d.subject.replace(/\|/g, '\\|')} (${d.files.join(', ')})`)
  L.push('', '## Reverts', '')
  if (!reverted.length) L.push('- none')
  for (const r of reverted) L.push(`- \`${r.run}\` → \`${r.sha.slice(0, 8)}\` ${r.subject}`)
  L.push('', '## Runs', '', '| Run | Last commit | Commits | Code files | Rounds (plan/code/qa) | Result |', '|---|---|---|---|---|---|')
  for (const r of runs) {
    const m = r.meta
    const rounds = m?.rounds ? `${m.rounds.plan ?? '-'}/${m.rounds.code ?? '-'}/${m.rounds.qa ?? '-'}` : 'n/a'
    L.push(`| \`${r.id}\` | ${r.end.toISOString().slice(0, 10)} | ${r.commits.length} | ${r.codeFiles.size} | ${rounds} | ${m?.result ?? 'n/a'} |`)
  }
  L.push('', '## Data gaps', '')
  L.push('- Runs before the `Autopilot-Run` trailer are found only through their report hashes or the headless PR title; local lite runs from that time are missed.')
  L.push('- A follow-up fix done by a later autopilot run whose subject is not `fix:` is not counted.')
  L.push(`- Runs that ended less than ${args.window} days before the report date have not had their full fix window yet.`)
  L.push('- Headless runs have no cost in their run-metrics file; the workflow posts it on the PR.')
  return L.join('\n') + '\n'
}

function sh(cmd, argv) {
  return execFileSync(cmd, argv, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'] })
}

function ghJson(argv) {
  try {
    const rows = JSON.parse(sh('gh', argv))
    if (rows.length >= GH_LIMIT) console.error(`gh ${argv.slice(0, 2).join(' ')} hit the ${GH_LIMIT} row limit, older rows are missing`)
    return rows
  } catch (e) {
    console.error(`gh ${argv.slice(0, 2).join(' ')} failed, section skipped: ${e.message.split('\n')[0]}`)
    return null
  }
}

function loadRunFiles(root) {
  const dir = join(root, RUNS_DIR)
  if (!existsSync(dir)) return []
  return readdirSync(dir).filter((f) => f.endsWith('.json')).flatMap((f) => {
    try {
      const m = JSON.parse(readFileSync(join(dir, f), 'utf8'))
      if (!m || typeof m !== 'object' || Array.isArray(m)) throw new Error('not an object')
      const num = (x) => (typeof x === 'number' && Number.isFinite(x) ? x : null)
      return [{
        ...m, id: f.replace(/\.json$/, ''), pr: num(m.pr), costUsd: num(m.costUsd),
        tests: { ...m.tests, flaky: num(m.tests?.flaky) ?? 0 },
      }]
    } catch (e) {
      console.error(`skipped unreadable ${RUNS_DIR}/${f}: ${e.message}`)
      return []
    }
  })
}

function loadReports(root) {
  const dir = join(root, REPORTS_DIR)
  if (!existsSync(dir)) return []
  return readdirSync(dir).filter((f) => /^autopilot-.*\.md$/.test(f)).map((f) => ({
    id: f.replace(/^autopilot-/, '').replace(/\.md$/, ''),
    hashes: reportHashes(readFileSync(join(dir, f), 'utf8')),
  }))
}

function main() {
  const args = parseArgs(process.argv.slice(2))
  const root = sh('git', ['rev-parse', '--show-toplevel']).trim()
  // Commits after --until are read too, so runs near the end still get their fix window
  const log = sh('git', ['-C', root, 'log', `--since=${args.since}`,
    '--name-only', '--no-merges', '--format=%x1e%H%x1f%cI%x1f%s%x1f%b%x1f'])
  const commits = parseGitLog(log)
  const untilEnd = args.until ? new Date(`${args.until}T23:59:59Z`) : null
  const runs = findRuns(commits, loadRunFiles(root), loadReports(root)).filter((r) => !untilEnd || r.end <= untilEnd)
  const defects = escapedDefects(runs, commits, args.window)
  const reverted = reverts(runs, commits)
  let prs = null; let builds = null; let autopilotRuns = null
  if (args.gh) {
    const inPeriod = (d) => d.slice(0, 10) >= args.since && (!args.until || d.slice(0, 10) <= args.until)
    prs = ghJson(['pr', 'list', '--state', 'all', '--search', 'Autopilot in:title', '--limit', String(GH_LIMIT),
      '--json', 'number,title,state,createdAt,mergedAt,comments'])
      ?.filter((p) => /^Autopilot:/.test(p.title) && inPeriod(p.createdAt)) ?? null
    builds = ghJson(['run', 'list', '--workflow', BUILD_WORKFLOW, '--limit', String(GH_LIMIT), '--json', 'conclusion,headSha,createdAt'])
      ?.filter((b) => inPeriod(b.createdAt)) ?? null
    autopilotRuns = ghJson(['run', 'list', '--workflow', AUTOPILOT_WORKFLOW, '--limit', String(GH_LIMIT), '--json', 'conclusion,createdAt'])
      ?.filter((b) => inPeriod(b.createdAt)) ?? null
  }
  const s = summarize({ runs, defects, reverted, prs, builds, autopilotRuns })
  if (args.json) {
    process.stdout.write(`${JSON.stringify({ since: args.since, until: args.until ?? null, summary: s, signals: signals({ runs, defects, reverted, prs }) }, null, 2)}
`)
    return
  }
  const md = render({ args, s, weeks: weekly(runs, defects, builds), runs, defects, reverted })
  if (args.out) {
    const out = resolve(root, args.out)
    if (relative(root, out).startsWith('..') || isAbsolute(relative(root, out))) throw new Error('--out must stay inside the repo')
    writeFileSync(out, md)
    console.log(`wrote ${args.out}`)
  } else process.stdout.write(md)
}

if (process.argv[1] && resolve(fileURLToPath(import.meta.url)).toLowerCase() === resolve(process.argv[1]).toLowerCase()) {
  try { main() } catch (e) { console.error(e.message); process.exit(2) }
}
