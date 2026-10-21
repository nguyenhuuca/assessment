// Run: node --test scripts/__tests__/agent-metrics.test.mjs
import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  escapedDefects, findRuns, isCodeFile, isFix, parseArgs, parseGitLog, reportHashes, reverts, summarize, weekKey,
} from '../agent-metrics.mjs'

const rec = (sha, date, subject, body, files) => `\x1e${sha}\x1f${date}\x1f${subject}\x1f${body}\x1f\n${files.join('\n')}\n`
const commit = (sha, date, subject, files, body = '') => ({ sha, date: new Date(date), subject, body, files })

test('parseGitLog splits records, body and files', () => {
  const text = rec('a1', '2026-10-01T10:00:00+07:00', 'feat: x', 'line\n\nAutopilot-Run: r1', ['api/A.java', 'docs/x.md'])
    + rec('b2', '2026-10-02T10:00:00+07:00', 'fix: y', '', [])
  const cs = parseGitLog(text)
  assert.equal(cs.length, 2)
  assert.equal(cs[0].sha, 'a1')
  assert.match(cs[0].body, /Autopilot-Run: r1/)
  assert.deepEqual(cs[0].files, ['api/A.java', 'docs/x.md'])
  assert.deepEqual(cs[1].files, [])
})

test('isCodeFile ignores docs, markdown, tests and session.txt', () => {
  for (const f of ['docs/a.md', 'TASKS.md', 'session.txt', 'api/src/test/java/X.java', 'api/src/main/java/FooTest.java',
    'webapp/src/a/__tests__/b.jsx', 'webapp/src/x.test.jsx', 'scripts/__tests__/t.mjs',
    'api/src/main/java/FooTests.java', 'api/src/main/java/FooIT.java', 'webapp/src/__mocks__/m.js']) assert.equal(isCodeFile(f), false, f)
  for (const f of ['api/src/main/java/Foo.java', 'webapp/src/styles/index.css', 'scripts/agent-metrics.mjs']) assert.equal(isCodeFile(f), true, f)
})

test('isFix matches conventional fix subjects only', () => {
  assert.ok(isFix({ subject: 'fix: a' }))
  assert.ok(isFix({ subject: 'fix(report): a' }))
  assert.ok(isFix({ subject: 'Fix!: a' }))
  assert.ok(!isFix({ subject: 'feat: fix later' }))
  assert.ok(!isFix({ subject: 'fixture: a' }))
})

test('reportHashes reads only the Result line', () => {
  const md = '# R\n- **Result:** ✅ pushed `6d5fe722` (BE `801dcb9f`, FE `b3030e52`)\n- see `deadbeef0`\n'
  assert.deepEqual(reportHashes(md), ['6d5fe722', '801dcb9f', 'b3030e52'])
  assert.deepEqual(reportHashes('no result here'), [])
})

test('findRuns groups by trailer, headless PR title and report hashes', () => {
  const cs = [
    commit('aaa111', '2026-10-01T00:00:00Z', 'feat: one', ['api/A.java'], 'x\n\nAutopilot-Run: 2026-10-01-one\nCo-Authored-By: C'),
    commit('aaa222', '2026-10-01T01:00:00Z', 'docs: one', ['TASKS.md'], 'Autopilot-Run: 2026-10-01-one'),
    commit('bbb111', '2026-10-02T00:00:00Z', 'Autopilot: Fix button (#48) (#49)', ['webapp/src/a.css']),
    commit('ccc111', '2026-10-03T00:00:00Z', 'feat: old run', ['api/B.java']),
    commit('ddd111', '2026-10-04T00:00:00Z', 'feat: by hand', ['api/C.java']),
  ]
  const runs = findRuns(cs, [{ id: '2026-10-02-button', pr: 49, rounds: { plan: 1, code: 1 } }],
    [{ id: '2026-10-03-old', hashes: ['ccc111', 'zzz999'] }])
  assert.deepEqual(runs.map((r) => r.id), ['2026-10-01-one', '2026-10-02-button', '2026-10-03-old'])
  assert.equal(runs[0].commits.length, 2)
  assert.deepEqual([...runs[0].codeFiles], ['api/A.java'])
  assert.equal(runs[1].meta.pr, 49)
  assert.equal(runs[2].meta, null)
})

test('findRuns falls back to pr-N when no run file names the PR', () => {
  const runs = findRuns([commit('e1', '2026-10-05T00:00:00Z', 'Autopilot: X (#7)', ['a.js'])])
  assert.equal(runs[0].id, 'pr-7')
})

test('escapedDefects counts later fixes on shared code files inside the window', () => {
  const run = commit('r1', '2026-10-01T00:00:00Z', 'feat: a', ['api/A.java', 'docs/a.md'], 'Autopilot-Run: run-a')
  const cs = [
    run,
    commit('f1', '2026-10-03T00:00:00Z', 'fix: a broke', ['api/A.java']), // counted
    commit('f2', '2026-10-03T00:00:00Z', 'fix: docs typo', ['docs/a.md']), // docs only, ignored
    commit('f3', '2026-10-20T00:00:00Z', 'fix: late', ['api/A.java']), // outside 7 days
    commit('f4', '2026-09-30T00:00:00Z', 'fix: before', ['api/A.java']), // before the run
    commit('f5', '2026-10-02T00:00:00Z', 'feat: more', ['api/A.java']), // not a fix
    commit('f6', '2026-10-02T00:00:00Z', 'fix: same run', ['api/A.java'], 'Autopilot-Run: run-a'), // part of the run
  ]
  const runs = findRuns(cs)
  const d = escapedDefects(runs, cs, 7)
  assert.deepEqual(d.map((x) => x.sha), ['f1'])
  assert.deepEqual(d[0].files, ['api/A.java'])
})

test('reverts links "This reverts commit" to the run', () => {
  const cs = [
    commit('abcdef1234', '2026-10-01T00:00:00Z', 'feat: a', ['a.js'], 'Autopilot-Run: run-a'),
    commit('r9', '2026-10-02T00:00:00Z', 'Revert "feat: a"', ['a.js'], 'This reverts commit abcdef1234.'),
    commit('r8', '2026-10-02T00:00:00Z', 'Revert "other"', ['b.js'], 'This reverts commit 0000000.'),
  ]
  const r = reverts(findRuns(cs), cs)
  assert.deepEqual(r.map((x) => [x.run, x.sha]), [['run-a', 'r9']])
})

test('summarize computes rates from runs, PRs and CI', () => {
  const cs = [
    commit('a1', '2026-10-01T00:00:00Z', 'feat: a', ['a.js'], 'Autopilot-Run: a'),
    commit('b1', '2026-10-02T00:00:00Z', 'feat: b', ['b.js'], 'Autopilot-Run: b'),
  ]
  const runs = findRuns(cs, [
    { id: 'a', rounds: { plan: 1, code: 1 }, result: 'landed', costUsd: 2, tests: { flaky: 1 } },
    { id: 'b', rounds: { plan: 2, code: 1 }, result: 'blocked', codeReviewSkipped: true, costUsd: null },
  ])
  const s = summarize({
    runs,
    defects: [{ run: 'a' }, { run: 'a' }],
    reverted: [],
    prs: [
      { state: 'MERGED', createdAt: '2026-10-01T00:00:00Z', mergedAt: '2026-10-01T00:10:00Z', comments: [{ body: '/revise more' }] },
      { state: 'CLOSED', createdAt: '2026-10-01T00:00:00Z', mergedAt: null, comments: [] },
    ],
    builds: [
      { conclusion: 'failure', headSha: 'a1' }, { conclusion: 'success', headSha: 'zz' }, { conclusion: 'cancelled', headSha: 'b1' },
    ],
  })
  assert.equal(s.escapedRate, '50%')
  assert.equal(s.firstRoundApprove, '50%')
  assert.equal(s.blocked, 1)
  assert.equal(s.reviewSkipped, 1)
  assert.equal(s.flaky, 1)
  assert.equal(s.costUsd, 2)
  assert.equal(s.costRuns, 1)
  assert.equal(s.prsClosedUnmerged, 1)
  assert.equal(s.prsRevised, 1)
  assert.equal(s.leadMin, 10)
  assert.equal(s.ciFailRate, '50%')
  assert.equal(s.ciAgentFailRate, '100%')
})

test('weekKey returns the Monday of the week', () => {
  assert.equal(weekKey(new Date('2026-10-21T12:00:00Z')), '2026-10-19') // Wednesday
  assert.equal(weekKey(new Date('2026-10-25T12:00:00Z')), '2026-10-19') // Sunday
  assert.equal(weekKey(new Date('2026-10-19T00:00:00Z')), '2026-10-19')
})

test('parseArgs validates input', () => {
  assert.equal(parseArgs(['--since', '2026-10-01', '--no-gh']).gh, false)
  assert.throws(() => parseArgs(['--since', '1 week']), /YYYY-MM-DD/)
  assert.throws(() => parseArgs(['--window', '0']), /positive/)
  assert.throws(() => parseArgs(['--bogus']), /unknown/)
  assert.throws(() => parseArgs(['--since']), /needs a value/)
  assert.throws(() => parseArgs(['--out']), /needs a value/)
})

test('findRuns ignores a trailer id with unsafe characters', () => {
  const runs = findRuns([commit('u1', '2026-10-05T00:00:00Z', 'feat: x', ['a.js'], 'Autopilot-Run: bad|id`x')])
  assert.equal(runs.length, 0)
})
