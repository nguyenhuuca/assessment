# ADR-0006: Migrate Frontend to React with Vite

## Status
Proposed

## Date
2026-03-01

## Context

ADR-0004 (Use Vanilla JS + Bootstrap for Current Frontend) explicitly planned migration to React for:
- Better component reuse and state management
- Richer interaction (comment threads, optimistic UI)
- Easier integration with design systems and CI tooling

The prototype phase is complete (version 14, production deployed). The codebase has grown to ~4,400 lines across 6 files with increasing complexity around:
- Auth flows (passwordless + MFA + magic link)
- YouTube Shorts-style video navigation (touch, keyboard, swipe)
- Comment panel with real-time state updates
- Vote state management per video
- Multi-modal UI (5 Bootstrap modals)

jQuery-based DOM manipulation has become hard to reason about as interactions grow. It's the right time to execute the planned migration.

## Decision

Migrate the frontend (`webapp/`) from Vanilla JS + jQuery + Bootstrap CDN to:

| Component | Decision |
|-----------|----------|
| Build tool | **Vite** (replaces no-build-step) |
| Framework | **React 19** |
| Language | **JavaScript ES6+** (no TypeScript in this phase) |
| HTTP | **Fetch API** (replace jQuery AJAX) |
| Server state | **TanStack Query v5** (React Query) |
| Client state | **React Context** (auth + theme) |
| Routing | **React Router v6** |
| UI library | **react-bootstrap** (Bootstrap 5 wrappers) |
| Gestures | **react-swipeable** |
| Testing | **Vitest + React Testing Library** |

### Why Vite over Create React App (CRA)?
- CRA is deprecated as of 2023
- Vite has native ESM, faster HMR, smaller output
- No ejecting required for config customization

### Why React Context over Redux/Zustand?
- Only 2 client-state concerns: auth (JWT/user) and theme
- React Query handles all server state (loading, caching, errors)
- Adding Redux for 2 global values would be over-engineering

### Why react-bootstrap over importing Bootstrap CSS directly?
- Avoids Bootstrap JS (conflicts with React's synthetic events)
- Native React component API (no `data-bs-toggle` attributes)
- Modal lifecycle managed by React, not jQuery

### Why JavaScript instead of TypeScript?
- Reduces migration scope (pure behavior migration, not type migration)
- Team can adopt TypeScript in a follow-up ADR once React patterns are established
- Vite supports TypeScript migration incrementally later

## Consequences

### Positive
- Component-based architecture enables isolated testing
- React Query eliminates manual loading/error state boilerplate
- Vite HMR speeds up local development
- Removes jQuery dependency (saves ~87KB gzipped)
- CI/CD now includes a build step with artifact verification

### Negative
- **Breaking change** to deployment pipeline (must add `npm run build` before SCP)
- `webapp/` directory structure completely changes (no coexistence with old code)
- Nginx needs `try_files $uri /index.html` for SPA routing
- Initial bundle size slightly larger than zero-JS-framework approach (~40KB React core gzipped)
- Node.js required in CI environment for build step

### Neutral
- PWA manifest (`manifest.json`) carries forward unchanged
- CSS custom property theme system (`assessment.css`) carries forward with minor adaptation
- All 14 backend API endpoints remain unchanged
- Authentication flows (passwordless, MFA, magic link) behavior unchanged

## Supersedes
- ADR-0004 (frontend-structure) — this ADR executes the planned migration referenced in that decision

## Related
- PRD: `artifacts/prd_react-migration.md`
- Plan: `artifacts/plan_react-migration.md`
- Tech Strategy: `doc/tech-strategy.md` — update JavaScript section after this ADR is accepted
