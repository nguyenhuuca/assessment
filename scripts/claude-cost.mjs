// Exact token usage and API-list-price cost of a Claude Code session, from its local transcripts.
// Reads ~/.claude/projects/<project>/<session>.jsonl (the main agent) and <session>/subagents/*.jsonl
// (one per worker), dedupes assistant messages by message.id and prices each request with
// scripts/claude-pricing.json. Used by /autopilot for the "Cost" section of its run report.
//
// Usage: node scripts/claude-cost.mjs [--session <id|path.jsonl>] [--project <dir>]
//                                     [--from <ISO time>] [--to <ISO time>] [--md]
//   default project = ~/.claude/projects/<cwd with : \ / replaced by ->, default session = newest .jsonl there
//   --from/--to keep only requests whose timestamp is in [from, to] (ISO, e.g. 2026-10-19T09:26+07:00)
//   --md prints a markdown table (for docs/reports), otherwise a plain table
// The figures are API list prices. On a Claude subscription this is the equivalent cost, not a bill.
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { homedir } from 'node:os'
import { basename, dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const PRICING = JSON.parse(readFileSync(join(dirname(fileURLToPath(import.meta.url)), 'claude-pricing.json'), 'utf8'))
const MTOK = 1_000_000

export function parseArgs(argv) {
  const args = { md: false }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--md') args.md = true
    else if (['--session', '--project', '--from', '--to'].includes(a)) args[a.slice(2)] = argv[++i]
    else throw new Error(`unknown argument: ${a}`)
  }
  return args
}

export function projectDirFor(cwd) {
  return join(homedir(), '.claude', 'projects', cwd.replace(/[:\\/]/g, '-'))
}

function newestSession(projectDir) {
  const files = readdirSync(projectDir).filter((f) => f.endsWith('.jsonl'))
  if (files.length === 0) throw new Error(`no session transcripts in ${projectDir}`)
  return files.map((f) => join(projectDir, f)).sort((a, b) => statSync(b).mtimeMs - statSync(a).mtimeMs)[0]
}

// One entry per API request: the transcript repeats a message on several lines with the same usage.
export function requestsOf(jsonlText) {
  const byId = new Map()
  for (const line of jsonlText.split('\n')) {
    if (!line.trim()) continue
    let row
    try { row = JSON.parse(line) } catch { continue }
    const msg = row.type === 'assistant' ? row.message : null
    if (!msg?.usage || !msg.id || msg.model === '<synthetic>') continue
    byId.set(msg.id, { model: msg.model, usage: msg.usage, time: Date.parse(row.timestamp) })
  }
  return [...byId.values()]
}

// Cost of one request in USD, or null when the model is not in the pricing table.
export function priceRequest({ model, usage }, pricing = PRICING) {
  const base = pricing.models[model]
  if (!base) return null
  const write5m = usage.cache_creation?.ephemeral_5m_input_tokens ?? usage.cache_creation_input_tokens ?? 0
  const write1h = usage.cache_creation?.ephemeral_1h_input_tokens ?? 0
  const read = usage.cache_read_input_tokens ?? 0
  const input = usage.input_tokens ?? 0
  const prompt = input + write5m + write1h + read
  let p = base.longPromptOver && prompt > base.longPromptOver ? { ...base, ...base.long } : base
  if (usage.speed === 'fast' && base.fast) {
    // the pricing page lists fast-mode input/output only; cache prices are scaled by the same factor
    const k = base.fast.input / base.input
    p = { ...p, ...base.fast, cacheWrite5m: p.cacheWrite5m * k, cacheWrite1h: p.cacheWrite1h * k, cacheRead: p.cacheRead * k }
  }
  const geo = usage.inference_geo === 'us' ? pricing.geoUsMultiplier : 1
  return geo * (input * p.input + write5m * p.cacheWrite5m + write1h * p.cacheWrite1h
    + read * p.cacheRead + (usage.output_tokens ?? 0) * p.output) / MTOK
}

export function summarize(requests) {
  const s = { requests: 0, input: 0, write: 0, read: 0, output: 0, usd: 0, unpriced: 0, models: new Set() }
  for (const r of requests) {
    const u = r.usage
    s.requests++
    s.models.add(r.model)
    s.input += u.input_tokens ?? 0
    s.write += u.cache_creation_input_tokens ?? 0
    s.read += u.cache_read_input_tokens ?? 0
    s.output += u.output_tokens ?? 0
    const usd = priceRequest(r)
    if (usd === null) s.unpriced++
    else s.usd += usd
  }
  return s
}

function agentLabel(jsonlPath) {
  const meta = jsonlPath.replace(/\.jsonl$/, '.meta.json')
  if (!existsSync(meta)) return basename(jsonlPath, '.jsonl')
  const m = JSON.parse(readFileSync(meta, 'utf8'))
  return `${m.agentType ?? 'agent'}: ${m.description ?? basename(jsonlPath, '.jsonl')}`
}

function main() {
  const args = parseArgs(process.argv.slice(2))
  const projectDir = args.project ?? projectDirFor(process.cwd())
  const sessionFile = !args.session ? newestSession(projectDir)
    : args.session.endsWith('.jsonl') ? args.session : join(projectDir, `${args.session}.jsonl`)
  const from = args.from ? Date.parse(args.from) : -Infinity
  const to = args.to ? Date.parse(args.to) : Infinity
  if (Number.isNaN(from) || Number.isNaN(to)) throw new Error('--from/--to must be ISO date-times')
  const inWindow = (r) => r.time >= from && r.time <= to

  const subDir = join(sessionFile.replace(/\.jsonl$/, ''), 'subagents')
  const sources = [{ label: 'main session (orchestrator)', file: sessionFile }]
  if (existsSync(subDir)) {
    for (const f of readdirSync(subDir).filter((f) => f.endsWith('.jsonl')).sort()) {
      sources.push({ label: agentLabel(join(subDir, f)), file: join(subDir, f) })
    }
  }
  const rows = sources
    .map(({ label, file }) => ({ label, ...summarize(requestsOf(readFileSync(file, 'utf8')).filter(inWindow)) }))
    .filter((r) => r.requests > 0)
  const total = rows.reduce((t, r) => ({
    requests: t.requests + r.requests, input: t.input + r.input, write: t.write + r.write, read: t.read + r.read,
    output: t.output + r.output, usd: t.usd + r.usd, unpriced: t.unpriced + r.unpriced,
  }), { requests: 0, input: 0, write: 0, read: 0, output: 0, usd: 0, unpriced: 0 })

  const n = (x) => x.toLocaleString('en-US')
  const usd = (x) => `$${x.toFixed(2)}`
  const line = (r, label, models) => [label, models, n(r.requests), n(r.input), n(r.write), n(r.read), n(r.output), usd(r.usd)]
  const header = ['who', 'model', 'requests', 'input', 'cache write', 'cache read', 'output', 'cost']
  const body = [...rows.map((r) => line(r, r.label, [...r.models].join(', '))), line(total, 'TOTAL', '')]
  if (args.md) {
    console.log(`| ${header.join(' | ')} |\n|${header.map(() => '---').join('|')}|`)
    for (const b of body) console.log(`| ${b[0] === 'TOTAL' ? b.map((c) => (c ? `**${c}**` : c)).join(' | ') : b.join(' | ')} |`)
  } else {
    for (const b of [header, ...body]) console.log(b.map((c, i) => (i < 2 ? c.slice(0, 48).padEnd(i ? 18 : 48) : c.padStart(12))).join(' '))
  }
  console.log(`\nsession ${basename(sessionFile, '.jsonl')} · ${args.from ?? 'start'} → ${args.to ?? 'end'} · `
    + `prices ${PRICING.source} (read ${PRICING.read}) · API list-price equivalent`)
  if (total.unpriced > 0) console.log(`WARNING: ${total.unpriced} requests used a model missing from claude-pricing.json (not counted)`)
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  try { main() } catch (e) { console.error(e.message); process.exit(2) }
}
