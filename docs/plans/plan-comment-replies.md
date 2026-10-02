# Plan: Comment Replies

<!--
Implementation Plan
Filename: docs/plans/plan-comment-replies.md
Owner: Builder (/builder)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)
-->

## Overview

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-10-02
**Related:** [plan-admin-comment-moderation](plan-admin-comment-moderation.md), Trello #6/#7 (comments, `parent_id`)
**Decision class:** Two-Way Door — reuses existing `parent_id` column; one CHECK-constraint change (`status` gains `DELETED`). No ADR; decisions recorded below.

## Problem

The backend already stores `parent_id` and returns a nested tree (`CommentNode.replies`), but:

1. **FE** (`CommentPanel.jsx`) renders a flat list of root comments, never reads `replies`, and has no "Reply" action — replies are invisible.
2. **`parentId` is not validated** — a reply can point to a non-existent comment, a comment on another video, or a removed comment.
3. **Owner delete cascades** — deleting a parent hard-deletes *other users'* replies (`deleteRecursively`).
4. **No length limit** on `content` (`TEXT`, no `@Size`).

## Objective

Logged-in users can reply to any visible comment. Replies show under their thread (2-level layout like YouTube/Facebook), threads collapse when long, and deleting or moderating a parent never destroys other people's replies.

## Scope

### In Scope
- Reply action + inline reply box in `CommentPanel`
- 2-level threaded rendering, collapse "Xem N phản hồi"
- BE validation of `parentId`, normalization to the root, content length limit
- Author delete of a comment with replies → "deleted" placeholder instead of cascade
- Admin table: reply indicator + `DELETED` status

### Out of Scope (follow-ups)
- Notifications ("X replied to you")
- Editing comments
- Likes on comments
- Guest replies (FE only allows logged-in commenting today)
- Hiding author email in public API (separate privacy fix)

---

## Technical Approach

### Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Thread depth | **2 levels**: root + replies. Replying to a reply attaches to the **root** (BE normalizes `parentId` to the top-level ancestor); FE prefills `@name ` | Readable on a 360 px panel; no deep indentation; same as YouTube |
| Legacy deeper data | FE flattens all descendants of a root, sorted by `createdAt ASC` | Works even if old rows are nested deeper |
| Parent validation | Parent must exist (404), same `videoId` (400), status `VISIBLE` (400) | Prevents orphan/cross-video/removed-thread replies |
| Content limit | `@Size(max = 2000)` on `CreateCommentRequest.content` (roots and replies) | Abuse guard; DB stays `TEXT` |
| Author deletes comment **with** replies | Soft: `status = DELETED`, `content = ''`; public tree shows placeholder *"Bình luận đã bị xoá"* | Keeps other users' replies; author's text is erased |
| Author deletes comment **without** replies (or a reply) | Hard delete (unchanged) | Nothing to preserve |
| Placeholder pruning | Existing `pruneRemoved` treats `DELETED` like `REMOVED` (leaf → omitted; with visible replies → placeholder) | One code path |
| `CommentNode` | Add `deleted: boolean` (next to `removed`) | FE picks placeholder text |
| Admin restore of `DELETED` | Rejected 400 ("Deleted by author") | Author's decision; not a moderation state |
| Ordering | Roots as returned by API (`createdAt ASC`, unchanged); replies `createdAt ASC` | Conversation reads top-down |

### API changes

```
POST /v1/funny-app/videos/{videoId}/comments
  body: { content: string (1..2000), parentId?: uuid }
  400 parent on another video / parent not VISIBLE / content too long
  404 parent not found
  stored parentId = root of the thread

GET /v1/funny-app/videos/{videoId}/comments
  CommentNode += deleted: boolean   (placeholder: content/userId/guestName null)

DELETE /v1/funny-app/videos/{videoId}/comments/{id}
  has replies → soft DELETED, else hard delete (same auth rules)
```

### Schema change

```sql
ALTER TABLE video_comments DROP CONSTRAINT chk_video_comments_status;
ALTER TABLE video_comments
    ADD CONSTRAINT chk_video_comments_status CHECK (status IN ('VISIBLE', 'REMOVED', 'DELETED'));
```

### FE layout (CommentPanel)

```
(A) alice · 2h
    Great video!
    Trả lời · Xoá
    ├─ (B) bob · 1h         @alice agree
    │      Trả lời
    ├─ (C) carol · 30m      lol
    └─ Xem thêm 3 phản hồi ▾
    [ @bob |               ] [Gửi]   ← inline reply box (one open at a time)
```

- Replies indented 40 px, smaller avatar (24 px)
- Show first 2 replies; "Xem thêm N phản hồi" expands; expanded state kept after posting
- Header count = visible comments including replies
- Reply box: `Esc` closes, `Enter` sends, `Shift+Enter` newline; prefilled `@{name} ` when replying to a reply
- Placeholders: removed → "Bình luận đã bị gỡ do vi phạm chính sách", deleted → "Bình luận đã bị xoá"; no Reply/Delete on placeholders

---

## Implementation Steps

### Phase 1 — BE reply validation (0.5 day) — `RC-1`
- [ ] `CreateCommentRequest.content`: add `@Size(max = 2000)`
- [ ] `VideoCommentServiceImpl.createComment`: when `parentId` present → parse UUID (400 if invalid), load parent (404), check same `videoId` (400), check `VISIBLE` (400), normalize to root (walk `parentId` up, bounded loop of 50 to guard cycles)
- [ ] Use `CustomException` with proper HTTP status (as in `AdminCommentServiceImpl`)
- [ ] Tests: valid reply, reply-to-reply → root, parent missing, other video, removed parent, deleted parent, invalid UUID, content 2001 chars (controller `@WebMvcTest`)

**AC:** replies always point at a visible root of the same video.

### Phase 2 — BE author delete keeps replies (0.75 day) — `RC-2` (after RC-1, same service)
- [ ] Migration `202610020002-comment-status-deleted.sql` (constraint change above)
- [ ] `CommentStatus.DELETED`
- [ ] `deleteComment`: has children → `status = DELETED`, `content = ''`, save; else hard delete (replace `deleteRecursively` for the owner path)
- [ ] `getNestedComments` / `pruneRemoved`: treat `DELETED` as hidden; placeholder sets `deleted = true`; `CommentNode.deleted`
- [ ] `AdminCommentServiceImpl`: RESTORE on `DELETED` → 400; REMOVE on `DELETED` → no-op (unchanged)
- [ ] Tests: owner deletes leaf → hard delete; owner deletes parent with others' replies → replies kept, placeholder shown; deleted leaf after its replies are removed → omitted; admin restore DELETED → 400
- [ ] `mvn verify` passes coverage gate

**AC:** no user action deletes another user's reply.

### Phase 3 — FE threaded UI (1 day) — `RC-3` (contract-first; integrate after RC-1/RC-2)
- [ ] `api/comments.js`: `post(videoId, content, parentId)` → body `{ content, ...(parentId && { parentId }) }`
- [ ] `components/comments/CommentItem.jsx` (one row: avatar, name, time, content, actions) — extract from `CommentPanel`
- [ ] `components/comments/CommentThread.jsx` (root + flattened replies, collapse, reply box)
- [ ] `CommentPanel.jsx`: render threads; `replyTo` state `{ rootId, name }`; post with `parentId = rootId`; reload; keep thread expanded
- [ ] Placeholders for `removed` / `deleted`; hide actions on placeholders
- [ ] Map server errors (400/404) to the inline error alert
- [ ] Styles: `.comment-replies`, `.comment-reply-box`, `.comment-action-link` in `styles/index.css`
- [ ] Tests (Vitest): renders replies under root; collapse/expand; reply posts with `parentId`; reply-to-reply prefills `@name` and posts root id; deleted/removed placeholders; Esc closes box; guest sees no Reply
- [ ] `npm run lint && npx vitest run && npm run build`

### Phase 4 — Admin table (0.25 day) — `RC-4` (after RC-2)
- [ ] `AdminCommentTable`: `↳ Reply` tag when `parentId` set; status filter option `DELETED`; badge `.admin-badge.deleted`; hide Restore for `DELETED`
- [ ] Tests for the tag and hidden Restore

### Phase 5 — Verification (0.25 day) — `RC-5`
- [ ] Prod E2E: user A comments, user B replies, A replies to B (→ same thread, `@B`), A deletes root → placeholder + replies stay; admin removes a reply → hidden
- [ ] Update `docs/tracking.md`, Trello

---

## Dependency Graph

```
RC-1 ──► RC-2 ──► RC-4 ──┐
                         ├──► RC-5
RC-3 (contract-first) ───┘
```

**Estimate:** ~2.75 days (≈1.5 days critical path with BE/FE in parallel).

## Risks

| Risk | Mitigation |
|------|-----------|
| Behaviour change: owner delete no longer removes others' replies | Intended; covered by tests; placeholder explains |
| Constraint change during rolling deploy | Drop+add on a small table; old pods never write `DELETED` |
| Spam via replies | Same `@RateLimited(permit = 5)` + 2000-char limit + admin moderation/bulk remove |
| Very long threads on mobile | Collapse after 2 replies |
| Cycle in legacy `parent_id` data | Bounded root walk (50 hops) |
