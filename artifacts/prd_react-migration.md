# PRD: Migrate Frontend from Vanilla JS to React

**Status:** Planning
**Date:** 2026-03-01
**Author:** Claude Code / Planning Orchestrator
**Supersedes:** ADR-0004 (frontend-structure) — migration phase

---

## Overview

Migrate the Funny Movies webapp from a vanilla JavaScript + jQuery + Bootstrap 5 SPA to a React application built with Vite. The migration is driven by ADR-0004, which explicitly planned this transition after the prototype phase.

## Current State

- **4,400 lines** of code across 6 files (`common.js`, `auth.js`, `assessment.js`, `index.html`, `assessment.css`)
- No build step — files served directly via Nginx via SCP deployment
- Dependencies: jQuery 3.5.1, Bootstrap 5.1.3, Font Awesome 5.7.0 (all CDN)
- No module system, no tests, global state via plain JS objects

## Goals

1. Replace jQuery DOM manipulation and AJAX with React declarative components + Fetch API
2. Achieve proper component encapsulation for: Auth, VideoPlayer, CommentPanel, Modals, Theme
3. Enable testability via React Testing Library + Vitest
4. Maintain feature parity with existing vanilla JS implementation
5. Keep CI/CD working (add Vite build step before SCP deploy)
6. Preserve existing backend API contract — no backend changes required

## Non-Goals

- **No SSR / Next.js** — static SPA is sufficient; Nginx serves static files
- **No TypeScript** in this migration (can be added separately later)
- **No new features** — pure migration, feature-for-feature parity
- **No backend changes** — API endpoints remain unchanged
- **No design changes** — same UI/UX, same CSS variables and theme

## User Requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-1 | Passwordless login + magic link flow unchanged | Must |
| FR-2 | MFA setup, verify, enable/disable unchanged | Must |
| FR-3 | Public video feed (Popular/Funny tabs) unchanged | Must |
| FR-4 | Private video tab (visible only when logged in) | Must |
| FR-5 | YouTube Shorts-style swipe navigation (touch + keyboard) | Must |
| FR-6 | Like/dislike voting with visual state | Must |
| FR-7 | Comment panel (slide-in, load, post, delete) | Must |
| FR-8 | Share video modal (URL, title, description, private flag) | Must |
| FR-9 | Delete video with confirmation | Must |
| FR-10 | Dark/light theme toggle with localStorage persistence | Must |
| FR-11 | HTML5 video player with progress bar, mute, play/pause | Must |
| FR-12 | Anonymous guest token support | Must |
| NFR-1 | PWA manifest maintained | Must |
| NFR-2 | Mobile-first responsive layout preserved | Must |
| NFR-3 | CI/CD deployment pipeline working (Vite build + SCP) | Must |

## API Endpoints (unchanged)

| Endpoint | Method | Used In |
|----------|--------|---------|
| `/user/join` | POST | Login/register |
| `/user/me` | GET | Session validation |
| `/user/verify-magic` | GET | Magic link |
| `/user/mfa/setup` | GET | MFA QR code |
| `/user/mfa/verify` | POST | Login MFA |
| `/user/mfa/enable` | POST | Enable MFA |
| `/user/mfa/disable` | POST | Disable MFA |
| `/video-stream/list` | GET | Public videos |
| `/private-videos` | GET | Private videos |
| `/share-links` | POST | Share video |
| `/share-links/{id}` | DELETE | Delete video |
| `/videos/{id}/comments` | GET | Load comments |
| `/videos/{id}/comments` | POST | Post comment |
| `/videos/{id}/comments/{cid}` | DELETE | Delete comment |

## Technology Choices

| Component | Choice | Reason |
|-----------|--------|--------|
| Build tool | **Vite** | Fast HMR, no CRA overhead, modern standard |
| Framework | **React 19** | Target framework per ADR-0004 |
| Language | **JavaScript (ES6+)** | Match current team skill; TS can be added later |
| HTTP client | **Fetch API** | Remove jQuery dependency; already in tech strategy |
| State (server) | **React Query (TanStack Query v5)** | Caching, loading states, refetch |
| State (client) | **React Context API** | Auth + theme state (simple enough) |
| Routing | **React Router v6** | Magic link token from URL params |
| UI components | **react-bootstrap** | Bootstrap 5 components as React wrappers |
| Swipe gestures | **react-swipeable** | Touch navigation for video swiper |
| Testing | **Vitest + React Testing Library** | Unit + integration tests |
| Icons | **Font Awesome 5** (npm) | Replace CDN with package |

## Acceptance Criteria

- [ ] All 12 API endpoints function correctly in React implementation
- [ ] Login/MFA/magic-link flows work end-to-end
- [ ] Video swipe navigation works on both mobile (touch) and desktop (keyboard)
- [ ] Dark/light theme persists across page reloads
- [ ] Private tab only shows for authenticated users
- [ ] Comment panel slides in/out with existing animation
- [ ] Vite build produces deployable `dist/` with no errors
- [ ] CI/CD pipeline (`deploy-web.yml`) deploys built React app via SCP
- [ ] Zero regressions from existing vanilla JS behavior

## Migration Constraints

- The `webapp/` directory is overwritten — no coexistence with old vanilla JS code
- `deploy-web.yml` must be updated to run `npm run build` before SCP
- `webapp/dist/` is the new deployment artifact (not `webapp/` directly)
- Backend API base URL still configured in environment/config (not hardcoded)

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Video player state complexity in React | High | High | Implement with `useRef` + custom hook early in Phase 3 |
| Touch/swipe parity on mobile | Medium | High | Test on real devices; use react-swipeable |
| Bootstrap modal lifecycle mismatch | Medium | Medium | Use react-bootstrap Modal, not vanilla Bootstrap JS |
| Nginx SPA routing (404 on refresh) | Low | Medium | Add `try_files $uri /index.html` nginx config |
| Bundle size regression | Low | Low | Vite tree-shaking; measure with `vite build --report` |
