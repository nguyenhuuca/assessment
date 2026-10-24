# Plan: Basic PWA (service worker, installable, new-version notice)

<!--
Implementation Plan
Filename: docs/plans/plan-pwa-basic.md
Owner: Builder (/builder)
-->

**Status:** Done (2026-10-24)
**Date:** 2026-10-24
**Run:** autopilot `2026-10-24-pwa-basic` (started lite, promoted to full mode when the non-test diff reached ~130 LOC)
**PRD / ADR / Spec:** none (small feature; no new dependency, hand-written service worker)

Request: "PWA cơ bản: service worker, cài được, có thông báo phiên bản mới".

Facts (read by the orchestrator):
- `webapp/public/manifest.json` exists (name/short_name "funny-app", start_url "/", display standalone, theme #FF0000, icons 192/512 `any maskable`) and is linked from `webapp/index.html:7`. There is no service worker anywhere (`grep serviceWorker webapp/src` → none), so Chrome does not offer install and nothing caches.
- `webapp/index.html` has no `theme-color`, `apple-touch-icon` or `apple-mobile-web-app-*` meta.
- Build id: `webapp/vite.config.js:8` defines `__APP_BUILD__` = first 7 chars of `GITHUB_SHA` (or `dev`).
- Deploy: `.github/workflows/deploy-web.yml:28-55` runs `npm run build`, scp's `webapp/dist/` to the VM and reloads nginx; Cloudflare sits in front (`scripts/monitoring/nginx-alert/cloudflare-realip.conf`). A plain `/sw.js` could be held by an edge or HTTP cache, so the SW URL carries the build id.
- Entry: `webapp/src/main.jsx` renders `<App/>`; the shell is `webapp/src/components/layout/AppShell.jsx:49` (main content at `:370`). i18n namespaces live in `webapp/src/i18n/locales/{en,vi}/*.json` (`common.json`, `shell.json`, …).
- Tech strategy: no new dependency is added (hand-written classic service worker; `vite-plugin-pwa`/Workbox deliberately not used, so no ADR).

| ID | Task | Files | Acceptance criteria | Est. LOC |
|----|------|-------|---------------------|----------|
| PW-1 | Service worker (installable, safe caching) + build-id file | new `webapp/public/sw.js`; new `webapp/src/pwa/registerSW.js`; `webapp/src/main.jsx` (call it); `webapp/vite.config.js` (inline plugin, no dependency) | (1) `vite.config.js`: an inline plugin emits `version.json` = `{"build":"<__APP_BUILD__ value>"}` in `generateBundle` (no new npm package). (2) `sw.js` (classic, no imports, no automatic reload logic): cache name `fm-shell-v1`; `install` → `self.skipWaiting()`; `activate` → delete every cache except the current one, then `clients.claim()`. `fetch` handler only for **same-origin GET without a `Range` header**: (a) navigation → `fetch(req, {cache:'no-cache'})` (network-first, never a stale HTTP/edge copy); store only `ok && !redirected` responses, always under the fixed key `'/'` (never the request URL, so `?token=`/`?v=` deep links are never cached); on network failure → `caches.match('/')`; (b) `/assets/*` → cache-first; store only `ok` responses whose `content-type` is not `text/html` (never the SPA fallback page); after storing, if the cache holds more than 60 entries the oldest ones (by `cache.keys()` order, never `'/'`) are deleted, so assets of old builds do not pile up (plan-review r2 [A]); (c) everything else (`/api/`, `/video-stream/`, SSE, `version.json`, other origins such as the API host `canh-labs.com`, fonts, images, non-GET, any `Range` request) → not intercepted (no `respondWith`). (3) `registerSW()`: only when `'serviceWorker' in navigator` and `import.meta.env.PROD`; registers `/sw.js` with `{ scope: '/', updateViaCache: 'none' }`; never reloads the page itself. (4) Tests (Vitest): load `public/sw.js` text into a fake `self` (vm / new Function) and assert: `/api/x`, cross-origin `https://canh-labs.com/api/...`, `/video-stream/...`, POST, a `Range` request and `version.json` are not intercepted; `/assets/a.js` cache-first and an HTML response for `/assets/missing.js` is not stored; navigation to `/?token=abc` fetches with `cache:'no-cache'`, stores under `'/'` only, and falls back to `caches.match('/')` offline; redirected/non-ok navigation responses are not stored; activate deletes other caches. Registration: not called without SW support or when `PROD` is false (`vi.stubEnv`), registers `/sw.js` with the options above | ~60 + ~100 |
| PW-2 | New-version notice + installable metadata | new `webapp/src/pwa/useNewVersion.js`; new `webapp/src/components/layout/UpdateBanner.jsx`; `AppShell.jsx` (render it); `webapp/public/manifest.json`; `webapp/index.html`; `webapp/src/i18n/locales/{en,vi}/shell.json`; styles in `webapp/src/styles/index.css` | (1) `useNewVersion()` (PROD only; any build-id mismatch shows the banner, a rollback deploy included — documented in the hook comment): fetches `/version.json?t=<Date.now()>` with `{cache:'no-store'}` when the tab becomes visible (`visibilitychange`) and every 15 min; when `build` differs from `__APP_BUILD__` it returns `updateAvailable=true`; network/JSON errors are ignored; listeners and the interval are removed on unmount. (2) `UpdateBanner`: hidden until `updateAvailable`; small fixed banner "Có phiên bản mới" / "A new version is available", button "Tải lại" / "Reload" → `window.location.reload()`, and a dismiss "×" (stays dismissed for that build); `role="status"`, labelled buttons. (3) Manifest: `name`/`short_name` "Funny Movies", `id` "/", `scope` "/"; icons split into separate `purpose: "any"` and `purpose: "maskable"` entries (same 192/512 files). (4) `index.html`: `theme-color` #FF0000, `apple-touch-icon` → `/icons/icon-app-192.png`, `apple-mobile-web-app-capable` yes, `apple-mobile-web-app-title` "Funny Movies". (5) en/vi keys. (6) Tests: mismatch → banner shows; same build → hidden; fetch error → hidden; visibilitychange triggers a check; Reload calls `location.reload`; dismiss hides; no polling outside PROD (`vi.stubEnv`, `__APP_BUILD__` stub/define); i18n keys in both languages | ~45 + ~60 |

Design note (plan-review round 1 [B]): version detection is independent of the service worker (a build-scoped SW URL is never re-fetched by an open old tab, and `controllerchange` would reload on first install). The SW activates immediately and never reloads pages; assets are hashed and the deploy (`.github/workflows/deploy-web.yml:36-45`, scp without cleanup) keeps old files, so an old tab keeps working until the user reloads.

Out of scope: install button / `beforeinstallprompt` UI, Web Push, offline video, caching API responses, nginx/Cloudflare header changes.
Risks: a bad SW could pin users on an old shell — mitigated by navigation network-first with `cache:'no-cache'`, `updateViaCache:'none'`, a stable `/sw.js` re-checked by the browser on navigation, and no caching of anything but hashed assets and the `/` shell; a SW is easy to remove later (ship one that unregisters itself). Cloudflare could hold `/sw.js` at the edge for its TTL — this only delays SW changes, not app updates (version.json is fetched with a unique query). iOS: install via Share → Add to Home Screen (no prompt); a 180 px apple-touch-icon is not produced (192 px is used; iOS scales it).
Manual checks for the owner (after deploy): Chrome desktop/Android shows the install icon/menu; installed app opens standalone; deploy again → within 15 min (or on returning to the tab) the banner appears in an open tab → Reload loads the new build; DevTools → Application → Service Workers shows `/sw.js` activated; `/version.json` returns the new short SHA; Network shows `/api/` and `/video-stream/` requests not served by the SW.

## Progress Log

| Date | Task | Commit | Note |
|------|------|--------|------|
| 2026-10-24 | — | — | Plan review r1 revise (open-tab update, first-install reload) → redesigned: version.json polling, SW never reloads; r2 approve |
| 2026-10-24 | PW-1, PW-2 | a6dfd4b3, 32839c3c, ae83a137 | Built by autopilot; code review approve (advisory fix: shell requires text/html); QA ×2 28/28; vitest 638/638, lint 0 errors, build emits sw.js + version.json |
