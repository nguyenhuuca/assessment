# Plan: Frontend Dependency Upgrade

<!--
Implementation Plan
Filename: docs/plans/plan-frontend-deps-upgrade.md
Owner: Builder (/builder)
-->

## Overview

**Status:** In Progress
**Author:** nguyenhuuca
**Date:** 2026-10-29
**Tracking:** `docs/tracking.md` row "Frontend Dependency Upgrade"; `TASKS.md` section "Frontend dependency upgrade"
**Related PRD:** [PRD-frontend-deps-upgrade](../prd/PRD-frontend-deps-upgrade.md)
**Related ADR:** [ADR-0034](../adr/0034-frontend-deps-upgrade.md)
**Spec:** [spec-frontend-deps-upgrade](../specs/spec-frontend-deps-upgrade.md) (cited as Rule n / FR-n / EC-n)
**Sequence:** [sequence-frontend-deps-upgrade](../sequence/sequence-frontend-deps-upgrade.md) (flows 1–4)
**Size:** Small/Medium. 6 tasks; mostly manifest, lockfile and config changes. App code changes only if a new lint rule or a test-tooling change forces them.

## Objective

Bring every `webapp/` dependency to its latest release, remove the four unused `@fortawesome/*` packages, and build the webapp on Node 22 in CI, with no behaviour change for users.

| Package | From | To |
|---------|------|----|
| vite | 6.4.1 | 8.3.5 |
| @vitejs/plugin-react | 4.7.0 | 6.1.2 |
| vitest | 3.2.4 | 5.0.5 |
| jsdom | 26.1.0 | 29.1.1 |
| @testing-library/jest-dom | 6.9.1 | 7.0.1 |
| eslint / @eslint/js | 9.39.4 | 10.13.0 / 10.0.1 |
| eslint-plugin-react-hooks | 5.2.0 | 7.1.1 |
| eslint-plugin-react-refresh | 0.4.26 | 0.5.7 |
| globals | 15.15.0 | 17.13.0 |
| react / react-dom | 19.2.4 | 19.3.1 |
| hls.js (exact pin) | 1.7.3 | 1.7.4 |
| other in-range packages | — | latest (spec FR-1) |
| @fortawesome/* (4) | — | removed |
| CI Node | 20 | 22 |

## Scope

### In Scope

- Spec FR-1 … FR-7: version bumps, FontAwesome removal, config adaptation, Node 22 in `deploy-web.yml` and `autopilot.yml`, docs (tech-strategy ×2, README), measurements in the commit body.
- Local verification after each group, prod deploy and smoke.

### Out of Scope

Same as the PRD: refactoring, new library APIs, replacing libraries, TypeScript, `api/`, `landing/`, Dependabot.

## Technical Approach

### Architecture Changes

None at runtime. Build pipeline changes from Vite 6 (Rollup) to Vite 8 (Rolldown); CI Node 20 → 22.

### Key Decisions

- **One commit, verified in groups** (spec Rule 7). Each task FD-1 … FD-5 ends with the gates green and a local WIP commit; FD-6 squashes them with `git reset --soft <base>` into one commit before asking to push. No interactive rebase.
- **Lockfile regenerated per group** (EC-1): `rm -rf node_modules package-lock.json && npm install`, then `npm ci` once at the end.
- **No `--force` / `--legacy-peer-deps`** (EC-2). A peer conflict stops the task and goes to the owner.
- **Lint fallback** per spec Rule 6 (≤ 10 local fixes, else `warn` + `TASKS.md` follow-up; never `eslint-disable`).
- Order puts the cheap, low-risk group first, so a failure in the toolchain groups is clearly attributable.

## Implementation Steps

Gates for every task (G): `cd webapp && npm run lint && npx vitest run --root . && npm run build`, then check `dist/` for `version.json`, `sw.js`, `manifest.json` and a separate `assets/hls-*.js`.

### Phase 1: Low-risk bumps

1. **FD-1 In-range packages** (FR-1, Rules 3–4)
   - `npm install` the latest of: react, react-dom, @types/react, @types/react-dom, @tanstack/react-query, react-router-dom, i18next, react-i18next, i18next-browser-languagedetector, @testing-library/react, @testing-library/user-event, cronstrue.
   - Set `"hls.js": "1.7.4"` exactly.
   - G; WIP commit.
2. **FD-2 Remove FontAwesome** (FR-4, Rule 2)
   - `npm uninstall` the four `@fortawesome/*` packages; `grep -r fortawesome webapp/src` returns nothing.
   - G; WIP commit.

### Phase 2: Toolchain majors

3. **FD-3 Build and test toolchain** (FR-2, EC-3, EC-5)
   - Bump vite 8, @vitejs/plugin-react 6, vitest 5, jsdom 29, @testing-library/jest-dom 7; regenerate lockfile.
   - Apply only the migration-guide changes needed in `vite.config.js`, `vitest.config.js`, `src/test-setup.js`.
   - Confirm the `version-file` plugin still emits `version.json` and `__APP_BUILD__` is defined; `hls` chunk still separate (sequence flows 3, 4).
   - Record main chunk gzip size (target ≤ 258.8 kB).
   - G; WIP commit.
4. **FD-4 Lint toolchain** (FR-3, Rule 6, EC-4)
   - Bump eslint 10, @eslint/js 10, eslint-plugin-react-hooks 7, eslint-plugin-react-refresh 0.5, globals 17.
   - Adapt `eslint.config.js` to the react-hooks 7 config export (currently spreads `reactHooks.configs.recommended.rules`).
   - Count new errors per rule; apply Rule 6. Add `TASKS.md` follow-ups for any rule set to `warn`.
   - G; WIP commit.

### Phase 3: CI, docs, land

5. **FD-5 Node 22 and docs** (FR-5, FR-6, Rule 8)
   - `deploy-web.yml` and `autopilot.yml`: `node-version: '22'`.
   - `.claude/rules/tech-strategy.md` + `docs/claude/rules/tech-strategy.md`: Vite 8, hls.js 1.7.4.
   - `README.md`: Build Tool → Vite 8; Icons → Material Symbols Outlined.
   - WIP commit.
6. **FD-6 Land and verify** (Rule 1, Rule 7, FR-7)
   - `npm outdated` empty; `npm ci` from the lockfile; final G.
   - Squash WIP commits into one with measurements in the body (chunk sizes, lint warnings, `npm audit` counts).
   - Update `TASKS.md`, `docs/tracking.md`, `session.txt`.
   - Ask the owner before `git push`; watch "Deploy Frontend (React Build)"; owner prod smoke.

## Files to Modify

| File | Change |
|------|--------|
| `webapp/package.json`, `webapp/package-lock.json` | Versions, FontAwesome removed, lockfile regenerated |
| `webapp/vite.config.js` | Only if Vite 8 / plugin-react 6 require it |
| `webapp/vitest.config.js`, `webapp/src/test-setup.js` | Only if Vitest 5 / jsdom 29 / jest-dom 7 require it |
| `webapp/eslint.config.js` | ESLint 10 / react-hooks 7 config shape; possible `warn` overrides |
| `webapp/src/**` | Only local fixes for new lint rules or tooling-driven test edits |
| `.github/workflows/deploy-web.yml`, `.github/workflows/autopilot.yml` | Node 22 |
| `.claude/rules/tech-strategy.md`, `docs/claude/rules/tech-strategy.md`, `README.md` | Versions, icon row |
| `TASKS.md`, `docs/tracking.md`, `session.txt` | Status |

## Dependencies

### Code Dependencies

- Vite 7 + 8, Vitest 4 + 5, ESLint 10, eslint-plugin-react-hooks 7 migration guides (read before FD-3 / FD-4; verify via Context7 per Core Principle 1).

### Service Dependencies

- GitHub Actions `actions/setup-node@v4` with Node 22.

## Testing Strategy

### Unit Tests

Existing 76 Vitest files / 655 tests are the regression suite; none skipped or deleted (Rule 5). No new tests: the change adds no behaviour.

### Integration Tests

Build-output checks after FD-3 and FD-6: `dist/version.json`, `dist/sw.js`, `dist/manifest.json`, separate `dist/assets/hls-*.js`; main chunk ≤ 258.8 kB gzip.

### Manual Testing

Prod smoke after deploy: login, feed, HLS playback and Range fallback, admin pages, language switch, PWA new-version banner.

## Rollback Plan

`git revert <commit>` and push (with owner approval); "Deploy Frontend" redeploys the previous build. No data or API change to undo.

## Risks

| Risk | Mitigation |
|------|------------|
| Rolldown changes chunks or plugin hooks | FD-3 output checks; EC-3 blocks on missing `hls` chunk or `version.json` |
| react-hooks 7 floods lint | Rule 6 threshold and `warn` fallback |
| Test tooling semantics change | EC-5: adapt API usage, keep assertions |
| Peer conflicts | EC-2: stop, no `--force` |
| Node 22 CI-only failure | EC-6: reproduce under Node 22 locally |

## Checklist

### Before Starting

- [x] PRD approved, ADR-0034 accepted, spec + sequence written
- [ ] Baseline recorded (spec "Baseline" table)
- [ ] Working tree clean in `webapp/`

### Before PR

- [ ] All spec Acceptance Criteria pass locally
- [ ] Single squashed commit with measurements

### Before Merge

- [ ] Owner approves push
- [ ] "Deploy Frontend" green, prod smoke passed

## Notes

Commit lands directly on `main` (repo convention); "Before PR" means "before asking to push".

## Progress Log

| Date | Task | Note |
|------|------|------|
| 2026-10-29 | — | Plan written |
| 2026-10-29 | FD-1..FD-5 | Built by autopilot, squashed to 82be7105 |
