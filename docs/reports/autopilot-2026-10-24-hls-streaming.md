# Autopilot run: HLS streaming for selected videos

- **Request:** `/autopilot docs/plans/plan-hls-streaming.md` (plan mode, HL-1..HL-7)
- **Mode:** local
- **Plan:** [plan-hls-streaming](../plans/plan-hls-streaming.md)
- **Started:** 2026-10-24 10:28 · **Landed:** 2026-10-24 11:08
- **Result:** ✅ committed `5c485b5f`..`64d66721` (Gate 2 pending at report time)

## Pipeline

| Stage | Result | Rounds |
|-------|--------|--------|
| Start | owner request in chat (`/autopilot <plan>`) | — |
| Plan | reused: plan mode, 7 open tasks (written by `/swarm-plan` from the spec; cap raised to 8 the same day) | — |
| Plan review | approve, 0 blocking, 3 advisory (folded into the plan before Gate 1) | 1 of 3 |
| Gate 1 (owner) | approved | — |
| Implement | 4 worker-builders in worktrees, 2 waves: BE-A HL-1+HL-2 ∥ FE HL-6+HL-7, then BE-B HL-3+HL-4 ∥ BE-C HL-5 (wave 2 cherry-picked BE-A's commits). Cherry-picked onto `main` as 11 commits; 60 files +3,277/−20 (non-test +1,333/−14) | 1 of 3 |
| Code review | FE (quality): approve, 0 blocking, 3 advisory (applied). BE (security + quality): round 1 revise, 1 blocking (publish-guard race), 5 advisory; round 2 approve, 0 blocking, 3 advisory | 2 of 3 |
| Local QA ×2 | `qa-twice.sh`: round 1 fail 2 (test files failed to load: `hls.js` was installed only in the worker's worktree, not in the main checkout; `npm ci` fixed it). Round 2: 347 tests, pass 347 · flaky 0 · fail 0 | 2 of 3 |
| Fix | blocker fixed with conditional `markHlsReady` / `markHlsError` (`e61f747e`); OUTPUT_MISSING made conditional (`64d66721`); FE advisories (`1e436475`) | — |
| Land: full gates | `mvn verify`: 1788 tests, 0 failures, 2 skipped, line coverage 96.37% (threshold 90%). `npm run test`: 610/610. `npm run lint`: 0 errors, 3 old warnings. `npm run build`: hls.js in its own chunk | — |
| Gate 2 (owner) | pending | — |
| CI + smoke | pending (after push) | — |

## What changed

- **Schema:** `video_sources.hls_status` (NONE default, CHECK) + `hls_error`, partial queue index (`202610240001-video-source-hls.sql`).
- **Transcoding:**
  - `HlsStorage` validates names and keeps every path inside `video-cache/hls/{fileId}/`. It also rejects symlinks and publishes by renaming the tmp directory.
  - `FfmpegHlsClient` runs ffprobe (H.264 + AAC/MP3 only) and `ffmpeg -c copy -f hls`, with argument lists and a timeout kill.
  - `HlsTranscodeWorker` runs one job at a time. It peeks the queue, takes the file lock, then claims by id. It runs the disk guard, then publishes under conditional `markHlsReady`, so it never overwrites NONE/PENDING. Startup recovery and an orphan sweep run at boot.
- **Admin:**
  - SUPER_ADMIN-only `PUT`/`DELETE /admin/videos/{id}/hls` with `@AuditLog`.
  - `AdminVideoDto.hlsStatus/hlsError`.
  - Video delete always removes the rendition.
  - A re-download resets READY/ERROR to PENDING.
- **Playback:**
  - Public `GET /video-stream/hls/{fileId}/playlist.m3u8` (READY + PUBLISHED + not hidden) and `/{segment}` (immutable cache, no DB).
  - `VideoDto.hlsUrl` is set only when the Harness flag `hls_streaming_enabled` is on and the video is READY. The flag is read once per request.
- **Frontend:**
  - `hls.js@1.7.3`, loaded lazily.
  - `useHlsSource` uses native HLS or hls.js and falls back to Range at the same position.
  - The swiper skips Range preload for HLS videos.
  - The admin HLS column has a SUPER_ADMIN toggle and polls every 5 s while in progress.
  - Tech strategy row added.

## Runs

| # | cycle | stage | round | path | result | blocking | advisory | turns | min | tokens | model |
|---|-------|-------|-------|------|--------|----------|----------|-------|-----|--------|-------|
| 1 | 1 | plan | — | reused | plan mode, 7 open tasks | — | — | — | — | — | — |
| 2 | 1 | plan review | 1 | fallback (worker-reviewer) | approve | 0 | 3 | 17 | 1 | 62,931 | sonnet |
| 3 | 1 | gate 1 | — | owner | approved | — | — | — | — | — | — |
| 4 | 1 | implement HL-1+HL-2 (BE-A) | 1 | fallback (worker-builder, worktree) | mvn test green 1707 | — | — | 48 | 8 | 108,667 | sonnet |
| 5 | 1 | implement HL-6+HL-7 (FE) | 1 | fallback (worker-builder, worktree) | vitest 608 green, lint 0 err | — | — | 56 | 10 | 91,218 | sonnet |
| 6 | 1 | code review FE | 1 | fallback (worker-reviewer, quality) | approve | 0 | 3 | 5 | 1 | 36,765 | sonnet |
| 7 | 1 | fix FE advisories | 1 | fallback (same FE worker) | vitest 610 green | — | — | 19 | 3 | — | sonnet |
| 8 | 1 | implement HL-5 (BE-C) | 1 | fallback (worker-builder, worktree) | mvn test green 1738 | — | — | 53 | 7 | 87,312 | sonnet |
| 9 | 1 | implement HL-3+HL-4 (BE-B) | 1 | fallback (worker-builder, worktree) | mvn test green 1749 | — | — | 41 | 11 | 146,322 | sonnet |
| 10 | 1 | code review BE | 1 | fallback (worker-reviewer, security+quality) | revise | 1 | 5 | 7 | 1 | 59,178 | sonnet |
| 11 | 1 | fix BE review | 2 | fallback (same BE-B worker) | mvn test green 1756 | — | — | 22 | 5 | — | sonnet |
| 12 | 1 | code review BE | 2 | fallback (worker-reviewer, security+quality) | approve | 0 | 3 | 5 | 1 | 37,918 | sonnet |
| 13 | 1 | fix BE advisory (OUTPUT_MISSING) | — | orchestrator | `64d66721`, tests green | — | — | — | 2 | — | opus |
| 14 | 1 | local QA ×2 | 1 | `qa-twice.sh` | fail 2 (load, env) | — | — | — | 4 | — | — |
| 15 | 1 | local QA ×2 | 2 | `qa-twice.sh` | pass 347 | — | — | — | 4 | — | — |
| 16 | 1 | land: full gates | — | orchestrator | green | — | — | — | 6 | — | opus |

The orchestrator ran `mvn verify` once on the merged `main` before the fix (1780 tests, green), then again after it (1788 tests, green).

## Open advisories

Applied: 3 from plan review, 3 from FE review, 4 from BE round 1, and 1 from BE round 2 (OUTPUT_MISSING). Left open:
- **BE r1, segments served without a DB/visibility check.** This is spec-conformant (BR-13), and the plan Risks row records it.
- **BE r1, hardcoded `https://canh-labs.com` in `Converter`.** This is the same debt as `embedLink`. Follow-up: move it to `AppProperties`.
- **BE r2, symlink check is lexical and not atomic with the open** (CWE-367). The risk is low, because only the app writes `video-cache/hls/`.
- **BE r2, published dir briefly visible while the status is still PROCESSING.** It is harmless, because the playlist endpoint requires READY.
- **Bundle.** The main chunk grew about 1.9 kB gzip (+5.2 kB raw) over the whole run. The plan's "< 1 KB" AC was for HL-6 alone, where it was +0.99 kB gzip. The rest is HL-7 admin UI code and i18n. hls.js itself is a separate 186 kB gzip chunk.

## Manual checks for the owner

1. Create the Harness flag `hls_streaming_enabled`, **OFF**, before or right after the deploy. With no flag, the default `false` already keeps everyone on Range.
2. Turn HLS on for one H.264 video in the admin table. It should reach READY in under 30 s. Then open `/api/v1/funny-app/video-stream/hls/{fileId}/playlist.m3u8` in Safari: it plays.
3. Turn HLS on for an iPhone HEVC video. It should end ERROR "unsupported codec".
4. Turn the flag ON and reload the feed. Network shows `.m3u8`/`.ts` for the READY video and `/stream/` for the others. Check resume and watch history.
5. Delete one segment on the VM during playback. Playback continues through Range.
6. Turn HLS off for the video: the directory is gone. Turn the flag OFF: the feed is back to Range.
7. Run `./api/mock-test.sh` after the deploy.
- **Rollback note:** turn the flag OFF first, then revert HL-7..HL-1, then remove `video-cache/hls/` on the VM.

## Ignored instructions

- None (local run).

## Cost

API list-price equivalent, not a bill. Window 2026-10-24T03:28:45Z → 04:07:29Z.

| who | model | requests | input | cache write | cache read | output | cost |
|---|---|---|---|---|---|---|---|
| main session (orchestrator) | claude-opus-5-5 | 46 | 118 | 68,517 | 14,320,559 | 29,726 | $4.01 |
| worker-builder: Implement HL-3 and HL-4 backend | claude-sonnet-5-5 | 52 | 106 | 161,871 | 6,237,344 | 62,581 | $1.65 |
| worker-builder: Implement HL-5 backend | claude-sonnet-5-5 | 22 | 44 | 80,424 | 1,232,532 | 29,214 | $0.62 |
| worker-reviewer: Code review backend diff | claude-sonnet-5-5 | 6 | 12 | 56,451 | 192,372 | 5,749 | $0.22 |
| worker-reviewer: Code review backend round 2 | claude-sonnet-5-5 | 4 | 8 | 36,564 | 86,190 | 2,465 | $0.12 |
| worker-builder: Implement HL-1 and HL-2 backend | claude-sonnet-5-5 | 18 | 36 | 106,947 | 1,348,042 | 33,082 | $0.73 |
| worker-reviewer: Code review frontend diff | claude-sonnet-5-5 | 4 | 8 | 35,114 | 81,868 | 3,700 | $0.13 |
| worker-reviewer: Plan review round 1 | claude-sonnet-5-5 | 5 | 12 | 60,935 | 181,579 | 8,068 | $0.25 |
| worker-builder: Implement HL-6 and HL-7 frontend | claude-sonnet-5-5 | 35 | 72 | 100,192 | 2,481,921 | 35,756 | $0.86 |
| **TOTAL** |  | **192** | **416** | **707,015** | **26,162,407** | **210,341** | **$8.59** |

| job | turns | cost | models | run |
|-----|-------|------|--------|-----|
| local | 192 requests | $8.59 | opus 5.5 + sonnet 5.5 | 2026-10-24-hls-streaming |
