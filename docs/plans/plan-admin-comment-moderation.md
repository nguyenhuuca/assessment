# Plan: Admin Comment Moderation (Content Manager → Comments)

<!--
Implementation Plan
Filename: docs/plans/plan-admin-comment-moderation.md
Owner: Builder (/builder)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)
-->

## Overview

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-10-02
**Related:** [ADR-0007 Admin Dashboard](../adr/0007-admin-dashboard.md), [ADR-0015 Bitwise Permissions](../adr/0015-bitwise-permission-system.md), [plan-admin-dashboard](plan-admin-dashboard.md)
**Decision class:** Two-Way Door — additive nullable/defaulted columns on `video_comments`, new admin endpoints, new FE tab. No ADR; decisions recorded below.

## Objective

Admins can **review every comment** in the Content Manager and **remove comments that violate the community policy** (with a reason), and **restore** them if removed by mistake. Removed comments disappear from the public feed immediately, but the record is kept for audit.

## Scope

### In Scope
- New `COMMENTS` tab in `AdminView` (`CONTENT_MANAGER`)
- List all comments (newest first, paginated) with filters: status, keyword, video
- Remove (soft) with a policy reason + optional note; Restore
- Public comment API hides removed comments
- Audit: `@AuditLog` + `moderated_by` / `moderated_at` on the row
- Stats card: removed comments count

### Out of Scope (follow-ups)
- User "Report comment" button / report queue
- Auto-moderation (ChatGPT / keyword filter)
- Banning or muting the comment author
- Bulk actions
- Hiding author email in the public comments API (separate privacy fix)

---

## Policy Reasons

Stored as enum `CommentModerationReason`:

| Code | Meaning |
|------|---------|
| `SPAM` | Spam, ads, repeated links |
| `HARASSMENT` | Insults, bullying, personal attacks |
| `HATE_SPEECH` | Hate based on race, religion, gender… |
| `SEXUAL` | Sexual / NSFW content |
| `VIOLENCE` | Threats, incitement to violence |
| `PERSONAL_INFO` | Doxxing, phone numbers, addresses |
| `OTHER` | Anything else — note required |

---

## Technical Approach

### Architecture

```
[AdminView → COMMENTS tab → AdminCommentTable]
        ↓ useAdminComments / useModerateComment
[api/admin.js]
  GET   /v1/funny-app/admin/comments?status=&q=&videoId=&page=&size=
  PATCH /v1/funny-app/admin/comments/{id}/moderation   {action: REMOVE|RESTORE, reason?, note?}
  GET   /v1/funny-app/admin/stats                       (+ removedComments)
        ↓ Spring Boot (class-level @PreAuthorize("hasRole('ADMIN')"))
[AdminController] → [AdminCommentService / Impl] → [VideoCommentRepository, VideoSourceRepository]
        ↓
[PostgreSQL: video_comments (+ status, moderation_* columns)]
```

### Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Removal type | **Soft remove** (`status = REMOVED`) | Reversible, keeps evidence for disputes/audit; hard delete stays only for owner self-delete |
| Replies of a removed comment | Public tree shows placeholder *"Bình luận đã bị gỡ do vi phạm chính sách"* (no author/content) if it has visible replies; otherwise omitted | Keeps thread structure without leaking removed content |
| Who can moderate | `ADMIN` role (class-level, existing) + `@HasPermission(perm = Permission.ADMIN)` | Same as other admin endpoints; no new permission bit until a content-manager role is needed |
| Paging | Spring `Pageable`, `ResultObjectInfo<Page<AdminCommentDto>>`, default size 20 sort `createdAt DESC` | Matches `GET /admin/videos` |
| Filtering | JPA `Specification` (status, `content ILIKE %q%`, videoId) | Optional combinable filters without query explosion |
| Video title in list | Batch lookup: collect `videoId`s on the page → `VideoSourceRepository.findAllById` → map | `video_id` is `VARCHAR` without FK; avoids N+1 |
| Audit | `@AuditLog("moderateComment")` + persisted `moderated_by` (admin email), `moderated_at`, `moderation_reason`, `moderation_note` | Who/when/why visible in UI and logs |
| Owner delete of a removed comment | Still allowed (hard delete) | Owner's right to erase own data |

### Schema change

```sql
ALTER TABLE video_comments
    ADD COLUMN status            VARCHAR(20)  NOT NULL DEFAULT 'VISIBLE',
    ADD COLUMN moderation_reason VARCHAR(30),
    ADD COLUMN moderation_note   VARCHAR(500),
    ADD COLUMN moderated_by      VARCHAR(255),
    ADD COLUMN moderated_at      TIMESTAMPTZ;

ALTER TABLE video_comments
    ADD CONSTRAINT chk_video_comments_status CHECK (status IN ('VISIBLE', 'REMOVED'));

CREATE INDEX idx_video_comments_status_created ON video_comments (status, created_at DESC);
```

`ADD COLUMN … DEFAULT <constant>` is metadata-only on PostgreSQL 11+, so no table rewrite.

### DTOs

```json
// AdminCommentDto
{
  "id": "uuid", "videoId": "14", "videoTitle": "Funny cat",
  "authorEmail": "a@b.com", "guestName": null, "isGuest": false,
  "content": "…", "parentId": null, "createdAt": "…",
  "status": "REMOVED", "moderationReason": "SPAM", "moderationNote": "link farm",
  "moderatedBy": "admin@x.com", "moderatedAt": "…"
}
// ModerateCommentRequest
{ "action": "REMOVE", "reason": "SPAM", "note": "optional, required when reason=OTHER, max 500" }
```

---

## Implementation Steps

### Phase 1 — Data layer (0.5 day) — `CM-1`
- [ ] Migration `api/src/main/resources/db/changelog/sql/202610020001-add-comment-moderation.sql` (schema above)
- [ ] `enums/CommentStatus.java` (`VISIBLE`, `REMOVED`), `enums/CommentModerationReason.java`
- [ ] `VideoComment`: add `status` (`@Enumerated(STRING)`, default `VISIBLE` in builder), `moderationReason`, `moderationNote`, `moderatedBy`, `moderatedAt`
- [ ] `VideoCommentRepository extends JpaSpecificationExecutor<VideoComment>`; `long countByStatus(CommentStatus)`

**AC:** Liquibase applies on existing DB; existing comments become `VISIBLE`; existing tests green.

### Phase 2 — Public API respects moderation (0.5 day) — `CM-2` (depends on CM-1)
- [ ] `VideoCommentServiceImpl.getNestedComments`: removed comments → placeholder node if they have visible replies, else dropped; placeholder has no `content`/`userId`/`guestName`, flag `removed: true`
- [ ] `CommentNode`: add `boolean removed`
- [ ] New comments always created with `VISIBLE`
- [ ] Tests: removed leaf hidden; removed parent with reply → placeholder; replies intact

**AC:** `GET /videos/{id}/comments` never returns removed content.

### Phase 3 — Admin API (1 day) — `CM-3` (depends on CM-1)
- [ ] `dto/admin/AdminCommentDto`, `dto/admin/ModerateCommentRequest` (`@NotNull action`; reason required for REMOVE; note required for OTHER; `@Size(max=500)` note)
- [ ] `service/AdminCommentService` + `impl/AdminCommentServiceImpl`
  - `getComments(Pageable, CommentStatus, String q, String videoId)` — Specification + batch video-title lookup
  - `moderate(UUID id, ModerateCommentRequest)` — REMOVE sets status/reason/note/moderatedBy(=`AppUtils.getCurrentUser().getEmail()`)/moderatedAt; RESTORE sets `VISIBLE`, keeps last moderation fields for history; 404 unknown id; idempotent
- [ ] `AdminController`: `GET /admin/comments`, `PATCH /admin/comments/{id}/moderation` with `@AuditLog("moderateComment")`, `@HasPermission(perm = Permission.ADMIN)`
- [ ] `AdminStatsDto` + `getStats()`: add `removedComments`, `totalComments`
- [ ] Tests: service (Mockito) — filters, remove, restore, validation (OTHER without note → 400), unknown id → 404; controller (`@WebMvcTest`) — 403 for non-admin, 200 for admin, paging params
- [ ] `mvn verify` passes coverage gate

**AC:** non-admin gets 403; admin removes → public API hides it; restore → visible again.

### Phase 4 — Frontend (1 day) — `CM-4` (can start against the contract; integrates after CM-3)
- [ ] `src/api/admin.js`: `getComments(params)`, `moderateComment(id, body)`
- [ ] `src/hooks/useAdmin.js`: `useAdminComments(page, filters)` key `['admin','comments',page,status,q,videoId]`; `useModerateComment()` invalidates `['admin','comments']` + `['admin','stats']`
- [ ] `components/admin/AdminCommentTable.jsx` (follow `AdminVideoTable` style, `SaveIndicator`, pagination)
  - Columns: COMMENT (2-line clamp, click to expand), AUTHOR (email or `Guest · AnonymousN`), VIDEO (title, id), CREATED, STATUS badge (+ reason, by, at on hover), ACTIONS
  - Filters: status (`ALL / VISIBLE / REMOVED`), keyword search (debounced 300 ms)
  - REMOVE opens small modal (`.app-modal`): reason select (policy table), note textarea (required for OTHER); RESTORE uses confirm
- [ ] `AdminView.jsx`: add tab `{ key: 'COMMENTS', label: 'COMMENTS' }`; stat card `REMOVED_COMMENTS`
- [ ] `components/comments/CommentPanel.jsx`: render placeholder for `removed: true` nodes (italic muted text, no delete button)
- [ ] `styles/index.css`: status badge `.admin-badge.removed` if missing
- [ ] Tests (Vitest): table renders rows, filter changes query, remove flow requires reason, OTHER requires note, restore, placeholder render in CommentPanel
- [ ] `npm run lint && npm run test && npm run build`

### Phase 5 — Verification (0.25 day) — `CM-5` (depends on CM-2, CM-3, CM-4)
- [ ] Prod/staging E2E: admin removes a comment → disappears for viewers (reload) → restore → reappears; non-admin `GET /admin/comments` → 403
- [ ] Check AUDIT log line for `moderateComment`
- [ ] Update `docs/tracking.md`, Trello

---

## Dependency Graph

```
CM-1 ──┬──► CM-2 ─────────────┐
       └──► CM-3 ──┐          ├──► CM-5
CM-4 (contract-first) ┴───────┘
```

**Estimate:** ~3.25 days (≈2 days on critical path with BE/FE in parallel).

## Risks

| Risk | Mitigation |
|------|-----------|
| `ALTER TABLE` during rolling deploy → `cached plan must not change result type` on old pods (seen before) | Add `prepareThreshold=0` to JDBC URL, or deploy during low traffic; Hibernate selects explicit columns so impact is limited |
| Keyword search `ILIKE '%q%'` is a sequential scan | Acceptable at current volume; add `pg_trgm` GIN index if comments > 100k |
| Admin removes by mistake | Soft remove + Restore; moderation history kept on row and in AUDIT log |
| Removed content leaking via public API | Placeholder strips content/author; dedicated test |
| Author email shown in admin table | Admin-only endpoint behind `hasRole('ADMIN')`; acceptable |
