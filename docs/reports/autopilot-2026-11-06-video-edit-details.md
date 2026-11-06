# Autopilot run: Edit video title and description

- **Request:** issue #86: allow editing the video title and description in the admin Vault tab
- **Mode:** headless (issue #86, PR #87)
- **Plan:** [plan-video-edit-details](../plans/plan-video-edit-details.md)
- **Started:** 2026-11-06 05:20 · **Landed:** pending
- **Result:** ✅ PR ready for merge

## Pipeline

| Stage | Result | Rounds |
|-------|--------|--------|
| Start | issue #86, author is repo owner | — |
| Plan | plan in the PR body (plan job); plan file added in the build job after the diff passed ~100 LOC | 1 of 3 |
| Plan review | approve, 0 blocking, 4 advisory | 1 of 3 |
| Gate 1 (owner) | `/approve` | — |
| Implement | orchestrator, 17 files | 1 of 3 |
| Code review | round 1 revise (1 blocking, 5 advisory); round 2 approve (0 blocking, 2 advisory) | 2 of 3 |
| Local QA ×2 | `qa-twice.sh`: 117 tests, pass 117 · flaky 0 · fail 0 | 1 of 3 |
| Fix | blocking finding accepted and documented; focus effect split; `Objects` import; permission tests; JSON contract fixture (found by `mvn verify`) | — |
| Land: full gates | `mvn verify` green (coverage gate included); `npm run test` 771 passed; `npm run lint` clean | — |
| Gate 2 (owner) | pending: owner merges | — |
| CI + smoke | n/a in CI: runs after merge | — |

## What changed

- `PATCH /admin/videos/{id}/details`, audited, in `AdminController.java` (null body and non-string values rejected with a Contract error).
- `AdminVideoServiceImpl.updateDetails`: title trimmed, 1..200; description max 2000, blank stored as null; no save when unchanged; feed cache invalidated on change. `TitleSanitizer` is not used: it strips `: ? "` and cuts at 120 because it builds Drive file names.
- `AdminVideoDto.description`, and the recorded JSON contract `dto-admin-video.json`.
- `VideoEditModal.jsx`, an "Edit" row action in `AdminVideoTable.jsx`, `useUpdateVideoDetails`, en and vi strings.

## Runs

| # | cycle | stage | round | path | result | blocking | advisory | turns | min | tokens | model |
|---|-------|-------|-------|------|--------|----------|----------|-------|-----|--------|-------|
| 1 | 1 | plan review | 1 | plan job | approve | 0 | 4 | n/a | n/a | n/a | n/a |
| 2 | 1 | code review | 1 | fallback worker-reviewer | revise | 1 | 5 | 10 | 1 | 38765 | sonnet |
| 3 | 1 | code review | 2 | fallback worker-reviewer | approve | 0 | 2 | 11 | 1 | 46216 | sonnet |
| 4 | 1 | QA ×2 | 1 | qa-twice.sh | exit 0 | 0 | 0 | n/a | n/a | n/a | n/a |

## Open advisories

- [A] The 2000-char description cap also applies to existing descriptions (unbounded TEXT, possibly imported from YouTube), so a title-only edit of such a video returns 400 until the description is shortened. Skipping the check for an unchanged description would break the "oversize rejected without lookup" criterion, so it is left for a follow-up.
- [A] The modal's `maxLength` does not truncate a longer prefilled description. Same follow-up.

## Manual checks for the owner

- After the deploy: open Admin → Vault, edit a video, confirm the new title shows in the list and in the feed.
- Run `./api/mock-test.sh` after the deploy.
- Clearing a description is refilled by the scheduled description job (accepted; the modal says so).

## Ignored instructions

- none

## Cost

API list-price equivalent, not a bill.

| job | turns | cost | models | run |
|-----|-------|------|--------|-----|
| build | 103 | $1.49 | claude-sonnet-5-5 $1.49 | [run](https://github.com/nguyenhuuca/assessment/actions/runs/42168079428) |
