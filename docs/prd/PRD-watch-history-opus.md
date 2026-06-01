# PRD: Watch History

<!-- Model-comparison artifact — generated with Claude Opus 4.8. Sibling: PRD-watch-history.md. Do not merge the two. -->

## Overview

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-06-01
**Version:** 1.0
**Beads Issue:** N/A
**PR-FAQ:** N/A
**Stakeholders:** Product, Engineering, Design

> **Note:** This PRD was generated with Claude Opus 4.8 as a model-comparison exercise against the existing `PRD-watch-history.md`. Both describe the same feature; keep them separate.

---

## Problem Statement

A logged-in user watches a video, leaves the app, and later wants to return to it — but the app keeps no record of what they watched. Their only recovery path is to re-search by half-remembered title, which frequently fails. The watch intent simply evaporates at session end.

The product already anticipates this: the frontend ships a `/history` nav item that today routes to a generic `ComingSoon` placeholder (`webapp/src/components/layout/ComingSoon.jsx`, wired in `AppShell.jsx`). Users who click it get nothing. We are advertising a capability we don't deliver.

### Evidence

**Quantitative Evidence:**
- **100% of watch intent is non-recoverable today.** There is no persistence of play events per user — once a session ends, the watched set is gone.
- **A dead nav entry already exists.** `/history` is presented in the app shell and resolves to a placeholder, so every click on it is a measurable dead-end (instrumentable as bounce on `/history`).

**Qualitative Evidence:**
- Observed loop: *play a video → navigate away → come back later → can't find it → give up.* No tooling exists to short-circuit this.
- Baseline expectation: every major video platform (YouTube, Netflix, Vimeo) treats watch history as table stakes. Its absence reads as "unfinished" rather than "minimal."

---

## Goals & Success Metrics

| Goal | Metric | Target |
|------|--------|--------|
| Help users rediscover content they've watched | Re-watch rate from the `/history` page (plays originating from a history entry ÷ total history entries) | **≥ 20%** of history entries are replayed within 30 days |
| (Secondary) Drive feature adoption | % of active logged-in users with ≥ 1 history entry | ≥ 30% within 30 days of launch |
| (Secondary) Reduce re-search friction | Repeat-search rate for the same query, users with vs. without history | Measurable decrease for users with history |

> **Primary metric:** re-watch rate from `/history`. It directly validates the core hypothesis — that a persistent history helps users return to content. Adoption and re-search are supporting signals, not pass/fail gates for v1.

---

## User Stories

### Authenticated User

- As a logged-in user, I want videos I play to be recorded automatically, so that I never have to remember to "save" something to find it later.
  - Acceptance: Playing a video creates a history entry for the current user with zero manual action. Playing it again updates the existing entry's timestamp rather than creating a duplicate.

- As a logged-in user, I want a `/history` page that lists everything I've watched, newest first, so that the thing I watched most recently is the easiest to find.
  - Acceptance: The page lists entries with thumbnail, title, and a relative watch timestamp ("2 hours ago"), ordered by `watched_at` descending. Clicking an entry plays the video. An empty history shows a friendly empty state, not a blank page.

- As a logged-in user, I want video cards to show a "watched" badge for videos I've already seen, so that I can tell new from seen content at a glance while browsing.
  - Acceptance: Cards for videos in my history render a visible "watched" indicator; cards for unwatched videos render none. The indicator requires no per-card API call.

- As a logged-in user, I want to delete a single history entry, so that I can remove something I'd rather not keep a record of.
  - Acceptance: Each entry has a delete action; confirming it removes only that entry and refreshes the list and badges.

- As a logged-in user, I want to clear my entire history in one action, so that I can reset my record for privacy.
  - Acceptance: A "Clear all" action, gated behind an explicit confirmation, removes every entry for my account and resets all watched badges.

---

## Requirements

### Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | Record a history entry when a logged-in user plays a video | Must Have | Triggered on the play event from the frontend |
| FR-2 | Recording is an **upsert** keyed by `(user_id, source_video_id)` — re-watch updates `watched_at`, never duplicates | Must Have | A re-watched video moves to the top of the list |
| FR-3 | `GET /watch-history` returns the current user's entries sorted by `watched_at` DESC, paginated | Must Have | Wrapped in `ResultListInfo<T>` |
| FR-4 | `GET /watch-history/ids` returns the set of watched video IDs for the current user | Must Have | Powers client-side card badges with one fetch |
| FR-5 | Video cards render a "watched" indicator for IDs present in the user's history | Must Have | Client-side `Set` lookup; no per-card request |
| FR-6 | Delete a single history entry | Must Have | Owner-scoped |
| FR-7 | Clear all history for the current user | Must Have | Requires explicit confirmation in the UI |
| FR-8 | History is strictly private — a user can only read or mutate their own entries | Must Have | Enforced server-side from the JWT subject, never a client-supplied user id |
| FR-9 | Cap history at 500 entries per user; on insert beyond the cap, auto-evict the oldest by `watched_at` | Must Have | Bounds storage growth (see Risks) |
| FR-10 | Replace the `/history` `ComingSoon` placeholder with a real `HistoryPage` | Must Have | `webapp/src/components/layout/AppShell.jsx` |

### Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Recording must not slow down or block playback | Fire-and-forget POST from the frontend; playback never awaits the call |
| NFR-2 | `/history` list query latency | < 500 ms p99 for up to 500 entries |
| NFR-3 | Watched badges add no per-card network cost | 0 extra API calls after the single `ids` fetch on page load |
| NFR-4 | Authorization | All endpoints require a valid JWT; ownership derived from the token, not request params |
| NFR-5 | Eviction correctness | Cap enforcement is atomic with insert so a user never exceeds 500 entries under concurrent plays |

---

## Scope

### In Scope

- Auto-record a history entry on video play, as an upsert (no duplicates; re-watch bumps to top)
- `/history` page listing watched videos newest-first (thumbnail, title, relative timestamp), with an empty state
- "Watched" indicator badge on video cards, driven by a single client-side ID set
- Delete a single history entry
- Clear all history, behind a confirmation
- 500-entry-per-user cap with auto-eviction of the oldest entry
- New `watch_history` table via a Liquibase migration in `api/src/main/resources/db/changelog/sql/`
- Backend REST API: `WatchHistoryController` under the app's `BASE_URL` (`/watch-history`)
- Frontend: replace the `ComingSoon` placeholder with a real `HistoryPage` component

### Out of Scope (deferred to v2+)

- **Resume playback** — remembering the timestamp where the user stopped and resuming there
- **History-based recommendations** — suggesting videos from viewing patterns
- **History for guest (unauthenticated) users** — v1 is logged-in only
- **Public / shareable history** and **watch-time / duration analytics**

---

## Dependencies

| Dependency | Owner | Status | Risk |
|------------|-------|--------|------|
| `VideoSource` entity / `video_sources` table | Existing | Stable | Low |
| `User` entity / `users` table | Existing | Stable | Low |
| Frontend `/history` nav item (currently `ComingSoon`) | Existing | Ready to replace | Low |
| JWT authentication (token subject = user identity) | Existing | Stable | Low |
| `ResultListInfo<T>` / `ResultObjectInfo<T>` response wrappers | Existing | Stable | Low |
| Liquibase migration for `watch_history` | Engineering | To Do | Low |

---

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Recording the play event slows playback | L | H | Fire-and-forget POST from the frontend; UI never awaits the response, failures are swallowed silently |
| A `VideoSource` is deleted while still referenced by history | M | M | `ON DELETE SET NULL` on the FK to `video_sources`; keep an immutable `source_video_id` for the unique key; UI renders "Video unavailable" for null references |
| Unbounded history growth bloats the table | L | M | FR-9 cap of 500 entries/user with atomic auto-eviction of the oldest on insert |
| Watched badges go stale across tabs/sessions | L | L | Invalidate the React Query `history-ids` cache on any play, delete, or clear |
| Forged ownership (reading another user's history) | L | H | Derive ownership from the JWT subject server-side; never trust a client-supplied user id (FR-8) |
| Concurrent plays exceed the cap momentarily | L | L | Enforce cap inside the same transaction as the upsert (NFR-5) |

---

## Open Questions

- [ ] Should there be a **minimum watch duration** (e.g., ≥ 5 seconds) before an entry is recorded, to avoid logging accidental clicks? *(Leaning: yes, a small threshold — confirm with Product.)*
- [ ] Should the watched badge distinguish **"fully watched" vs. "started"**, or is binary "seen" sufficient for v1? *(v1 assumption: binary.)*
- [ ] When the cap evicts an entry the user can still see on screen (open `/history` tab), do we surface that, or let the next refresh reconcile silently? *(Leaning: silent reconcile.)*

---

## Appendix

### Data Model (Sketch)

```
watch_history
  id               BIGINT       PRIMARY KEY (IDENTITY)
  user_id          BIGINT       NOT NULL  REFERENCES users(id)         ON DELETE CASCADE
  video_id         BIGINT                 REFERENCES video_sources(id) ON DELETE SET NULL
  source_video_id  BIGINT       NOT NULL  -- immutable; survives video deletion, used for uniqueness
  watched_at       TIMESTAMPTZ  NOT NULL  DEFAULT now()
  UNIQUE (user_id, source_video_id)
  INDEX (user_id, watched_at DESC)        -- supports newest-first list + eviction
```

- `source_video_id` is kept immutable so the unique constraint and badge lookups survive a `VideoSource` deletion (where `video_id` becomes NULL).
- The `(user_id, watched_at DESC)` index serves both the list query (FR-3) and oldest-entry eviction (FR-9).

### API Endpoints (Sketch)

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `GET` | `/watch-history` | Current user's history, newest first, paginated | `ResultListInfo<HistoryEntryDto>` |
| `GET` | `/watch-history/ids` | Watched video IDs for the current user (badges) | `ResultListInfo<Long>` |
| `POST` | `/watch-history` | Record/update a watch event `{ videoId }` (upsert) | `ResultObjectInfo<HistoryEntryDto>` |
| `DELETE` | `/watch-history?videoId=X` | Remove a single entry | `ResultObjectInfo<Void>` |
| `DELETE` | `/watch-history/all` | Clear the user's entire history | `ResultObjectInfo<Void>` |

All endpoints resolve the owning user from the JWT subject; no endpoint accepts a user id from the client.

### Mockups/Wireframes

N/A — to be produced by `/ui-ux-designer` (replaces the existing `ComingSoon` view for `/history`).

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product | nguyenhuuca | 2026-06-01 | Pending |
| Engineering | | | Pending |
| Design | | | Pending |

---

## Next Steps & Handoffs

After PRD approval:

1. [ ] **Architect Review** — technical feasibility & design
   - Trigger: `/architect`
   - Output: ADR (`docs/adr/00NN-watch-history-design.md`)

2. [ ] **Spec** — feature specification from PRD + ADR
   - Trigger: `/spec`
   - Output: `docs/specs/spec-watch-history-opus.md`

3. [ ] **UI/UX Designer** — `/history` page + watched badge
   - Trigger: `/ui-ux-designer`
   - Output: Design spec

4. [ ] **Implementation Plan** — decomposition into trackable work
   - Trigger: `/swarm-plan`
   - Output: `docs/plans/plan-watch-history-opus.md`

**Related Artifacts:**
- Sibling PRD (comparison baseline): `docs/prd/PRD-watch-history.md`
- ADR: [Link after architect review]
- Design Spec: [Link after designer review]
- Implementation Plan: [Link after decomposition]

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-06-01 | nguyenhuuca | Initial draft (generated with Claude Opus 4.8 for model comparison) |
