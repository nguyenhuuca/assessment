# Plan: Library Collections

<!--
Implementation Plan
Filename: docs/plans/plan-library-collections.md
Owner: Builder (/swarm-execute)
Handoff to: Builder, QA Engineer
-->

## Overview

**Status:** Approved (2026-10-13: drop `bookmarks` table; admin public collections added)
**Author:** nguyenhuuca
**Date:** 2026-10-13
**Related PRD:** [PRD-bookmark-feature](../prd/PRD-bookmark-feature.md) (user story "organise my bookmarks into named folders/collections")
**Related ADR:** [ADR-0012](../adr/0012-bookmark-feature-design.md) — see *Amendment 2026-10-13*
**Supersedes:** [plan-bookmark-feature](plan-bookmark-feature.md) (never implemented)

## Objective

Turn the **Library** tab (today a `ComingSoon` placeholder) into:

1. **My collections**: a logged-in user creates private, named collections and adds videos to them from the feed.
2. **Public collections**: ADMIN / SUPER_ADMIN curate collections that **everyone can view**, including guests.

## Scope

### In Scope

- One migration: `video_collections` + `video_collection_items`
- BE:
  - entities and repos
  - `VideoCollectionService` (interface + impl)
  - `VideoCollectionController`
  - optional-auth public reads
- FE:
  - `api/collections.js`
  - `hooks/useCollections.js`
  - `LibraryView`, which replaces `ComingSoon page="library"`
  - `AddToCollectionModal`, opened from a new "Lưu" button in `VideoSwiper`
  - deep link `?l=<collectionId>`
- Built-in **"Xem sau"** (Watch later) collection:
  - created lazily, always private
  - replaces the planned bookmark feature

### Out of Scope

- Public collections created by normal users, and collaborative user collections
- Reordering items by drag & drop: items are sorted newest-added first
- Admins viewing other users' private collections

## Technical Approach

### Key Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Data model | Collection items reference `video_sources` directly. There is no `bookmarks` table. | One concept instead of two. A "bookmark" is an item in "Xem sau". |
| Visibility | `visibility` is `PRIVATE` or `PUBLIC`. Only users with the ADMIN permission bit (ADMIN, SUPER_ADMIN) can create a PUBLIC collection or set one to PUBLIC. A normal user who tries gets 403. | Covers the curated-content need without a separate table. |
| Who manages a public collection | Any ADMIN or SUPER_ADMIN can rename it, add or remove items, change its visibility, or delete it. All of these are audit-logged. | It is editorial content. It must not be orphaned when its creator loses the admin role. |
| Private collection access | Owner only. Admins cannot see them. A foreign or private id returns 404, not 403. | Privacy, and no id enumeration. |
| Default collection | `is_default = true` is always PRIVATE (CHECK constraint). It cannot be renamed, made public or deleted. | A predictable "save for later". |
| Video delete | `ON DELETE CASCADE` on `video_id` | Admin delete is a hard `deleteById`. |
| Hidden or unpublished video | The row stays in the DB but is excluded from item lists for everyone. `unavailableCount` is returned only to people who can manage the collection (owner, or an admin for a public one). For everyone else it is `null`. | Unhiding the video brings it back. Viewers learn nothing about hidden videos. |
| Public reads | `GET /collections/public` and `GET /collections/{id}/videos` are added to `OPTIONAL_AUTH_PATH`, with the same pattern as `/videos/*/reaction`. | Guests can browse. A bad token is treated as a guest. |
| Card state (is the video saved?) | `GET /collections/membership?videoId=X` when the modal opens. There is no global ids preload. | The feed cache is untouched. |
| Caps | 50 collections per user, 500 items per collection, name 1–60 chars. Names are unique per owner and case-insensitive. Public names are unique globally. | Bounded payloads. No two public collections with the same name. |
| Service style | Interface in `service/`, impl in `service/impl/` | Matches `VideoReactionService` and `AdminCommentService` |

### Schema

`api/src/main/resources/db/changelog/sql/202610130001-create-video-collections.sql`

```sql
CREATE TABLE video_collections (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,  -- owner / creator
    name        VARCHAR(60)  NOT NULL,
    visibility  VARCHAR(10)  NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PRIVATE', 'PUBLIC')),
    is_default  BOOLEAN      NOT NULL DEFAULT false,   -- "Xem sau"
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_video_collections_default_private CHECK (NOT is_default OR visibility = 'PRIVATE')
);
CREATE UNIQUE INDEX uq_video_collections_user_name    ON video_collections(user_id, lower(name));
CREATE UNIQUE INDEX uq_video_collections_public_name  ON video_collections(lower(name)) WHERE visibility = 'PUBLIC';
CREATE UNIQUE INDEX uq_video_collections_user_default ON video_collections(user_id) WHERE is_default;
CREATE INDEX idx_video_collections_public ON video_collections(updated_at DESC) WHERE visibility = 'PUBLIC';

CREATE TABLE video_collection_items (
    collection_id UUID        NOT NULL REFERENCES video_collections(id) ON DELETE CASCADE,
    video_id      BIGINT      NOT NULL REFERENCES video_sources(id)     ON DELETE CASCADE,
    added_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (collection_id, video_id)
);
CREATE INDEX idx_video_collection_items_video ON video_collection_items(video_id);
```

There is one known edge case: `ON DELETE CASCADE` on `user_id` deletes a public collection when its creator account is deleted. The plan accepts this, and the delete-account copy does not mention it. Revisit if editorial loss becomes a problem.

The master changelog uses `includeAll: sql/`, so no master edit is needed.

### API (`/v1/funny-app/collections`)

| Method | Path | Auth | Body / Query | Returns |
|--------|------|------|--------------|---------|
| GET | `/collections` | JWT | — | `ResultListInfo<CollectionDto>`: my collections. Creates "Xem sau" if it is missing. Admins also get the public collections, flagged `canManage`. |
| GET | `/collections/public` | optional | — | `ResultListInfo<CollectionDto>`: public collections with `itemCount > 0` (admins also see empty ones) |
| POST | `/collections` | JWT | `{name, visibility?}` | `CollectionDto` (201). 403 if a non-admin asks for PUBLIC. 409 for a duplicate name or the cap. |
| PATCH | `/collections/{id}` | JWT | `{name?, visibility?}` | `CollectionDto`. 400 on the default collection. 403 if a non-admin sets PUBLIC. |
| DELETE | `/collections/{id}` | JWT | — | 204. 400 on the default collection. |
| GET | `/collections/{id}/videos` | optional | `page,size` | A page of visible `VideoDto`. Works for a public collection, or for a private one if you are its owner. Otherwise 404. |
| PUT | `/collections/{id}/videos/{videoId}` | JWT | — | 204, idempotent. 404 if the video is not visible or you cannot manage the collection. |
| DELETE | `/collections/{id}/videos/{videoId}` | JWT | — | 204, idempotent |
| GET | `/collections/membership` | JWT | `videoId` | `ResultListInfo<UUID>`: ids of the manageable collections that contain the video |

`CollectionDto` has these fields:
- `id`, `name`, `visibility`, `isDefault`
- `ownerName`: display name only, never the email
- `itemCount`, `unavailableCount` (`null` unless `canManage`)
- `coverVideoId`, `canManage`, `updatedAt`

Endpoint rules:
- Writes carry `@RateLimited(permit = 30)`. The two public GETs carry `@RateLimited(permit = 60)`.
- `@AuditLog` is applied to create, rename, delete and visibility change, and to any write on a PUBLIC collection.
- The admin check reads the caller's role from the DB (ADMIN or SUPER_ADMIN), the same way `AdminAccountServiceImpl.actorRole` does. A token issued before a demotion therefore cannot manage public collections.
- PATCH, PUT and DELETE are already in `WebSecurityConfig.setAllowedMethods`. Verify only.

### Frontend

```
AppShell (activeNav === 'library')
  └── LibraryView
        ├── "Nổi bật" — public collections (guests + users)
        ├── "Collection của tôi" — logged-in only; guests see sign-in hint
        ├── CollectionDetail (reel tile grid like Explore; click → open in feed; Share copies ?l=<id>)
        ├── Create / rename modal — admins see "Công khai" toggle
        └── delete via useConfirm
VideoSwiper action column
  └── "Lưu" button (bookmark_add) → AddToCollectionModal
        ├── checkbox per manageable collection (public ones show a globe badge for admins)
        └── inline "Tạo collection mới"
AppShell deep link ?l=<id> → activeNav='library' + open CollectionDetail (same strip-from-URL as ?v=)
```

- The "Lưu" button shows a login hint for guests, the same way `VoteButtons` does.
- All mutation buttons use `aria-busy`, the busy feedback added in `7d8f72a5`.
- Query keys:
  - `['collections', 'mine']`
  - `['collections', 'public']`
  - `['collections', id, 'videos', page]`
  - `['collections', 'membership', videoId]`
- Toggling an item invalidates the affected keys. Changing visibility invalidates both lists.

## Tasks

| ID | Task | Est. | Depends on | Acceptance criteria |
|----|------|------|-----------|---------------------|
| BE-1 | Migration, `VideoCollection` and `VideoCollectionItem` entities, and repos | 0.5d | — | Liquibase applies cleanly locally. The CHECK and unique indexes reject bad rows. |
| BE-2 | DTOs and `VideoCollectionService` / impl | 1.5d | BE-1 | Unit tests cover: lazy default; duplicate name 409 (per user and public-global); cap 409; default rename, delete or make-public 400; non-admin PUBLIC 403; a second admin can manage another admin's public collection; foreign private id 404; a hidden video is excluded, with `unavailableCount` only for managers; idempotent add and remove. |
| BE-3 | `VideoCollectionController`, `OPTIONAL_AUTH_PATH` entries, and HTTP tests | 0.5d | BE-2 | WebMvc tests cover 200/201/204/400/401/403/404/409. A guest can GET public endpoints and gets 401 on writes. `mvn verify` passes the coverage gate. |
| FE-1 | `api/collections.js` and `hooks/useCollections.js` | 0.5d | BE-3 contract | Hook tests: mine and public lists, create, toggle an item, with invalidation |
| FE-2 | `AddToCollectionModal` and the "Lưu" button in `VideoSwiper` | 1d | FE-1 | A checkbox toggle adds or removes the video. Inline create adds the video to the new collection. A guest sees the login hint. An admin sees public collections with a badge. Buttons show `aria-busy`. |
| FE-3 | `LibraryView` (public section, my section, detail, create/rename/delete, `?l=` deep link) | 1.5d | FE-1 | A guest sees public collections and can play them. "Xem sau" is pinned first and cannot be deleted. The "Công khai" toggle shows only for admins. Delete uses `useConfirm`. A `?l=` link opens the collection for a guest. |
| QA-1 | Local E2E and prod smoke | 0.5d | BE-3, FE-2, FE-3 | Admin creates a public collection and adds videos. A guest opens the `?l=` link and sees them. Hiding a video in admin removes it for the guest, and the admin sees the unavailable count. A user creates a private one, and another user's GET returns 404. Deleting a video removes its item. |

The total is about 6 days, which makes this a Medium feature.

## Dependency Graph

```
BE-1 → BE-2 → BE-3 → FE-1 ─┬→ FE-2 ─┐
                           └→ FE-3 ─┴→ QA-1
```

FE-2 and FE-3 can run in parallel once FE-1 is merged.

## Beads

`bd` is not installed in this repo (there is no `.beads/`), so tasks are tracked in this table and in `docs/tracking.md`. When `bd` is available:

```bash
bd create --title="Library Collections" --type=feature --priority=2
bd create --title="[BE-1] Collections migration + entities + repos" --type=task
bd create --title="[BE-2] VideoCollectionService (private + admin public)" --type=task
bd create --title="[BE-3] VideoCollectionController + optional-auth + tests" --type=task
bd create --title="[FE-1] collections API + hooks" --type=task
bd create --title="[FE-2] AddToCollectionModal + Save button" --type=task
bd create --title="[FE-3] LibraryView + ?l= deep link" --type=task
bd create --title="[QA-1] E2E + prod smoke" --type=task
# deps: BE-2←BE-1, BE-3←BE-2, FE-1←BE-3, FE-2←FE-1, FE-3←FE-1, QA-1←FE-2+FE-3
```

## Risks

| Risk | Mitigation |
|------|------------|
| The default collection is created twice in a race | The partial unique index `uq_video_collections_user_default` blocks it. Catch `DataIntegrityViolationException` and re-read. |
| A demoted admin keeps managing public collections | Resolved: the role is read from the DB on every request, not from the JWT. |
| Public endpoints get scraped or hammered | `@RateLimited`, paginated videos (max `size` 50), no emails in the DTO |
| N+1 when listing collections with counts and cover | One aggregate query: `GROUP BY collection_id` with `FILTER` counts for visible and unavailable items |
| Rolling deploy | The new tables are additive. Existing tables are not altered. |
| Coverage gate | BE-2 and BE-3 tests target at least 90% of the new classes |

## Rollback

All changes are additive.
1. Revert the FE commit. Library returns to `ComingSoon`.
2. Run `DROP TABLE video_collection_items; DROP TABLE video_collections;`.
3. Remove the Liquibase changeset row.

No existing data is touched.

## Progress Log

| Date | Update |
|------|--------|
| 2026-10-13 | Plan created from codebase exploration. ADR-0012 amended (no `bookmarks` table). |
| 2026-10-13 | Owner approved dropping bookmarks. Added admin/SA public collections that everyone, including guests, can view. |
| 2026-10-13 | BE-1..3 and FE-1..3 implemented. CollectionDto gained `coverFileId` for thumbnails. A collection's videos play inside LibraryView in its own VideoSwiper, not through Explore's popular-list index, so non-popular videos also work. |
