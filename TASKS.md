# TASKS.md

Format: `[ ]` pending · `[~]` in progress · `[x]` done

---

## Feature: Admin Dashboard — Content & Account Management

**ADR:** `artifacts/adr_admin_dashboard.md`  
**Plan:** `artifacts/plan_admin_dashboard.md`  
**Design:** `stitch_admin.png`

---

### Phase 1 — DB & Entity Foundation

- [x] **BE-1** Create migration `202504300001-add-user-role.sql` — add `role VARCHAR(20) NOT NULL DEFAULT 'USER'` to `users`
- [x] **BE-1** Create migration `202504300002-add-video-status.sql` — add `status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED'` to `video_sources`
- [x] **BE-2** Create `enums/UserRole.java` (`USER`, `ADMIN`)
- [x] **BE-2** Create `enums/VideoStatus.java` (`PUBLISHED`, `PENDING`, `FLAGGED`)
- [x] **BE-3** Add `UserRole role` field to `entity/User.java`
- [x] **BE-3** Add `VideoStatus status` field to `entity/VideoSource.java`

### Phase 2 — Auth: JWT Role Claim

- [x] **BE-4** Add `String role` field to `dto/UserDetailDto.java`
- [x] **BE-5** Update `JwtProvider.generatePayload()` — include `role` claim
- [x] **BE-5** Update `JwtProvider.convertValue()` — read `role` claim back into dto
- [x] **BE-6** Update `JWTAuthenticationFilter` — build `GrantedAuthority` list from role (replace `Collections.emptyList()`)
- [x] **BE-7** Update `WebSecurityConfig` — change `/admin/**` from `authenticated()` to `hasRole("ADMIN")` on `/v1/funny-app/admin/**`

### Phase 3 — Backend Admin API

- [x] **BE-8** Create `dto/AdminVideoDto.java` — `id, title, thumbnailPath, creatorEmail, status, viewCount, createdAt`
- [x] **BE-8** Create `dto/AdminAccountDto.java` — `id, email, role, mfaEnabled, createdAt`
- [x] **BE-8** Create `dto/AdminStatsDto.java` — `totalVideos, totalUsers, pendingCount, flaggedCount`
- [x] **BE-9** Create `service/AdminVideoService.java` interface
- [x] **BE-9** Create `service/impl/AdminVideoServiceImpl.java` — `getVideos`, `updateStatus`, `deleteVideo`, `getStats`
- [x] **BE-10** Create `service/AdminAccountService.java` interface
- [x] **BE-10** Create `service/impl/AdminAccountServiceImpl.java` — `getAccounts`, `updateRole`, `deleteAccount` (with self-action guard)
- [x] **BE-11** Create `web/AdminController.java` — `@PreAuthorize("hasRole('ADMIN')")` at class level, 7 endpoints
- [x] **BE-repo** Add `countByStatus` + `findAllByStatus(Pageable)` to `VideoSourceRepository`

### Phase 4 — Frontend

- [x] **FE-1** Update `AuthContext.jsx` — expose `user.isAdmin` from `/user/me` response
- [x] **FE-2** Update `AppShell.jsx` — add Admin to `SIDE_NAV` with `requiresAdmin: true` guard
- [x] **FE-3** Create `src/api/admin.js` — 7 API functions
- [x] **FE-4** Create `src/hooks/useAdmin.js` — React Query hooks (queries + mutations)
- [x] **FE-5** Create `src/components/admin/AdminView.jsx` — tabs container + stat cards
- [x] **FE-6** Create `src/components/admin/AdminVideoTable.jsx` — table with status badges + actions
- [x] **FE-7** Create `src/components/admin/AdminAccountTable.jsx` — table with role badges + actions
- [x] **FE-8** Update `src/styles/index.css` — add `.admin-*` CSS classes

---

## Dependency Order

```
BE-1 → BE-2/3 → BE-4/5/6 → BE-7 → BE-8 → BE-9/10 → BE-11
                    ↓
               FE-1 → FE-2
BE-11 → FE-3/4 → FE-5/6/7 ← FE-8 (parallel)
```

---

## Feature: Video Reactions (Like / Unlike / Dislike)

**Plan:** `docs/plans/plan-video-reactions.md`
**Fixes:** 404 on `POST /v1/funny-app/video/like` (endpoint never existed)

- [x] **BE-R1** Migration `202610010001-create-video-reactions.sql` + `ReactionType` enum + `VideoReaction` entity + `VideoReactionRepository` (native upsert, count by reaction)
- [x] **BE-R2** `ReactionSummaryDto`, `ReactionRequest`, `VideoReactionService` + impl + Mockito tests — depends on BE-R1
- [x] **BE-R3** `VideoReactionController` GET/PUT/DELETE `/videos/{videoId}/reaction` + security (writes require JWT) + `@WebMvcTest` + `mvn verify` — depends on BE-R2
- [x] **FE-R1** `api.put`, `api/reactions.js`, remove `videosApi.like/unlike`, `useVideoReaction` hook (optimistic + rollback) + tests — parallel, integrates after BE-R3
- [x] **FE-R2** Rewrite `VoteButtons.jsx` (like/unlike/dislike/switch, guest guard, no silent catch) + tests + lint/build — depends on FE-R1
- [ ] **QA-R1** Manual E2E (persist after reload, multi-user counts, guest), update Trello [9]/[10] + `docs/tracking.md` — depends on BE-R3, FE-R2

---

## Feature: Admin Comment Moderation (Content Manager → COMMENTS)

**Plan:** `docs/plans/plan-admin-comment-moderation.md`

- [x] **CM-1** Migration `202610020001-add-comment-moderation.sql` (status + moderation_* cols, index) + `CommentStatus`/`CommentModerationReason` enums + entity fields + repo `JpaSpecificationExecutor`, `countByStatus`
- [x] **CM-2** Public `getNestedComments` hides removed (placeholder when it has replies) + `CommentNode.removed` + tests — depends on CM-1
- [x] **CM-3** `AdminCommentService` + `GET /admin/comments` (filters, paging, batch video titles) + `PATCH /admin/comments/{id}/moderation` (REMOVE/RESTORE, reason, note, @AuditLog) + stats + tests + `mvn verify` — depends on CM-1
- [x] **CM-4** `admin.js` + hooks + `AdminCommentTable` (filters, remove modal w/ reason, restore) + COMMENTS tab + stat card + CommentPanel placeholder + tests/lint/build — contract-first, integrates after CM-3
- [x] **CM-6** Bulk moderation: `PATCH /admin/comments/moderation` (≤100 ids, REMOVE/RESTORE, @AuditLog) + checkbox select / select-all page + bulk bar (Gỡ / Khôi phục / Bỏ chọn) + tests
- [ ] **CM-5** E2E on prod (remove → hidden → restore), 403 for non-admin, AUDIT log check, update `docs/tracking.md` — depends on CM-2, CM-3, CM-4

---

## Feature: Comment Replies

**Plan:** `docs/plans/plan-comment-replies.md`

- [x] **RC-1** BE: validate `parentId` (exists 404, same video 400, VISIBLE 400, bad UUID 400), normalize to thread root, `content` `@Size(max=2000)` + tests
- [x] **RC-2** BE: migration `202610020002-comment-status-deleted.sql`, `CommentStatus.DELETED`, owner delete with replies → soft DELETED placeholder (no cascade of others' replies), `CommentNode.deleted`, admin RESTORE on DELETED → 400 + tests + `mvn verify` — after RC-1
- [x] **RC-3** FE: `comments.post(..., parentId)`, `CommentItem` + `CommentThread`, 2-level threads, collapse after 2, inline reply box (`@name` prefill, Esc/Enter), removed/deleted placeholders + tests/lint/build — contract-first
- [x] **RC-4** FE admin: `↳ Reply` tag, DELETED filter/badge, hide Restore for DELETED + tests — after RC-2
- [ ] **RC-5** Prod E2E (reply, reply-to-reply, owner delete keeps replies, admin remove reply), update `docs/tracking.md` — after all

---

## Feature: User Notifications

**PRD:** `docs/prd/PRD-user-notifications.md` · **ADR:** `docs/adr/0017-user-notifications.md` · **Plan:** `docs/plans/plan-user-notifications.md`

- [x] **NT-1** Migration `notifications` + entity + owner-scoped repo (grouped upsert, mark-all, retention delete) + `UserSettingsRepository.findByUserId`
- [x] **NT-2** Events (`CommentRepliedEvent`, `CommentRemovedEvent`) + `NotificationEventListener` (AFTER_COMMIT, @Async virtual threads, recipients, grouping, never throws) + tests — after NT-1
- [x] **NT-3** `NotificationController` list / unread-count / read / read-all (owner-only, 404 for others) + tests — after NT-1
- [x] **NT-4** SSE: `NotificationPublisher` + `InMemorySsePublisher` (cap 5/user, 30-min timeout, 25 s heartbeat, gauge) + `/notifications/stream` header auth + tests — after NT-3
- [x] **NT-5** Email digest every 15 min (notify_email, ≤50% daily budget, emailed_at) + 90-day retention job + tests — after NT-1
- [x] **NT-6** FE: `sseStream.js` parser, `useNotificationStream` (fetch + header, backoff, stop on 401), polling fallback, bell + dropdown, deep link `?v=&c=` with highlight + tests — contract-first
- [x] **NT-7** Prod E2E (two accounts, restart reconnect, digest), optional nginx stream location, update `docs/tracking.md` — after all
  - Verified on prod 2026-10-03 by owner with two accounts (reply, reply-to-reply after fix 368bf0c, badge). Grouping per thread keeps the badge at 1 per thread by design (ADR-0017).

---

## Feature: Optional Email + Password Login

**PRD:** `docs/prd/PRD-password-login.md` · **ADR:** `docs/adr/0018-password-login-option.md` (Accepted) · **Plan:** `docs/plans/plan-password-login.md`

- [x] **PW-1** ~~Prod pre-check~~ (no legacy passwords — owner-confirmed 2026-10-07), migration (`password_set_at`, `failed_login_count`, `locked_until`, `credentials_version`), `User` fields (`@Builder.Default`), `app.auth.password-login-enabled`, `PasswordPolicy` (10–72 bytes, no email part, common list), BCrypt 12
- [x] **PW-2** `POST /user/login` + lockout (5 fails → 15 min) + generic `INVALID_CREDENTIALS` + remove legacy auto-register path in `/user/join` + tests — after PW-1
- [x] **PW-3** `PUT/DELETE /user/password` (current pw / OTP rules, policy codes, new JWT, security email, per-user `passwordEnabled`) + tests — after PW-1
- [x] **PW-4** JWT `cv` claim + `CredentialsVersionCache` + filter rejects revoked tokens (`TOKEN_REVOKED`) + tests — after PW-1
- [x] **PW-5** FE: `PasswordInput`, password login modal (🔑 icon in header, forgot → link), Settings set/change/remove, global `TOKEN_REVOKED` logout + tests — contract-first
- [x] **PW-6** Prod E2E verified by owner 2026-10-09; `docs/tracking.md` + mkdocs nav updated
  - Follow-up: `3de7a4a` login response now carries `role` (admin menu appeared only after F5)
  - [ ] `/security-auditor` review of the diff — not run yet

---

## Feature: Admin Video Import (YouTube / Facebook → Drive)

**PRD:** `docs/prd/PRD-admin-video-import.md` (draft) · **ADR:** `docs/adr/0019-admin-video-import.md` (Proposed) · **Plan:** `docs/plans/plan-admin-video-import.md`

- [ ] **VI-0** PoC: upload a test file into `FOLDER_ID` with the Service Account → choose SA vs OAuth uploader, accept ADR-0019 ⚠️ gate
- [ ] **VI-1** Migration `video_import_jobs`, entity/repo (`claimNextDue` SKIP LOCKED), `AppProperties.videoImport`, `ImportUrlValidator`, `TitleSanitizer` + tests
- [ ] **VI-2** `YtDlpClient` (metadata, download, progress parse, timeouts, error codes) + `DriveUploader` (resumable, progress) + tests — after VI-0, VI-1
- [ ] **VI-3** `VideoImportService`, `VideoImportWorker` (concurrency 1, wake-up, interrupted reset), `/admin/video-imports` API (history kept forever, bulk max 20) + tests — after VI-1, VI-2
- [ ] **VI-4** FE tab "Import Video": bulk form, now/schedule, preview title, job table with polling progress, actions + tests — contract-first
- [ ] **VI-5** Host ops (yt-dlp -U cron, work dir), prod E2E, security review, tracking + docs — after all

