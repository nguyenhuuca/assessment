# Feature Specification: Frontend Dependency Upgrade

<!--
Feature Specification
Filename: docs/specs/spec-frontend-deps-upgrade.md
Owner: Builder (/builder)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)

Related:
- PRD: docs/prd/PRD-frontend-deps-upgrade.md
- ADR: docs/adr/0034-frontend-deps-upgrade.md
- Plan: docs/plans/plan-frontend-deps-upgrade.md
-->

## Metadata

**Status:** Approved
**Author:** nguyenhuuca (with Claude Code `/spec`)
**Date:** 2026-10-29
**Related PRD:** [PRD-frontend-deps-upgrade](../prd/PRD-frontend-deps-upgrade.md)
**Related ADR:** [ADR-0034](../adr/0034-frontend-deps-upgrade.md)
**Sequence diagrams:** [sequence-frontend-deps-upgrade](../sequence/sequence-frontend-deps-upgrade.md)

---

## Overview

Every dependency in `webapp/package.json` moves to its latest release (23 packages) or is removed (4 unused `@fortawesome/*` packages), and every CI job that installs or builds `webapp/` runs on Node 22. The built SPA must behave exactly as before for viewers and admins; only the toolchain, configuration files and test setup change where a migration requires it.

---

## Baseline (measured 2026-10-29, before the upgrade)

| Measure | Value | How |
|---------|-------|-----|
| Outdated packages | 27 | `npm outdated` |
| Main JS chunk `assets/index-*.js` | 798.54 kB, **246.44 kB gzip** | `npm run build` output |
| Lazy `assets/hls-*.js` chunk | 595.01 kB, 185.84 kB gzip | `npm run build` output |
| CSS `assets/index-*.css` | 298.97 kB, 43.13 kB gzip | `npm run build` output |
| `dist/` top level | `assets/`, `icons/`, `index.html`, `manifest.json`, `sw.js`, `version.json` | `ls dist` |
| Build time (local, Node 24) | 6.25 s (Vite), 8.5 s wall | `time npm run build` |
| Lint | 0 errors, 3 warnings (`react-refresh/only-export-components`) | `npm run lint` |
| Tests | 76 files, 655 tests, all pass | `npx vitest run --root .` |
| `npm audit` | 16 (1 low, 3 moderate, 10 high, 2 critical) | `npm audit` |

---

## Business Rules

### Rule 1: Everything current
After the change, `cd webapp && npm outdated` prints nothing.

### Rule 2: Unused packages are removed, not upgraded
`@fortawesome/free-brands-svg-icons`, `@fortawesome/free-regular-svg-icons`, `@fortawesome/free-solid-svg-icons` and `@fortawesome/react-fontawesome` are deleted from `package.json` and the lockfile. No source file imports them before or after.

### Rule 3: hls.js is pinned exactly
`package.json` contains `"hls.js": "1.7.4"` with no range operator (ADR-0031 intent kept).

### Rule 4: Other ranges use caret
Every other dependency uses a `^<latest>` range, matching the file's current style.

### Rule 5: No test is skipped or deleted
All 76 test files and the 655 tests from the baseline still exist and pass. A test may be edited only where the new test tooling changes an API it uses, and the assertion it makes must stay the same.

### Rule 6: Lint rule fallback threshold
For new rules introduced by `eslint-plugin-react-hooks` 7: if the total number of new errors is **≤ 10** and each can be fixed locally (inside one function or hook, without changing a component's structure or props), fix them. If there are **> 10**, or any one needs refactoring, set each affected rule to `warn` in `eslint.config.js` and add one follow-up line per rule to `TASKS.md`. Per-line `eslint-disable` comments are not allowed.

### Rule 7: One commit, verified in groups
The change lands as one commit on `main`, but the builder runs the gates after each package group (Plan tasks) and does not squash until every group is green.

### Rule 8: Same Node everywhere in CI
`deploy-web.yml` and the webapp setup step in `autopilot.yml` both use `node-version: '22'`. No workflow that runs `npm ci` in `webapp/` stays on 20.

---

## Functional Requirements

### FR-1: In-range upgrades
The system must move these packages to latest: `react`, `react-dom` 19.3.1; `@types/react`, `@types/react-dom` 19.3.0; `@tanstack/react-query` 5.105.0; `react-router-dom` 7.18.5; `i18next` 26.5.11; `react-i18next` 17.1.9; `i18next-browser-languagedetector` 8.2.3; `@testing-library/react` 16.3.3; `@testing-library/user-event` 14.6.8; `cronstrue` 3.38.0; `hls.js` 1.7.4 (exact).

### FR-2: Build and test toolchain
The system must move `vite` to 8.x, `@vitejs/plugin-react` to 6.x, `vitest` to 5.x, `jsdom` to 29.x and `@testing-library/jest-dom` to 7.x, and update `vite.config.js`, `vitest.config.js` and `src/test-setup.js` only where their migration guides require it. The custom `version-file` plugin and the `__APP_BUILD__` define must keep their current output.

### FR-3: Lint toolchain
The system must move `eslint` to 10.x, `@eslint/js` to 10.x, `eslint-plugin-react-hooks` to 7.x, `eslint-plugin-react-refresh` to 0.5.x and `globals` to 17.x. `eslint.config.js` must be adapted to the new config exports (the current file spreads `reactHooks.configs.recommended.rules`). New rule violations follow Rule 6.

### FR-4: Remove FontAwesome
The system must not declare any `@fortawesome/*` package.

### FR-5: Node 22 in CI
`.github/workflows/deploy-web.yml` and `.github/workflows/autopilot.yml` must set `node-version: '22'` on their `actions/setup-node` steps that install `webapp/`.

### FR-6: Documentation agrees with the code
- `.claude/rules/tech-strategy.md` and its mirror `docs/claude/rules/tech-strategy.md`: *Build Tool* → `Vite 8`; *HLS Playback* → `hls.js 1.7.4 (ADR-0031; …)`
- `README.md` stack table: *Build Tool* → `Vite 8`; *Icons* → `Material Symbols Outlined` (the README still names FontAwesome, which the code does not use)
- `docs/tracking.md` row updated when the work lands

### FR-7: Commit body reports measurements
The commit message must list the new main-chunk gzip size, the `hls` chunk presence, the lint warning count and the `npm audit` counts after the upgrade.

---

## API Changes

### New Endpoints

N/A: no backend change.

### Modified Endpoints

N/A: no backend change. The SPA keeps calling the same `/v1/funny-app` endpoints.

---

## Database Changes

### New Tables

N/A.

### Modified Tables

N/A.

### No Changes

No migration. `api/` is untouched.

---

## Security Requirements

### Authentication

Unchanged: JWT handling in the SPA (`webapp/src/api/`) is not modified.

### Authorization

Unchanged.

### Data Validation

Unchanged.

### Sensitive Data

No new data handling. Supply chain: only packages already declared (or their new majors) are installed; no new package is added. `npm audit` high/critical count must not increase versus the baseline of 12 (PRD NFR-4).

---

## Caching Impact

No server cache impact (no Guava cache is touched).

Browser-side caching must keep working:

| Cache | Impact | Action |
|-------|--------|--------|
| Service worker (`public/sw.js`, copied to `dist/sw.js`) | Caches hashed `assets/*` and the navigation shell | Verify `dist/sw.js` is still emitted and assets keep content-hashed names |
| `version.json` new-version check (`src/pwa/useNewVersion.js`) | Compares `/version.json` with `__APP_BUILD__` | Verify `dist/version.json` is emitted with `{"build": "<sha7>"}` and `useNewVersion.test.jsx` passes |
| TanStack Query client cache | Library minor bump only | Covered by existing tests |

---

## Frontend Changes

### New Routes

None.

### Modified Components

| Component | File | Change |
|-----------|------|--------|
| Any component flagged by a new react-hooks 7 rule | `webapp/src/**` | Local fix only, per Rule 6 |
| Build config | `webapp/vite.config.js` | Only if Vite 8 / plugin-react 6 require it |
| Test config | `webapp/vitest.config.js`, `webapp/src/test-setup.js` | Only if Vitest 5 / jsdom 29 / jest-dom 7 require it |
| Lint config | `webapp/eslint.config.js` | Adapt to ESLint 10 and react-hooks 7 config exports |
| Manifest | `webapp/package.json`, `webapp/package-lock.json` | New versions, FontAwesome removed, lockfile regenerated |

### State Management

No new React Query keys or API calls.

---

## Event / Job Changes

### Domain Events

No change.

### Scheduled Jobs

No `AppScheduler` change. CI workflows change only their Node version (FR-5).

---

## Non-Functional Requirements

### Performance

| Operation | Target |
|-----------|--------|
| Main JS chunk gzip | ≤ 258.8 kB (baseline 246.44 kB + 5%) |
| `hls.js` | Still a separate chunk, loaded only through `import('hls.js')` in `src/hooks/useHlsSource.js` |
| `npm run build` in CI | Not slower than the previous green "Deploy Frontend" run on Node 20 |

> **Assumption:** the size and time targets are guard rails (PRD NFR-1/2). A miss is investigated and reported in the commit body; it blocks only if the cause is an accidental change in chunking (e.g. `hls.js` merged into the main chunk).

### Availability

Static deploy, no downtime; rollback is a revert commit plus a redeploy.

### Scalability

N/A.

---

## Edge Cases

### EC-1: Lockfile drift
**Condition:** `npm install` keeps old transitive versions from the existing lockfile.
**Expected:** regenerate the lockfile (`rm -rf node_modules package-lock.json && npm install`) after the version bumps, then confirm `npm ci` works from the new lockfile.

### EC-2: Peer dependency conflict
**Condition:** `npm install` reports `ERESOLVE` (e.g. a plugin not yet declaring Vite 8 or ESLint 10 support).
**Expected:** stop and report the conflicting package. Do not use `--force` or `--legacy-peer-deps` to get past it; if no compatible release exists, that package stays one major behind and Rule 1 is recorded as not met in the commit body for owner decision.

### EC-3: Rolldown changes chunk output
**Condition:** after the Vite 8 build, `dist/assets/` has no separate `hls-*.js`, or `version.json` is missing.
**Expected:** treat as a blocker (behaviour change); fix the config so the chunk and file are emitted as before.

### EC-4: New react-hooks rules flag code
**Condition:** `npm run lint` reports errors from rules that did not exist in react-hooks 5.
**Expected:** apply Rule 6 exactly (≤ 10 and local → fix; otherwise `warn` + `TASKS.md` follow-up per rule).

### EC-5: Test fails only because of tooling semantics
**Condition:** a test fails due to a Vitest 5 / jsdom 29 / jest-dom 7 API or behaviour change, not an app bug.
**Expected:** adapt the test to the new API keeping the same assertion (Rule 5). If an app bug is found, fix the app and keep the test.

### EC-6: CI fails on Node 22 but local passes on Node 24
**Expected:** reproduce under Node 22 locally (nvm, or a `node:22` Docker container running `npm ci && npm run build`) before changing code.

---

## Acceptance Criteria

- [ ] `npm outdated` in `webapp/` prints nothing (Rule 1)
- [ ] `package.json` has no `@fortawesome/*` entry and `"hls.js": "1.7.4"` exactly (Rules 2, 3)
- [ ] `npm ci` succeeds from the committed lockfile (EC-1)
- [ ] `npm run lint` has 0 errors; any rule moved to `warn` has a `TASKS.md` follow-up and no `eslint-disable` comment was added (Rule 6)
- [ ] `npx vitest run --root .` passes 76 files and ≥ 655 tests (Rule 5)
- [ ] `npm run build` succeeds; `dist/` contains `version.json`, `sw.js`, `manifest.json`, a separate `assets/hls-*.js`, and the main chunk is ≤ 258.8 kB gzip (FR-2, NFR)
- [ ] `deploy-web.yml` and `autopilot.yml` use Node 22 (Rule 8)
- [ ] tech-strategy (both copies) and README name Vite 8 and hls.js 1.7.4; README icons row names Material Symbols (FR-6)
- [ ] Commit body lists chunk size, lint warnings and `npm audit` counts (FR-7)
- [ ] "Deploy Frontend (React Build)" is green on the pushed commit
- [ ] Prod smoke passes: login, feed loads, HLS playback and Range fallback, admin pages, language switch, PWA new-version banner
- [ ] `mvn verify` not required (no `api/` change)

---

## Open Questions

None. Threshold for lint fallback resolved by owner on 2026-10-29 (Rule 6).

---

## Version History

| Version | Date | Author | Change |
|---------|------|--------|--------|
| 1.0 | 2026-10-29 | nguyenhuuca / Claude Code | Initial spec from PRD + ADR-0034 |
