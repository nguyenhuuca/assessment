# ADR-0004: Use Vanilla JS + Bootstrap for Initial Frontend with Planned Migration to React

## Metadata

**Status:** Accepted · **Date:** 2025-07-03 · **Deciders:** nguyenhuuca · **Tags:** frontend  
**Related PRD:** N/A · **Supersedes:** N/A · **Superseded By:** ADR-0008

**Tech Strategy:** ✅ Follows Golden Path

---

## Context
The frontend must display video content, expose interactions such as comments and likes, and provide a responsive UI across mobile and desktop. At the early stage of the project, the priority was to ship a working UI quickly with minimal toolchain overhead, while deliberately deferring the adoption of a component framework until the product's interaction model stabilized.

---

## Decision Drivers
- Fastest path to a working UI with no build toolchain required
- Team can prototype and iterate quickly without framework overhead
- Product interaction requirements were not yet stable enough to justify a full SPA framework
- Future migration path to a richer framework must remain open
- Responsive design is required from day one

---

## Considered Options

### Option 1: Vanilla JS + Bootstrap
Plain JavaScript for DOM manipulation and logic, Bootstrap for responsive CSS. No build step required.

| Pros | Cons |
|------|------|
| No build toolchain needed — open in browser directly | No component reuse; logic duplicates across pages |
| Fast to prototype new UI features | State management becomes complex as interactions grow |
| Bootstrap provides responsive grid and components out of the box | Migration to a framework later requires a full rewrite |
| Zero framework learning curve | No module bundling or tree-shaking |

### Option 2: React (immediate adoption)
A component-based SPA framework with a full build pipeline (Vite), state management, and rich ecosystem.

| Pros | Cons |
|------|------|
| Component reuse and composability from day one | Build toolchain setup required upfront |
| Ecosystem for state management, routing, and testing | Higher initial complexity and onboarding cost |
| Easier to integrate design systems | More overhead than needed for a simple early-stage UI |
| Long-term maintainability advantage | |

### Option 3: Vue.js
A progressive JavaScript framework with a gentler learning curve than React.

| Pros | Cons |
|------|------|
| Easier to learn than React for teams new to SPA frameworks | Smaller ecosystem than React |
| Progressive adoption possible (script tag or SPA) | Less alignment with team's existing JavaScript knowledge |
| Good documentation and CLI tooling | Not the team's intended long-term direction |

### Option 4: Angular
A full-featured opinionated SPA framework from Google.

| Pros | Cons |
|------|------|
| Complete solution: routing, forms, HTTP client built-in | Highest learning curve of all options |
| Strong typing via TypeScript by default | Heavyweight for a video streaming side project |
| Enterprise-grade architecture | Slow to prototype new features |
| | Over-engineered for current scale |

---

## Decision Outcome
**Chosen Option:** Option 1 — Vanilla JS + Bootstrap (interim)
**Rationale:** The early-stage priority was speed of delivery. Vanilla JS with Bootstrap required no toolchain and enabled immediate prototyping. The decision explicitly deferred React adoption until product requirements stabilized, with the migration planned for Q4 2025 and formalized in ADR-0008. Vue and Angular were excluded due to ecosystem fit and team direction.

### Quantified Impact *(where applicable)*
| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| Time to first working UI | Days (with framework setup) | Hours (no build step) | Prototype speed was the primary driver |
| Build toolchain complexity | N/A | None | No bundler, transpiler, or package manager needed |

---

## Consequences
**Positive:**
- Working UI delivered quickly with no toolchain overhead
- Bootstrap provides responsive design without custom CSS effort
- No framework lock-in during the unstable early product phase

**Negative:**
- Code reuse across pages is limited; logic must be duplicated manually
- State management complexity increases as interactions grow
- Full rewrite required when migrating to React (see ADR-0008)

**Risks:**
- Delaying the migration too long increases the cost of the eventual rewrite
- Without a module system, dependency management between JS files is fragile at scale

---

## Validation
- [ ] Tech Strategy alignment confirmed
- [ ] Related plan document created: N/A

---

## Links
- [ADR-0008: React Migration](./0008-react-migration.md)
- [Bootstrap 5 docs](https://getbootstrap.com/docs/5.0/)

---

## Changelog
| Date | Author | Change |
|------|--------|--------|
| 2025-07-03 | nguyenhuuca | Initial draft |
| 2026-05-31 | nguyenhuuca | Restructured to new ADR template |
