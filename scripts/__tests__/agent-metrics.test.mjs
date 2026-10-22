// Run: node --test scripts/__tests__/agent-metrics.test.mjs
import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  changedOldRanges, escapedDefects, findRuns, fixCommitsFor, isClaudeCoAuthored, isCodeFile, isFix, linkBugs, parseArgs, parseGitLog, reportHashes, reverts,
  signals, summarize, weekKey,
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

test('signals give stable ids and the run diff span', () => {
  const cs = [
    commit('a1aaaaaaaa', '2026-10-01T00:00:00Z', 'feat: a', ['api/A.java'], 'Autopilot-Run: run-a'),
    commit('a2aaaaaaaa', '2026-10-01T01:00:00Z', 'docs: a', ['TASKS.md'], 'Autopilot-Run: run-a'),
    commit('f1ffffffff', '2026-10-03T00:00:00Z', 'fix: a broke', ['api/A.java']),
    commit('b1bbbbbbbb', '2026-10-04T00:00:00Z', 'feat: b', ['b.js'], 'Autopilot-Run: run-b'),
  ]
  const runs = findRuns(cs, [{ id: 'run-b', result: 'blocked', tests: { flaky: 2 }, codeReviewSkipped: true }])
  const sig = signals({
    runs, defects: escapedDefects(runs, cs, 7), reverted: [],
    prs: [{ number: 9, comments: [{ body: '/revise x' }] }, { number: 10, comments: [] }],
  })
  assert.deepEqual(sig.map((x) => x.id), ['defect:f1ffffff', 'blocked:run-b', 'flaky:run-b', 'review-skipped:run-b', 'revise:pr-9'])
  assert.equal(sig[0].base, 'a1aaaaaaaa^')
  assert.equal(sig[0].head, 'a2aaaaaaaa')
  assert.equal(sig[0].run, 'run-a')
})

test('changedOldRanges keeps changed or removed old lines, skips pure additions', () => {
  const patch = [
    'diff --git a/a.jsx b/a.jsx', '--- a/a.jsx', '+++ b/a.jsx',
    '@@ -10,2 +10,3 @@ x', '-old', '-old', '+new', '+new', '+new',
    '@@ -20,0 +22,4 @@', '+added',
    '@@ -30 +34 @@', '-one', '+one',
    'diff --git a/n.css b/n.css', '--- /dev/null', '+++ b/n.css', '@@ -0,0 +1,2 @@', '+a',
  ].join('\n')
  assert.deepEqual(changedOldRanges(patch), [{ file: 'a.jsx', start: 10, count: 2 }, { file: 'a.jsx', start: 30, count: 1 }])
})

test('fixCommitsFor finds the squash commit of the closing PR or a Fixes line', () => {
  const cs = [
    commit('s1', '2026-10-21T10:00:00Z', 'Autopilot: Issue x (#53) (#54)', ['a.jsx']),
    commit('s2', '2026-10-21T11:00:00Z', 'fix: y', ['b.jsx'], 'Fixes #53'),
    commit('s3', '2026-10-21T12:00:00Z', 'feat: z (#540)', ['c.jsx'], 'fixes #530'),
  ]
  assert.deepEqual(fixCommitsFor({ number: 53, closedByPullRequestsReferences: [{ number: 54 }] }, cs).map((c) => c.sha), ['s1', 's2'])
  assert.deepEqual(fixCommitsFor({ number: 7 }, cs), [])
})

test('linkBugs blames the latest run whose lines the fix changed; signals skip unlinked and fix: duplicates', () => {
  const cs = [
    commit('r1a', '2026-10-01T10:00:00Z', 'feat: a', ['webapp/src/A.jsx'], 'Autopilot-Run: run-1'),
    commit('r2a', '2026-10-05T10:00:00Z', 'feat: b', ['webapp/src/A.jsx'], 'Autopilot-Run: run-2'),
    commit('h1', '2026-10-06T10:00:00Z', 'feat: human', ['webapp/src/A.jsx']),
    commit('fx', '2026-10-10T10:00:00Z', 'Autopilot: Issue bug (#9) (#10)', ['webapp/src/A.jsx'], 'Autopilot-Run: run-3'),
    commit('fx2', '2026-10-11T10:00:00Z', 'fix: other', ['webapp/src/A.jsx']),
  ]
  const runs = findRuns(cs)
  const bugs = linkBugs([
    { number: 9, title: 'Nút khó nhìn', fix: cs[3], blamed: ['r1a', 'r2a', 'h1'], files: ['webapp/src/A.jsx'] },
    { number: 11, title: 'human bug', fix: cs[3], blamed: ['h1'] },
    { number: 12, title: 'already a defect', fix: cs[4], blamed: ['r2a'] },
    { number: 13, title: 'adds only', fix: cs[3], blamed: [] },
  ], runs, [{ sha: 'fx2', run: 'run-2' }])
  assert.deepEqual(bugs.map((b) => [b.number, b.run, b.dup]), [[9, 'run-2', false], [11, null, false], [12, 'run-2', true], [13, null, false]])
  assert.equal(bugs[0].commit, null, 'an autopilot run wins over a session commit')
  const sig = signals({ runs, defects: [], reverted: [], bugs })
  assert.deepEqual(sig.map((x) => x.id), ['bug:#9'])
  assert.equal(sig[0].base, 'r2a^')
  assert.equal(sig[0].head, 'r2a')
  assert.equal(sig[0].issue, 9)
  const s = summarize({ runs, defects: [], reverted: [], bugs })
  assert.equal(s.bugReports, 4)
  assert.equal(s.bugReportsLinked, 2)
})

test('linkBugs falls back to the latest Claude-co-authored session commit when no run wrote the lines', () => {
  assert.ok(isClaudeCoAuthored('feat: x\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>'))
  assert.ok(!isClaudeCoAuthored('feat: x\n\nCo-Authored-By: Alice <a@x>'))
  const cs = [
    commit('r1', '2026-10-01T10:00:00Z', 'feat: a', ['api/A.java'], 'Autopilot-Run: run-1'),
    commit('fx', '2026-10-20T10:00:00Z', 'Autopilot: Issue (#56) (#57)', ['api/A.java'], 'Autopilot-Run: run-9'),
  ]
  const runs = findRuns(cs)
  const s1 = { sha: '50ade4e1aaaa', date: new Date('2026-10-13T10:00:00Z') }
  const s0 = { sha: '11112222bbbb', date: new Date('2026-10-05T10:00:00Z') }
  const later = { sha: '99990000cccc', date: new Date('2026-10-25T10:00:00Z') }
  const bugs = linkBugs([
    { number: 56, title: '@unknown', fix: cs[1], blamed: [s0.sha, s1.sha, 'human'], agentCommits: [s0, s1, later], files: ['api/A.java'] },
    { number: 60, title: 'run and session', fix: cs[1], blamed: ['r1', s1.sha], agentCommits: [s1] },
  ], runs)
  assert.deepEqual(bugs.map((b) => [b.run, b.commit]), [['session:50ade4e1', s1.sha], ['run-1', null]])
  const sig = signals({ runs, defects: [], reverted: [], bugs })
  assert.deepEqual(sig.map((x) => [x.id, x.origin, x.base, x.head]), [
    ['bug:#56', 'session', '50ade4e1aaaa^', '50ade4e1aaaa'],
    ['bug:#60', 'autopilot', 'r1^', 'r1'],
  ])
  const s = summarize({ runs, defects: [], reverted: [], bugs })
  assert.deepEqual([s.bugReportsLinked, s.bugReportsSession], [2, 1])
})
