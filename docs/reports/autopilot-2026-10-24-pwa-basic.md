# Autopilot run: Basic PWA

- **Request:** `/autopilot PWA cơ bản: service worker, cài được, có thông báo phiên bản mới`
- **Mode:** local, request mode. It started as a lite run and was promoted to full mode when the non-test diff reached about 130 LOC.
- **Plan:** [plan-pwa-basic](../plans/plan-pwa-basic.md)
- **Started:** 2026-10-24 20:17 · **Landed:** 2026-10-24 20:32
- **Result:** ✅ committed `a6dfd4b3`, `32839c3c`, `ae83a137` (Gate 2 pending at report time)

## Pipeline

| Stage | Result | Rounds |
|-------|--------|--------|
| Start | owner request in chat | — |
| Plan | 2 tasks, PW-1 and PW-2. A hand-written service worker, so there is no new dependency and no ADR (`vite-plugin-pwa` was deliberately not used) | — |
| Plan review | **Round 1: revise, 2 blocking.** (1) A build-scoped `/sw.js?build=<sha>` is never re-fetched by an already-open tab, so the "new version" banner would never appear there. (2) `clients.claim()` fires `controllerchange` on the first install, so a "reload once" guard would reload the first visit. Redesigned: version detection polls a build-id `version.json` independently of the SW, and the SW never reloads pages. **Round 2: approve**, and 2 advisories were folded in (a 60-entry asset cap, rollback documented) | 2 of 3 |
| Gate 1 (owner) | approved | — |
| Implement | 1 worker-builder in a worktree, 2 commits cherry-picked. The non-test diff is about 130 LOC, so the run was promoted to full mode and the plan moved to `docs/plans/` | 1 of 3 |
| Code review | Correctness, security and quality: approve, 0 blocking, 3 advisory. One was fixed in `ae83a137`: only a `text/html` response may replace the cached shell | 1 of 3 |
| Local QA ×2 | `qa-twice.sh` on the 3 PWA test files: 28 tests, pass 28 · flaky 0 · fail 0 | 1 of 3 |
| Land: full gates | `npm run test`: 72 files, 638 tests. `npm run lint`: 0 errors (3 old warnings). `npm run build`: emits `dist/sw.js` and `dist/version.json` (`{"build":"<sha7>"}`). Main chunk 797.56 kB, about +1.5 kB. Backend unchanged | — |
| Gate 2 (owner) | pending | — |
| CI + smoke | pending (after push) | — |

## What changed

- **`webapp/public/sw.js`** is a classic service worker with no imports and cache `fm-shell-v1`. It handles only same-origin GET requests without a Range header:
  - **Navigations** are network-first with `cache:'no-cache'`. Only an `ok`, non-redirected `text/html` response is stored, and always under the fixed key `/`. If the network fails, it serves `caches.match('/')`.
  - **`/assets/*`** (hashed Vite files) are cache-first. HTML is never stored, and the cache is capped at 60 entries without ever removing `/`.
  - **Everything else** is not intercepted: `/api/`, `/video-stream/`, SSE, `version.json`, other origins and non-GET requests.
  - The worker activates immediately, deletes other caches, and never reloads a page.
- **`webapp/vite.config.js`** has an inline `version-file` plugin that emits `version.json` with the same build id as `__APP_BUILD__`.
- **`webapp/src/pwa/registerSW.js`** registers `/sw.js` (scope `/`, `updateViaCache:'none'`) in production builds only.
- **`useNewVersion`** fetches `/version.json?t=<now>` with `no-store` when the tab becomes visible and every 15 minutes. **`UpdateBanner`** then shows "Có phiên bản mới / A new version is available" with Reload and dismiss (remembered per build).
- **Manifest and `index.html`:**
  - the name is now "Funny Movies";
  - `id` and `scope` are set to `/`;
  - icons are split into `any` and `maskable` entries;
  - `theme-color` and the Apple web-app meta tags are added.

## Runs

| # | cycle | stage | round | path | result | blocking | advisory | turns | min | tokens | model |
|---|-------|-------|-------|------|--------|----------|----------|-------|-----|--------|-------|
| 1 | 1 | plan | 1 | orchestrator (lite) | plan, 2 tasks | — | — | — | 6 | — | opus |
| 2 | 1 | plan review | 1 | fallback (worker-reviewer) | revise | 2 | 5 | 11 | 1 | 37,808 | sonnet |
| 3 | 1 | plan (revise) | 2 | orchestrator | version.json detection, no SW reload | — | — | — | 3 | — | opus |
| 4 | 1 | plan review | 2 | fallback (worker-reviewer) | approve | 0 | 2 | 2 | 1 | 42,958 | sonnet |
| 5 | 1 | gate 1 | — | owner | approved | — | — | — | — | — | — |
| 6 | 1 | implement PW-1+PW-2 | 1 | fallback (worker-builder, worktree) | vitest 638, lint 0 err, build ok | — | — | 41 | 5 | 70,726 | sonnet |
| 7 | 1 | code review | 1 | fallback (worker-reviewer) | approve | 0 | 3 | 3 | 1 | 32,563 | sonnet |
| 8 | 1 | fix advisory (shell needs text/html) | — | orchestrator | `ae83a137`, 28 PWA tests green | — | — | — | 2 | — | opus |
| 9 | 1 | local QA ×2 | 1 | `qa-twice.sh` | pass 28 | — | — | — | 1 | — | — |
| 10 | 1 | land: full gates | — | orchestrator | green | — | — | — | 3 | — | opus |

## Open advisories

- **Host caching of `sw.js` and `version.json`.** nginx or Cloudflare could cache `/sw.js`. That delays only SW changes, not app updates, because `version.json` is fetched with a unique query. To be safe, configure `Cache-Control: no-cache` for `/sw.js` and `/version.json` on the VM's nginx (outside the repo).
- **Maskable icon.** It reuses the same 192 and 512 PNGs as the `any` icon, so Android's maskable crop may clip the art unless it has safe-zone padding. A padded maskable icon would fix it.
- **No 180 px `apple-touch-icon`.** The 192 px icon is used and iOS scales it.
- **Rollback deploys** also show the banner, because the build id differs. This is accepted and documented in the hook.

## Manual checks for the owner

1. Chrome on Android or desktop: the install icon or menu item appears, and the installed app opens standalone with the red theme colour.
2. iOS Safari: Share → Add to Home Screen opens full screen.
3. DevTools → Application → Service Workers shows `/sw.js` activated. In Network, `/api/…` and `/video-stream/…` show no "(ServiceWorker)" source.
4. Login by magic link still works (`?token=` navigation is not cached).
5. After the next deploy, a tab that stays open shows the banner within 15 minutes, or at once when you switch back to it. Reload loads the new build, and `/version.json` shows the new short SHA.
6. Offline (DevTools → Network → Offline): reloading shows the app shell, not the browser error page. Videos and API calls fail as expected.

## Ignored instructions

- None (local run).

## Cost

API list-price equivalent, not a bill. Window 2026-10-24T13:17:51Z → 13:30:47Z.

| who | model | requests | input | cache write | cache read | output | cost |
|---|---|---|---|---|---|---|---|
| main session (orchestrator) | claude-opus-5-5 | 19 | 46 | 35,470 | 13,270,913 | 19,324 | $3.32 |
| worker-builder: Implement PW-1 and PW-2 | claude-sonnet-5-5 | 19 | 38 | 69,358 | 902,749 | 21,442 | $0.48 |
| worker-reviewer: Code review PWA diff | claude-sonnet-5-5 | 4 | 10 | 31,266 | 77,772 | 3,407 | $0.12 |
| worker-reviewer: Plan review PWA basic | claude-sonnet-5-5 | 6 | 16 | 41,246 | 159,400 | 8,191 | $0.20 |
| **TOTAL** |  | **48** | **110** | **177,340** | **14,410,834** | **52,364** | **$4.12** |

| job | turns | cost | models | run |
|-----|-------|------|--------|-----|
| local | 48 requests | $4.12 | opus 5.5 + sonnet 5.5 | 2026-10-24-pwa-basic |
