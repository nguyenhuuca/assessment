# ADR-0034: Upgrade the Webapp Toolchain to Vite 8 and Build on Node 22

## Metadata

**Status:** Accepted · **Date:** 2026-10-29 · **Deciders:** nguyenhuuca · **Tags:** webapp, platform, dependencies, ci
**Related PRD:** [PRD-frontend-deps-upgrade](../prd/PRD-frontend-deps-upgrade.md) · **Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** ⚠️ Changes Golden Path rows — *Build Tool: Vite 6* becomes *Vite 8*, and *HLS Playback: hls.js 1.7.3* becomes *hls.js 1.7.4* (`.claude/rules/tech-strategy.md`). Every other frontend choice (React 19, React Router 7, TanStack Query 5, i18next, cronstrue, Vitest + Testing Library) stays; only versions move. The frontend CI runtime moves from Node 20 to Node 22.

---

## Context

`webapp/` is a React 19 SPA built with Vite and tested with Vitest. On 2026-10-29 `npm outdated` lists 27 packages behind latest, 13 of them by a major version (full list in the PRD appendix). The largest gap is the toolchain: Vite 6.4.1 → 8.3.5, `@vitejs/plugin-react` 4.7.0 → 6.1.2, Vitest 3.2.4 → 5.0.5, ESLint 9 → 10, jsdom 26 → 29. `npm audit` reports 16 findings (2 critical, 10 high), all in transitive build and test dependencies.

The owner's goal is to remove this version debt now, while the jumps are documented migrations, mirroring the backend move to Spring Boot 4.1 (ADR-0033). The owner chose one commit for the whole upgrade and no refactoring beyond what the upgrades require.

Constraints that shape the decision:

- Vite 8 needs Node ≥ 20.19. `deploy-web.yml` and the webapp setup in `autopilot.yml` pin Node 20 (no minor), while the owner's machine runs Node 24.
- The build has custom behaviour that depends on Vite internals: a `version-file` plugin emits `version.json` for the PWA update banner, `define` injects `__APP_BUILD__`, and `hls.js` must stay a lazily loaded chunk (ADR-0031).
- ADR-0031 pins `hls.js` to an exact version so every change to the playback library is deliberate.
- The four `@fortawesome/*` packages are declared but never imported in `webapp/src`.

---

## Decision Drivers

- Remove version debt across the whole frontend, not only the cheap part
- No behaviour change for viewers: playback (HLS and Range fallback), PWA update banner, i18n, admin pages
- Keep CI, autopilot and local builds on a Node version the toolchain supports
- Keep the existing 76 test files and the lint gate meaningful: no skipped tests, no blanket rule disabling
- Keep the playback library pinned deliberately (ADR-0031)

---

## Considered Options

### Option 1: Minor/patch only, stay on Vite 6

Run `npm update` within the current `^` ranges (14 packages) and leave the majors.

| Pros | Cons |
|------|------|
| Lowest risk, no config or test changes | Leaves 13 majors behind, including the whole toolchain; the debt keeps growing |
| No Node or tech-strategy change | Most `npm audit` high/critical findings sit in the old toolchain and stay |
| | Does not meet the PRD success metric (`npm outdated` empty) |

### Option 2: Stop at Vite 7 / Vitest 4 as an intermediate step

Move one major on the toolchain now, the next later.

| Pros | Cons |
|------|------|
| Smaller jump, Vite 7 keeps the Rollup bundler | Two migrations instead of one; the second still has to happen |
| | Still leaves packages outdated, missing the success metric |
| | Ecosystem plugins already target Vite 8 |

### Option 3: Everything to latest, Vite 8 on Node 22 *(chosen)*

Upgrade all packages to latest in one commit, move both webapp CI builds to Node 22 LTS, remove the unused FontAwesome packages, keep `hls.js` exactly pinned at 1.7.4.

| Pros | Cons |
|------|------|
| Meets the goal in one step: `npm outdated` empty | Largest single change; one commit makes bisecting harder |
| Toolchain on supported majors; most audit findings disappear | Vite 8 switches bundler (Rolldown), which can change chunking and plugin behaviour |
| Node 22 LTS satisfies Vite 8 with margin and matches the evolve workflow | `eslint-plugin-react-hooks` 7 adds rules that may flag existing components |
| Removing dead FontAwesome deps shrinks install and audit surface | Vitest 5 / jsdom 29 may need test-setup changes |

### Option 4: Everything to latest, keep Node 20

As Option 3 but pin CI to Node 20.19+.

| Pros | Cons |
|------|------|
| No CI runtime change | Sits on the Node floor of Vite 8; the next toolchain release can drop it |
| | Node 20 leaves LTS maintenance sooner than 22 |

---

## Decision Outcome

**Chosen Option:** Option 3 — everything to latest, Vite 8 on Node 22.

**Rationale:** Only Option 3 meets the PRD goal of zero outdated packages without planning a second migration. The added risk is in the toolchain, not in application code, and the existing test suite plus a prod smoke pass cover the behaviour that matters (playback, PWA banner, i18n). Node 22 is chosen over staying on 20 because it gives Vite 8 headroom and is the current LTS; it is also what `agent-evolve.yml` already uses. The single-commit decision is the owner's; its bisect cost is mitigated by verifying each package group locally before squashing.

**Sub-decisions that are part of this choice:**

- `hls.js` keeps an exact pin, moving from `1.7.3` to `1.7.4` (no `^`), preserving ADR-0031's intent.
- The four unused `@fortawesome/*` packages are removed rather than upgraded. This is dead-dependency removal, not a library replacement.
- `deploy-web.yml` and `autopilot.yml` both move to Node 22 in the same commit, so CI and autopilot never build the lockfile on different runtimes.
- New lint rules from `eslint-plugin-react-hooks` 7 are fixed where cheap. If they would require refactoring components (out of scope), they are enabled at `warn` in `eslint.config.js` with a `TASKS.md` follow-up, never disabled per line.

### Quantified Impact

| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| Outdated packages | 27 | 0 | `npm outdated` |
| Declared dependencies | — | −4 | FontAwesome removed |
| `npm audit` high + critical | 12 | Expected lower | Measured after the upgrade and reported in the commit body |
| Main JS chunk (gzip) | Current build | Within +5% | PRD NFR-1 guard rail |
| CI Node | 20 | 22 | `deploy-web.yml`, `autopilot.yml` |

---

## Consequences

**Positive:**
- Frontend toolchain on current majors; future upgrades are small, routine bumps
- Most known audit findings in build and test dependencies are removed
- One documented Node version for every webapp build path
- Smaller dependency tree after removing FontAwesome

**Negative:**
- One large commit: a regression found later is harder to attribute to a package
- Config and test-setup changes for Vite 8, Vitest 5 and ESLint 10 add review effort
- Possible `warn`-level lint debt if the new react-hooks rules are not fixed in this change

**Risks:**
- Rolldown changes chunk output or plugin hook timing: verify `dist/` contains `version.json` and a separate `hls.js` chunk, and that the PWA new-version banner test passes
- Tests break on Vitest 5 / jsdom 29 semantics: follow the migration guides, never skip or delete a test to get green
- Node 22 in CI differs from local Node 24: both exceed the Vite 8 floor; reproduce locally with Node 22 if CI fails

---

## Validation

- [ ] `cd webapp && npm outdated` is empty
- [ ] `npm run lint && npx vitest run --root . && npm run build` pass; all 76 test files run, none skipped
- [ ] `dist/` contains `version.json` and a separate lazy `hls.js` chunk; main chunk gzip within +5%
- [ ] "Deploy Frontend (React Build)" green on the upgrade commit
- [ ] Prod smoke: login, feed, HLS playback and Range fallback, admin pages, language switch, PWA update banner
- [ ] Tech Strategy updated: Vite 8, hls.js 1.7.4 (plus the `docs/claude/rules/` mirror)
- [ ] Related plan document created: `docs/plans/plan-frontend-deps-upgrade.md`

---

## Links

- [Related PRD](../prd/PRD-frontend-deps-upgrade.md)
- [Implementation Plan](../plans/plan-frontend-deps-upgrade.md) (pending)
- [ADR-0031: HLS streaming](./0031-hls-streaming.md) — exact `hls.js` pin
- [ADR-0033: Spring Boot 4.1 upgrade](./0033-spring-boot-4-upgrade.md) — backend counterpart

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-10-29 | nguyenhuuca / Claude Code | Initial draft (Proposed) |
| 2026-10-29 | nguyenhuuca | Accepted |
