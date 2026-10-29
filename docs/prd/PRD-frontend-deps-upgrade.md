# PRD: Frontend Dependency Upgrade

<!--
Product Requirements Document
Filename: docs/prd/PRD-frontend-deps-upgrade.md
Owner: Architect (/architect)
Handoff to: Architect (/architect)
Related Skills: scope, planning-artifacts
-->

## Overview

**Status:** Approved
**Author:** nguyenhuuca (with Claude Code `/scope`)
**Date:** 2026-10-29
**Version:** 1.1
**Tracking:** `docs/tracking.md` row "Frontend Dependency Upgrade"
**PR-FAQ:** N/A (maintenance upgrade, no new user-facing capability)
**Stakeholders:** Project owner (sole maintainer), coding agents working in `webapp/`

## Problem Statement

`webapp/` (React 19 + Vite SPA) has drifted behind its ecosystem. `npm outdated` on 2026-10-29 lists 27 packages behind their latest release, 13 of them by at least one major version. The build toolchain is two majors behind (Vite 6 → 8, Vitest 3 → 5). Every month the gap grows, the eventual migration gets larger, and new plugins and fixes stop targeting the versions we use. The owner wants to pay this technical debt down now, while the gap is still a set of known, documented migrations, in the same way the backend just moved to Spring Boot 4.1 (ADR-0033).

### Evidence

**Quantitative Evidence**:
- 27 outdated packages; 14 can move within their current `^` range, 13 need a major bump (table in Appendix).
- Toolchain gap: `vite` 6.4.1 → 8.3.5, `vitest` 3.2.4 → 5.0.5, `@vitejs/plugin-react` 4.7.0 → 6.1.2, `eslint` 9.39.4 → 10.13.0, `jsdom` 26.1.0 → 29.1.1.
- `npm audit` reports 16 vulnerabilities (1 low, 3 moderate, 10 high, 2 critical), all in transitive dev/build dependencies such as `@babel/core`, `browserslist`, `brace-expansion`, `js-yaml`, `@vitest/mocker`.
- The frontend CI (`deploy-web.yml`) and the autopilot workflow build on Node 20, while the local machine runs Node 24.13.

**Qualitative Evidence**:
- Owner decision (2026-10-29): the goal is to avoid technical debt, not to adopt any new feature.
- Observation: the four `@fortawesome/*` packages are declared in `webapp/package.json` but no file under `webapp/src` imports them; only `CODE_REVIEW.md` mentions them. They are upgrade work with no runtime effect.
- Precedent: the Spring Boot 4.1 upgrade showed that version jumps left to accumulate need a dedicated plan, contract tests and a prod watch.

## Goals & Success Metrics

| Goal | Metric | Target |
|------|--------|--------|
| No outdated frontend dependencies | `cd webapp && npm outdated` | Empty output on the day of merge |
| No regression in quality gates | `npm run lint && npx vitest run --root . && npm run build` | All pass; all 76 existing test files still run and pass, none skipped or deleted |
| Green delivery | "Deploy Frontend (React Build)" for the upgrade commit | Success on first push |
| No visible regression in prod | Manual smoke on prod: login, feed, video playback (HLS and Range fallback), admin pages, language switch, PWA update banner | All pass, no new console errors |

## User Stories

### Maintainer (project owner)

- As the maintainer, I want every frontend dependency on its latest release so that future upgrades are small and security fixes apply without a migration first
  - Acceptance: `npm outdated` is empty and `package.json` ranges point at the new majors
- As the maintainer, I want the upgrade to change no behaviour so that I can ship it without re-testing features by hand beyond a smoke pass
  - Acceptance: the existing test suite passes unchanged except for edits required by the new test tooling, and the prod smoke list passes

### Coding agent working in `webapp/`

- As an agent, I want `tech-strategy.md`, `webapp/CLAUDE.md` and CI to state the same versions the code uses so that I do not generate code for the old toolchain
  - Acceptance: tech-strategy names Vite 8 and hls.js 1.7.4; CI workflows that build the webapp use Node 22

### Viewer (end user)

- As a viewer, I want the site to keep working exactly as before so that the upgrade is invisible to me
  - Acceptance: prod smoke list passes; bundle still loads `hls.js` lazily only for videos with `hlsUrl`

## Requirements

### Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | Bump all in-range packages to latest (React/React DOM 19.3.1, `@types/react*` 19.3.0, TanStack Query 5.105.0, React Router DOM 7.18.5, i18next 26.5.11, react-i18next 17.1.9, language detector 8.2.3, Testing Library react 16.3.3 / user-event 14.6.8, hls.js 1.7.4, cronstrue 3.38.0) | Must Have | `npm update` plus range bumps where needed; hls.js stays pinned exactly (`"hls.js": "1.7.4"`, no `^`), as ADR-0031 pinned 1.7.3 |
| FR-2 | Bump build/test toolchain: `vite` 8, `@vitejs/plugin-react` 6, `vitest` 5, `jsdom` 29, `@testing-library/jest-dom` 7 | Must Have | Fix `vite.config.js`, `vitest.config.js` and test setup only as far as the migration guides require |
| FR-3 | Bump lint toolchain: `eslint` 10, `@eslint/js` 10, `eslint-plugin-react-hooks` 7, `eslint-plugin-react-refresh` 0.5, `globals` 17 | Must Have | New rule violations must be fixed or explicitly configured in `eslint.config.js`, not silenced per line |
| FR-4 | Remove the four unused `@fortawesome/*` packages from `package.json` | Must Have | No file in `webapp/src` imports them; removing a dead dependency is not replacing a library (resolved 2026-10-29) |
| FR-5 | Move webapp CI builds to Node 22 LTS in the same commit: `deploy-web.yml` and the webapp setup in `autopilot.yml` | Must Have | Vite 8 needs Node ≥ 20.19; Node 22 is the current LTS; both workflows build the same lockfile, so they must not diverge |
| FR-6 | Update `.claude/rules/tech-strategy.md` (Vite 8, hls.js 1.7.4), its docs mirror `docs/claude/rules/tech-strategy.md`, `webapp/CLAUDE.md`, and the README stack tables where they name these versions | Must Have | One source of truth for agents |
| FR-7 | Land everything as one commit on `main` | Must Have | Owner decision; verification still happens group by group locally (see Risks) |
| FR-8 | Clear the `npm audit` high/critical findings that disappear with the upgrade | Nice to Have | Not a success metric; report the remaining count in the commit body |

### Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Bundle size | Main JS chunk gzip size within +5% of the current build; `hls.js` stays a separate lazy chunk |
| NFR-2 | Build time | `npm run build` in CI not slower than today |
| NFR-3 | Compatibility | Same browser support as today (Vite default targets); PWA service worker and `version.json` still emitted |
| NFR-4 | Security | No new `npm audit` high/critical finding introduced by the upgrade |

> **Assumption:** NFR-1 and NFR-2 are guard rails, not goals. The owner chose "0 outdated + gates green" as the success measure, so a size or speed miss is investigated but does not by itself block the release.

## Scope

### In Scope

- 23 outdated packages brought to their latest version; the 4 outdated `@fortawesome/*` packages removed as unused (FR-4)
- Code, config and test changes strictly required by those upgrades
- Node 22 for the CI jobs that install and build `webapp/` (`deploy-web.yml`, `autopilot.yml`)
- Documentation that states these versions (tech-strategy, `webapp/CLAUDE.md`, READMEs)

### Out of Scope

- Refactoring, new features, or adopting new APIs from the upgraded libraries
- Replacing a library with another one (e.g. a different icon set, test runner or router)
- Migrating to TypeScript
- Backend (`api/`), `landing/`, `mcp/` or any other package
- Dependabot/Renovate automation for the webapp

## Dependencies

| Dependency | Owner | Status | Risk |
|------------|-------|--------|------|
| ADR for the toolchain change (Vite 6 → 8, Node 20 → 22) | Owner + `/architect` | Not started | Low |
| Vite 7 and Vite 8 migration guides (Node floor, Rolldown bundler, config changes) | Vite | Published | Med |
| Vitest 4 and 5 migration guides | Vitest | Published | Med |
| ESLint 10 and `eslint-plugin-react-hooks` 7 rule changes | ESLint / React | Published | Med |
| GitHub Actions `setup-node` with Node 22 | GitHub | Available | Low |

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| One commit hides which package broke something | H | M | Upgrade and run gates group by group locally (FR-1 → FR-2 → FR-3 → FR-4), squash into one commit only when all groups are green |
| Vite 8 (Rolldown) changes chunking or plugin hooks, breaking the custom `version-file` plugin or the lazy `hls.js` chunk | M | H | Check `dist/` for `version.json` and a separate `hls` chunk; run the PWA new-version test |
| `eslint-plugin-react-hooks` 7 adds compiler-style rules that flag many existing components | H | M | Fix real violations; if the count is large, enable the new rules at `warn` and record a follow-up in `TASKS.md` instead of rewriting components (refactor is out of scope) |
| Vitest 5 / jsdom 29 change mocking, timers or DOM behaviour and fail existing tests | M | M | Follow the migration guides; never delete or skip a test to get green |
| React Router 7.18 or TanStack Query minor changes alter behaviour | L | M | Covered by the existing test suite and the prod smoke list |
| Node 22 in CI differs from local Node 24 | L | L | Both are ≥ the Vite 8 floor; build once locally with Node 22 if CI fails |

## Open Questions

- [x] Unused `@fortawesome/*` packages: **remove** them (FR-4). Resolved by owner 2026-10-29.
- [x] hls.js: **keep an exact pin**, now `1.7.4` (FR-1). Resolved by owner 2026-10-29.
- [x] Node bump in `autopilot.yml`: **same commit** as `deploy-web.yml` (FR-5). Resolved by owner 2026-10-29.

## Appendix

### Mockups/Wireframes

N/A: no UI change.

### Research

Outdated packages on 2026-10-29 (`npm outdated`, current → latest):

| Group | Packages |
|-------|----------|
| In range (minor/patch) | react / react-dom 19.2.4 → 19.3.1; @types/react 19.2.14 → 19.3.0; @types/react-dom 19.2.3 → 19.3.0; @tanstack/react-query 5.96.1 → 5.105.0; react-router-dom 7.13.2 → 7.18.5; i18next 26.4.4 → 26.5.11; react-i18next 17.0.16 → 17.1.9; i18next-browser-languagedetector 8.2.1 → 8.2.3; @testing-library/react 16.3.2 → 16.3.3; @testing-library/user-event 14.6.1 → 14.6.8; hls.js 1.7.3 → 1.7.4; cronstrue 3.29.0 → 3.38.0; @fortawesome/free-regular-svg-icons 7.2.0 → 7.3.1 |
| Build/test (major) | vite 6.4.1 → 8.3.5; @vitejs/plugin-react 4.7.0 → 6.1.2; vitest 3.2.4 → 5.0.5; jsdom 26.1.0 → 29.1.1; @testing-library/jest-dom 6.9.1 → 7.0.1 |
| Lint (major) | eslint 9.39.4 → 10.13.0; @eslint/js 9.39.4 → 10.0.1; eslint-plugin-react-hooks 5.2.0 → 7.1.1; eslint-plugin-react-refresh 0.4.26 → 0.5.7; globals 15.15.0 → 17.13.0 |
| Icons (major, unused → removed) | @fortawesome/free-brands-svg-icons 6.7.2 → 7.3.1; @fortawesome/free-solid-svg-icons 6.7.2 → 7.3.1; @fortawesome/react-fontawesome 0.2.6 → 3.5.0 |

Related: ADR-0031 (hls.js pin), ADR-0033 (backend Spring Boot 4.1 upgrade, same "avoid version debt" motivation).

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product | nguyenhuuca | 2026-10-29 | Approved |
| Engineering | nguyenhuuca | 2026-10-29 | Approved |
| Design | N/A | | N/A |

---

## Next Steps & Handoffs

After PRD approval:

1. [ ] **Architect Review**: Technical feasibility assessment
   - Trigger: `/architect`
   - Output: ADR (`docs/adr/0034-frontend-deps-upgrade.md`)

2. [ ] **UI/UX Designer**: N/A, no UI change

3. [ ] **Engineering Estimate**: Effort estimation and decomposition
   - Trigger: `/spec` (spec + `docs/sequence/sequence-frontend-deps-upgrade.md`), then the plan
   - Output: Implementation Plan (`docs/plans/plan-frontend-deps-upgrade.md`)

4. [ ] **Track it**: add the feature row to `docs/tracking.md`
   - Then decompose: `/swarm-plan docs/prd/PRD-frontend-deps-upgrade.md`

**Related Artifacts**:
- ADR: [ADR-0034](../adr/0034-frontend-deps-upgrade.md)
- Design Spec: N/A
- Implementation Plan: pending

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-10-29 | nguyenhuuca / Claude Code | Initial draft from `/scope` |
| 1.1 | 2026-10-29 | nguyenhuuca / Claude Code | Open questions resolved: remove FontAwesome, keep hls.js exact pin, Node 22 in autopilot too |
