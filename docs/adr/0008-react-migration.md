# ADR-0008: Migrate Frontend to React with Vite

## Metadata

**Status:** Proposed · **Date:** 2026-03-01 · **Deciders:** nguyenhuuca · **Tags:** frontend  
**Related PRD:** [PRD-react-migration](../prd/PRD-react-migration.md) · **Supersedes:** ADR-0004 · **Superseded By:** N/A

**Tech Strategy:** ✅ Follows Golden Path

---

## Context

ADR-0004 explicitly planned a migration to React once the prototype phase was complete. That phase is now done (version 14, production deployed). The frontend codebase has grown to approximately 4,400 lines across 6 files. Complexity has increased significantly around auth flows (passwordless, MFA, magic link), YouTube Shorts-style video navigation (touch, keyboard, swipe), comment panel state, per-video vote state, and five Bootstrap modals.

jQuery-based DOM manipulation has become difficult to reason about as interaction complexity grows. The conditions described in ADR-0004 for triggering the migration have been met.

---

## Decision Drivers

- jQuery DOM manipulation does not scale well with the current interaction complexity
- Component isolation is needed to enable reliable unit testing of UI logic
- Server state management (loading, caching, error handling) requires a dedicated solution
- The build pipeline should produce a verifiable artifact before deployment
- Migration scope must be bounded to avoid introducing type migration risk alongside behavior migration

---

## Considered Options

### Option 1: Keep Vanilla JS + jQuery
Continue extending the existing jQuery-based codebase.

| Pros | Cons |
|------|------|
| No migration cost | DOM manipulation complexity continues to grow |
| No pipeline changes required | Isolated unit testing of UI logic remains impractical |
| | Server state boilerplate must be maintained manually |

### Option 2: React 19 + Vite (chosen)
Migrate `webapp/` to React 19 with Vite as the build tool, replacing jQuery and the no-build-step approach.

| Pros | Cons |
|------|------|
| Component-based architecture enables isolated testing | Breaking change to deployment pipeline |
| React Query eliminates loading/error state boilerplate | `webapp/` directory structure completely changes |
| Vite native ESM provides faster HMR than CRA | Nginx requires `try_files` reconfiguration for SPA routing |
| Removes jQuery (-87KB gzipped) | Bundle size increases by ~40KB for React core (gzipped) |
| CI build step produces verifiable artifact | Node.js required in CI environment |

### Option 3: Vue.js + Vite
Adopt Vue.js instead of React as the component framework.

| Pros | Cons |
|------|------|
| Gentler learning curve, single-file components | Not aligned with team's existing React knowledge trajectory |
| Vite native support | Smaller ecosystem for the specific libraries already chosen (React Query, react-bootstrap) |

### Option 4: Next.js
Adopt Next.js for server-side rendering capability.

| Pros | Cons |
|------|------|
| SSR and SSG support for improved initial load | Over-engineered for a SPA with an existing Spring Boot API |
| File-based routing | Adds server infrastructure complexity without clear benefit |

---

## Decision Outcome
**Chosen Option:** Option 2 — React 19 + Vite
**Rationale:** React with Vite is the most direct path to solving the core problems: testable component isolation, managed server state via TanStack Query, and a reproducible build artifact. CRA was excluded because it was deprecated in 2023; Vite's native ESM and faster HMR make it the correct replacement. Vue.js and Next.js were excluded because they do not align with the team's existing direction and add migration or infrastructure complexity without proportional benefit. React Context covers the only two client-state concerns (auth and theme); Redux would be over-engineering at this scope. JavaScript ES6+ is retained over TypeScript to keep the migration bounded to behavior only; TypeScript can be adopted incrementally in a follow-up ADR.

### Quantified Impact *(where applicable)*
| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| jQuery bundle | ~87KB gzipped | 0KB | Dependency removed entirely |
| React core bundle | 0KB | ~40KB gzipped | Net reduction of ~47KB |
| CI pipeline | No build step | `npm run build` added | Verifiable artifact before SCP |

---

## Consequences
**Positive:**
- Component-based architecture enables isolated unit testing of UI logic
- TanStack Query v5 eliminates manual loading, caching, and error state boilerplate
- Vite HMR accelerates local development
- jQuery dependency removed entirely
- CI/CD pipeline now includes a build step with artifact verification

**Negative:**
- Deployment pipeline requires a breaking change: `npm run build` must run before SCP
- `webapp/` directory structure changes completely with no coexistence path for old code
- Nginx must be reconfigured with `try_files $uri /index.html` for SPA routing
- Node.js must be available in the CI environment

**Risks:**
- PWA manifest, CSS theme system, and all 14 backend API endpoints are intentionally unchanged; any drift from this scope increases migration risk
- TypeScript adoption deferred; if React patterns are not established before a follow-up TypeScript ADR, incremental migration may be inconsistent

---

## Validation
- [x] Tech Strategy alignment confirmed
- [ ] Related plan document created: docs/prd/PRD-react-migration.md

---

## Links
- ADR-0004: frontend-structure (superseded by this decision)
- PRD: `docs/prd/PRD-react-migration.md`

---

## Changelog
| Date | Author | Change |
|------|--------|--------|
| 2026-03-01 | nguyenhuuca | Initial draft |
| 2026-05-31 | nguyenhuuca | Restructured to new ADR template |
