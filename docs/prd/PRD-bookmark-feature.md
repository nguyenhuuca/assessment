# PRD: Bookmark Feature

<!--
Product Requirements Document
Filename: docs/prd/PRD-bookmark-feature.md
Owner: nguyenhuuca
Handoff to: Architect (/architect), UI/UX Designer (/ui-ux-designer)
Related Skills: writing-prds, decomposing-tasks, requirements-analysis
-->

## Overview

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-05-31
**Version:** 1.0
**Beads Issue:** N/A
**PR-FAQ:** N/A
**Stakeholders:** Product, Engineering, Design

---

## Problem Statement

Logged-in users who discover interesting videos during browsing have no way to save them for later. When they return to the app, they cannot find the videos again because there is no persistent watch-list — they must re-search, often unsuccessfully, and eventually churn.

### Evidence

**Quantitative Evidence:**
- No native save mechanism exists in the current UI — 100% of "watch later" intent is lost at session end
- Re-search friction increases with catalog size; the app indexes YouTube content with growing volume

**Qualitative Evidence:**
- User behaviour pattern: browse → find video → navigate away → forget → leave app
- Common user expectation: every video platform (YouTube, Netflix, Twitch) provides a save/bookmark primitive; its absence is felt as a gap

---

## Goals & Success Metrics

| Goal | Metric | Target |
|------|--------|--------|
| Drive return visits through saved content | Bookmark-to-watch rate | ≥ 40% of bookmarked videos are eventually played |
| Feature adoption | % of active users with ≥ 1 bookmark | ≥ 20% within 30 days of launch |
| Retention signal | Session frequency for bookmarking users vs. non-bookmarking users | Bookmarking users return ≥ 1.5× more often |

---

## User Stories

### Authenticated User

- As a logged-in user, I want to bookmark a video from the video card or player page so that I can find it again later without searching.
  - Acceptance: A bookmark toggle button is visible on every video card and on the player page. Clicking it saves the video; clicking again removes it. State persists across sessions.

- As a logged-in user, I want to see a visual indicator on video cards I have already bookmarked so that I know at a glance what I saved.
  - Acceptance: Bookmarked videos display a filled/highlighted bookmark icon. Non-bookmarked videos show an outline icon.

- As a logged-in user, I want a dedicated `/bookmarks` page that lists all my saved videos so that I can browse and play them in one place.
  - Acceptance: Page shows all bookmarked videos in a grid, sorted by bookmark date (newest first). Each card supports remove-bookmark and play actions.

- As a logged-in user, I want to organise my bookmarks into named folders/collections so that I can group content by theme (e.g. "Comedy", "Weekend Watch").
  - Acceptance: I can create, rename, and delete collections. I can add a bookmarked video to one or more collections. A default "All Bookmarks" view always exists.

---

## Requirements

### Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | Authenticated users can add and remove bookmarks on any video | Must Have | Toggle action; idempotent |
| FR-2 | Bookmark state is persisted server-side, scoped to the authenticated user | Must Have | Stored in PostgreSQL |
| FR-3 | `/bookmarks` route shows all bookmarks for the logged-in user, newest first | Must Have | Uses `ResultListInfo<T>` wrapper |
| FR-4 | Video cards display a bookmark indicator reflecting current bookmark state | Must Have | Loaded via React Query |
| FR-5 | Users can create named collections (folders) to organise bookmarks | Must Have | v1 includes full CRUD for collections |
| FR-6 | Users can add a bookmark to one or more collections | Must Have | Many-to-many: bookmark ↔ collection |
| FR-7 | Users can remove a bookmark from a collection without deleting the bookmark | Should Have | Unlinks from collection, bookmark persists in "All Bookmarks" |
| FR-8 | Users can delete a collection (bookmarks are not deleted, just unlinked) | Should Have | |
| FR-9 | Bookmark count per video visible to the bookmarking user only | Nice to Have | Not a social/public counter |

### Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Bookmark toggle API response time | < 200 ms p99 |
| NFR-2 | Bookmarks page load (list query) | < 500 ms p99 for up to 500 bookmarks |
| NFR-3 | Security | All bookmark endpoints require valid JWT; users can only read/write their own bookmarks |
| NFR-4 | Data integrity | Deleting a `VideoSource` record does not silently orphan bookmarks — handle via FK constraint or soft-delete |
| NFR-5 | Scalability | Single-user bookmark cap of 500 to prevent unbounded growth (enforced at API layer) |

---

## Scope

### In Scope

- Add / remove bookmark toggle on video cards and player page
- Visual bookmark indicator (filled vs. outline icon) on video cards
- Dedicated `/bookmarks` page listing all saved videos, sorted by bookmark date
- Named collections (folders): create, rename, delete
- Add / remove a video from a collection
- "All Bookmarks" default view (always present, cannot be deleted)
- New `bookmarks` and `bookmark_collections` DB tables via Liquibase migration
- Backend REST API: `BookmarkController` under `BASE_URL/bookmarks`
- Frontend: new `bookmarks.js` API module + `/bookmarks` route in React Router

### Out of Scope

- Public or shareable bookmark lists
- Collaborative / multi-user collections
- Notifications for bookmarked videos (e.g., "trending now")
- Bulk import / export of bookmarks
- Social bookmark counts visible to other users

> **Assumption:** "No v2 planned" means this feature ships as a self-contained, complete experience. No future extension points are required beyond clean API design.

---

## Dependencies

| Dependency | Owner | Status | Risk |
|------------|-------|--------|------|
| `VideoSource` entity (`video_sources` table) | Existing | Stable | Low — FK target for bookmarks |
| `User` entity (`users` table) | Existing | Stable | Low — FK target for bookmarks |
| Liquibase migration for new tables | Engineering | To Do | Low — standard pattern |
| JWT authentication (Spring Security) | Existing | Stable | Low — reuse existing filter chain |
| React Query + React Router v7 | Existing | Stable | Low — add new route and query |

---

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| VideoSource deleted while bookmarked | M | M | Add `ON DELETE SET NULL` or soft-delete VideoSource; handle null in UI gracefully |
| Users create thousands of bookmarks (no cap) | L | M | Enforce 500-bookmark cap at API layer with a `409 Conflict` response |
| N+1 query on bookmarks page (fetching collections per bookmark) | M | M | Use JOIN fetch or batch loading in repository; test with 100+ bookmarks |
| Bookmark state stale in UI after toggle | L | L | React Query `invalidateQueries` on mutation ensures fresh state |

---

## Open Questions

- [ ] Should the bookmark icon appear on the video player page header or as an overlay button on the video?
- [ ] Is there a maximum number of collections per user? (Suggest: 50)
- [ ] Should bookmarks survive account deletion, or cascade-delete with the user?

---

## Appendix

### Data Model (Sketch)

```
bookmarks
  id            BIGSERIAL PRIMARY KEY
  user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE
  video_id      BIGINT NOT NULL REFERENCES video_sources(id) ON DELETE SET NULL
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
  UNIQUE (user_id, video_id)

bookmark_collections
  id            BIGSERIAL PRIMARY KEY
  user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE
  name          VARCHAR(100) NOT NULL
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()

bookmark_collection_items
  collection_id BIGINT NOT NULL REFERENCES bookmark_collections(id) ON DELETE CASCADE
  bookmark_id   BIGINT NOT NULL REFERENCES bookmarks(id) ON DELETE CASCADE
  PRIMARY KEY (collection_id, bookmark_id)
```

> **Assumption:** Integer (`BIGSERIAL`) primary keys are used to match the existing `users` and `video_sources` schema, rather than UUIDs used in newer migrations.

### API Endpoints (Sketch)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/bookmarks` | List all bookmarks for current user |
| `POST` | `/api/v1/bookmarks` | Add a bookmark (`{ videoId }`) |
| `DELETE` | `/api/v1/bookmarks/{id}` | Remove a bookmark |
| `GET` | `/api/v1/bookmarks/collections` | List all collections |
| `POST` | `/api/v1/bookmarks/collections` | Create a collection |
| `PUT` | `/api/v1/bookmarks/collections/{id}` | Rename a collection |
| `DELETE` | `/api/v1/bookmarks/collections/{id}` | Delete a collection |
| `POST` | `/api/v1/bookmarks/collections/{id}/items` | Add bookmark to collection |
| `DELETE` | `/api/v1/bookmarks/collections/{id}/items/{bookmarkId}` | Remove bookmark from collection |

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product | nguyenhuuca | 2026-05-31 | Pending |
| Engineering | | | Pending |
| Design | | | Pending |

---

## Next Steps & Handoffs

After PRD approval:

1. [ ] **Architect Review**: Technical feasibility — schema, API design, caching strategy
   - Trigger: `/architect`
   - Output: `docs/adr/00NN-bookmark-storage.md`

2. [ ] **UI/UX Designer**: Bookmark toggle, indicator design, collections UI
   - Trigger: `/ui-ux-designer`
   - Output: Design Spec

3. [ ] **Engineering Estimate**: Effort estimation and task decomposition
   - Trigger: `/builder` or `/architect`
   - Output: `docs/plans/plan-bookmark-feature.md`

**Related Artifacts:**
- ADR: TBD after architect review
- Design Spec: TBD after designer review
- Implementation Plan: TBD after decomposition

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-31 | nguyenhuuca | Initial draft |
