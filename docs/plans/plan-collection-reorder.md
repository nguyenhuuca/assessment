# Plan: Reorder Videos in a Collection (drag & drop)

<!--
Implementation Plan
Filename: docs/plans/plan-collection-reorder.md
Owner: Builder (/swarm-execute)
Handoff to: Builder, QA Engineer
-->

## Overview

**Status:** Done
**Author:** nguyenhuuca
**Date:** 2026-10-18
**Related plan:** [plan-library-collections](plan-library-collections.md) (listed drag & drop reordering as out of scope)
**Related ADR:** [ADR-0012](../adr/0012-bookmark-feature-design.md) (collections design). No new ADR: the schema change is additive (one column + index) and can be undone in one migration.
**Size:** Small feature (1–3 days): plan only, per `swarm-plan`.

## Objective

A user who manages a collection (owner of a private one, or any admin for a public one) can **drag & drop videos to set the viewing order**. "Phát" and the in-collection swiper then play videos in that order.

## Decisions (agreed 2026-10-18)

| # | Decision | Why |
|---|----------|-----|
| D1 | FE uses **`@dnd-kit/core` 6.x + `@dnd-kit/sortable` 10.x + `@dnd-kit/utilities` 3.x** | Works with touch (mobile is the main client) and keyboard, with screen-reader announcements. HTML5 DnD does not work on touch. The owner approved this deviation from tech-strategy; FE-2 records it in `tech-strategy.md` |
| D2 | **New videos go to the top** (`position = MIN − 1`) | Matches today's newest-first order, so existing collections look the same after the backfill |
| D3 | Order is stored as an integer **`position`** on `video_collection_items`; a move **renumbers the whole collection** 0..n−1 in one `UPDATE … FROM unnest(...) WITH ORDINALITY` | A collection is capped at 500 items, so a full renumber is cheap and leaves no gaps. Gap or fractional ranking would be premature |
| D4 | API is **relative**: `PATCH /collections/{id}/videos/{videoId}` with body `{ "afterVideoId": Long \| null }` (`null` = move to the top) | Hidden or unpublished items are not shown to the client, so an absolute index over visible items would be wrong. A neighbour id is not. PATCH is already in the CORS allow-list (`WebSecurityConfig:139`) |
| D5 | Reordering happens in a **"Sắp xếp" mode** that loads the whole list (`useCollectionPlaylist`, at most 500 items). The normal 24-per-page grid stays as it is | A video cannot be dragged across pages, and a tile cannot be both click-to-play and draggable without conflicts |
| D6 | **Cover = first visible video in the user's order** (it was the newest one) | Works like a playlist. Because of D2, unsorted collections keep the same cover |
| D7 | A move **does not bump `updated_at`** (no `touch`). It still evicts the public cache when the collection is public | Reordering is not new content, so it should not push a public collection up the "recently updated" list. The cover can change, so the cache has to be evicted |
| D8 | The default "Xem sau" collection **can be reordered** | It is a normal list. Only rename and delete are blocked for it |

## Scope

### In Scope

- Migration: `position INTEGER NOT NULL DEFAULT 0`, backfilled with `row_number() OVER (PARTITION BY collection_id ORDER BY added_at DESC, video_id DESC) - 1`. Replace `idx_video_collection_items_recent` with `(collection_id, position, video_id)`
- Repo: `addItem` inserts at the top. `findVisibleVideos` and `findCovers` order by `position, video_id`. New `findOrderedVideoIds` and `renumber`
- Service `moveVideo(id, videoId, afterVideoId)` + `MoveVideoRequest` DTO + controller endpoint + i18n error keys (vi/en)
- FE: `collectionsApi.moveVideo`, `useMoveCollectionItem` (optimistic update + rollback), "Sắp xếp" mode with a sortable grid, drag handle, keyboard support, vi/en strings

### Out of Scope

- Bulk sort options (by title or date): could come later
- Moving a video between collections
- Reordering from inside the player or swiper
- Undo for a move

## API Contract (FE-1 can start from this before BE lands)

```
PATCH /v1/funny-app/collections/{id}/videos/{videoId}
Body: { "afterVideoId": 123 }   // or null → move to top
204 No Content                  // also when the video is already in that place
400 error.collection.moveInvalid      // afterVideoId == videoId
404 error.collection.notFound         // not manageable (same rule as add/remove)
404 error.collection.itemNotFound     // videoId or afterVideoId is not in the collection
401 error.auth.required
@RateLimited(permit = 60) · @AuditLog("moveVideoInCollection")
```

`GET /collections/{id}/videos` keeps its shape. Only the order changes (`position ASC, video_id ASC`).

## Move Algorithm (service)

1. `requireManageable(id, user, admin)`: takes the `FOR UPDATE` row lock, so concurrent moves on one collection run one after another
2. `ids = itemRepository.findOrderedVideoIds(cid)`: all items, visible or not, `ORDER BY position, video_id`
3. Validate: `videoId` is in `ids` (otherwise 404 `itemNotFound`). `afterVideoId` is null or in `ids` (otherwise 404) and is not `videoId` (otherwise 400 `moveInvalid`)
4. Remove `videoId`, then insert it at index 0 (when `afterVideoId` is null) or right after `afterVideoId`
5. If the order did not change, return. Otherwise run `itemRepository.renumber(cid, newIds)` and then `evictPublicIf(collection)`

Hidden items keep their place relative to their neighbours, because the client only names visible neighbours.

## Tasks

| ID | Task | Est. LOC | Depends on |
|----|------|---------:|------------|
| BE-1 | Migration + repository ordering | ~120 | — |
| BE-2 | `moveVideo` service, DTO, controller, i18n errors | ~220 | BE-1 |
| FE-1 | API client + `useMoveCollectionItem` + `moveAfterId` util | ~130 | — (uses the contract above) |
| FE-2 | `@dnd-kit` "Sắp xếp" mode in `CollectionDetail` + tech-strategy entry | ~260 | FE-1 |
| QA-1 | Local E2E (mouse, touch emulation, keyboard) + prod smoke | — | BE-2, FE-2 |

Merge order to `main`: BE-1 → BE-2, and FE-1 → FE-2 in parallel. Each unit merges on its own: before BE-2 lands, FE-2 can be merged with the "Sắp xếp" button behind `canManage`, and a failing PATCH rolls the order back and shows an error. **Push FE-2 only after BE-2 is deployed**, so prod never shows a button that always fails.

### BE-1: Migration + repository ordering

- `db/changelog/sql/202610180001-video-collection-items-position.sql`: add the column with a default (old pods keep inserting during a rolling deploy), backfill, drop the old index, create `idx_video_collection_items_position (collection_id, position, video_id)`
- `VideoCollectionItemRepository`:
  - `addItem` → `INSERT … SELECT :cid, :vid, COALESCE((SELECT MIN(position) FROM video_collection_items WHERE collection_id = :cid), 0) - 1 ON CONFLICT DO NOTHING`
  - `findVisibleVideos` and its `countQuery`: `ORDER BY i.position, i.video_id`
  - `findCovers` LATERAL: `ORDER BY i.position, i.video_id LIMIT 1`, and update the Javadoc ("first visible video")
  - `findOrderedVideoIds(cid)` → `List<Long>`
  - `@Modifying renumber(cid, Long[] ids)` → `UPDATE video_collection_items i SET position = o.ord - 1 FROM unnest(CAST(:ids AS bigint[])) WITH ORDINALITY o(video_id, ord) WHERE i.collection_id = :cid AND i.video_id = o.video_id`
- Update the `VideoCollectionService#getVideos` Javadoc ("in collection order")

**Acceptance criteria**
- [ ] Liquibase runs cleanly on a copy of the local DB. Existing collections come back in the same order as before (newest added first)
- [ ] A newly added video comes back first from `GET /collections/{id}/videos`
- [ ] The cover is the first visible item in `position` order
- [ ] `renumber` binds `Long[]` correctly on Hibernate 6 / Postgres. Check by hand with `startLocal.sh` + psql, because the repo has no DB integration tests (no Testcontainers or `@DataJpaTest`)
- [ ] `mvn verify` passes, with LINE coverage ≥ `api/.coverage-threshold`

### BE-2: `moveVideo` service, DTO, controller, i18n errors

- `dto/collection/MoveVideoRequest` (record or Lombok, following `CollectionRequest`): `Long afterVideoId`
- `VideoCollectionService#moveVideo(UUID id, Long videoId, Long afterVideoId)` + impl, following the algorithm above
- `VideoCollectionController`: `@PatchMapping("/{id}/videos/{videoId}")`, `@RateLimited(permit = 60)`, `@AuditLog("moveVideoInCollection")`, returns 204
- `i18n/messages.properties` + `messages_vi.properties`: `error.collection.itemNotFound`, `error.collection.moveInvalid`

**Acceptance criteria**
- [ ] `VideoCollectionServiceImplTest` covers: move to the top, to the middle, to the bottom; a no-op (no `renumber` call); `videoId` not in the collection → 404; `afterVideoId` not in the collection → 404; `afterVideoId == videoId` → 400; a non-manager → 404; a public collection evicts the cache; a private one does not; `updatedAt` is not changed
- [ ] `VideoCollectionControllerTest` covers: 204 on success, a null body field means top, 401 for a guest
- [ ] Both new message keys exist in vi and en (the i18n parity test passes)
- [ ] `mvn verify` passes and coverage is ≥ the threshold

### FE-1: API client + hook + util

- `api/collections.js`: `moveVideo: (id, videoId, afterVideoId) => api.patch(\`/collections/${enc(id)}/videos/${enc(videoId)}\`, { afterVideoId })`
- `components/library/collectionUtils.js`: `moveAfterId(ids, fromIndex, toIndex)` returns the `afterVideoId` for the API (`null` when the target is index 0), using the list *after* the move
- `hooks/useCollections.js`: `useMoveCollectionItem()`
  - `onMutate`: cancel queries, then `setQueryData(['collections', id, 'videos', 'all'], arrayMove…)`. Return the snapshot
  - `onError`: restore the snapshot
  - `onSettled`: invalidate the page queries `['collections', id, 'videos', <n>]` and the lists (the cover may change; `includePublic: visibility !== 'PRIVATE'`). Leave out the `'all'` key, so a refetch of up to 500 items does not follow every drop

**Acceptance criteria**
- [ ] Vitest: `moveAfterId` for first→last, last→first, a middle move, and a no-op
- [ ] Vitest `useCollections.test.jsx`: the optimistic order shows up at once and rolls back on error. The PATCH body has the right `afterVideoId`
- [ ] `npm run lint` and `npm run test` pass

### FE-2: "Sắp xếp" mode with `@dnd-kit`

- `npm i @dnd-kit/core@^6.3.1 @dnd-kit/sortable@^10.0.0 @dnd-kit/utilities@^3.2.2`
- `LibraryView.jsx`, `CollectionDetail`: for `canManage`, add a "Sắp xếp" / "Xong" toggle button. In sort mode, render `<ReorderGrid>`:
  - data from `useCollectionPlaylist(collectionId)`, which is the same cache that "Phát" uses
  - `DndContext` with `PointerSensor` (activation distance 5px), `TouchSensor` (delay 200ms, tolerance 5, so page scroll still works), `KeyboardSensor` + `sortableKeyboardCoordinates`
  - `SortableContext` + `rectSortingStrategy` over the existing `explore-reels-grid` and tile look. A drag handle button (`drag_indicator` icon, `aria-label` from i18n). Clicking a tile does nothing in sort mode
  - `onDragEnd` → `moveAfterId` → `useMoveCollectionItem().mutate`. Show an `app-alert error` on failure
  - `accessibility.announcements` / `screenReaderInstructions` localized
- `i18n/locales/{vi,en}/library.json`: `reorder`, `reorderDone`, `reorderHint`, `dragHandleAria`, `moveError`, and the announcement strings
- `.claude/rules/tech-strategy.md`, JavaScript table: add the row `| Drag & Drop | @dnd-kit (core + sortable) |`

**Acceptance criteria**
- [ ] The "Sắp xếp" button shows only when `canManage`. Guests and viewers of a public collection do not see it
- [ ] Dragging with the mouse on desktop, a long press and drag on mobile (Chrome DevTools touch emulation), and keyboard (Space → arrows → Space) all reorder the list and persist after a reload
- [ ] After a reorder, "Phát" and the paged grid show the new order. The library card cover shows the first video
- [ ] Vitest `LibraryView.test.jsx`: the button is hidden for non-managers; sort mode renders the handles; `onDragEnd` calls the mutation with the right ids (mock the hook or fire the handler)
- [ ] The i18n parity test passes, `npm run lint` and `npm run build` pass, and the bundle grows by less than about 20 KB gzip

### QA-1: E2E + prod smoke

- [ ] Local: run all three input methods. Two tabs moving items at the same time end in a consistent order with no 500. Add a video after sorting: it lands at the top
- [ ] A hidden video stays out of the list and keeps its relative place once it is published again
- [ ] Prod, after the deploy: reorder "Xem sau", reload, then press "Phát" and check the order

## Risks

| Risk | Mitigation |
|------|-----------|
| `unnest(CAST(:ids AS bigint[]))` binding fails on Hibernate 6 | Check by hand in BE-1. Fallback: a `CASE video_id WHEN … THEN …` UPDATE built from bind parameters, or a JDBC batch of single-row updates (500 at most) |
| Rolling deploy: Postgres "cached plan must not change result type" after `ALTER TABLE` (see memory: CORS/cached-plan gotcha) | No query does `SELECT i.*` on `video_collection_items`. Only columns are added. If it shows up anyway, add `prepareThreshold=0` to the JDBC URL |
| Old pods insert `position = 0` during the rollout | Those items land in the middle at worst. The next move renumbers the collection. Acceptable while pods roll |
| A touch drag conflicts with page scroll or swipe | The handle gets `touch-action: none`, and `TouchSensor` uses a delay. Drag works only from the handle |
| Large lists (500) re-render on drag | `rectSortingStrategy` + `React.memo` on the tile. Check it in QA-1 with a 500-item collection |
