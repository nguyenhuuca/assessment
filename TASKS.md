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

- [x] **VI-0** PoC 2026-10-10: SA upload → 403 `storageQuotaExceeded` ⇒ OAuth uploader; owner created OAuth client + refresh token, env set on server
- [x] **VI-1** Migration `video_import_jobs`, entity/repo (`claimNextDue` SKIP LOCKED), `AppProperties.videoImport`, `ImportUrlValidator`, `TitleSanitizer` + tests
- [x] **VI-2** `YtDlpClient` (metadata, download, progress parse, timeouts, error codes) + `DriveUploader` (resumable, progress) + tests — after VI-0, VI-1
- [x] **VI-3** `VideoImportService`, `VideoImportWorker` (concurrency 1, wake-up, interrupted reset), `/admin/video-imports` API (history kept forever, bulk max 20) + tests — after VI-1, VI-2
- [x] **VI-4** FE tab "Import Video": bulk form, now/schedule, preview title, job table with polling progress, actions + tests — contract-first
  - Security review (2026-10-10): no Critical/High; fixed Medium (child env allowlist, dir-size cap + `!is_live`, preview rate limit + metadata semaphore) and Low (kill descendants, log sanitizing, `--no-cache-dir`, `--socket-timeout`)
  - Not verified locally: native SQL on Postgres, real yt-dlp/Drive run → covered by VI-5 E2E
- [x] **VI-5** Host ops, prod E2E (verified 2026-10-11), security review (2026-10-10), tracking + docs; ADR-0019 Accepted


---

## Feature: Admin Drive Force Sync (Drive ↔ local ↔ DB; ADMIN views, SUPER_ADMIN syncs)

**Plan:** `docs/plans/plan-admin-drive-force-sync.md` (Approved 2026-10-11)

- [x] **FS-0** Prod check 2026-10-11: 0 duplicate `(source_type, source_id)` rows in `video_sources` (run by owner) → unique index is safe
- [x] **FS-1** Unique index migration, `upsertDriveSource`, `DriveFileLocks`, `DriveVideoDownloader` (temp `.part` + atomic move + cache eviction; cron reuses it) + tests — after FS-0
- [x] **FS-2** `DriveSyncService` (folder listing + states, single in-memory run, folder-membership check, per-file progress) + tests — after FS-1
- [x] **FS-3** `AdminDriveSyncController` `/admin/drive-sync` (GETs `hasRole('ADMIN')`, `POST /runs` `hasRole('SUPER_ADMIN')`) + permission tests — after FS-2
- [x] **FS-4** FE tab `DRIVE_SYNC` (all admins view; select/FORCE_SYNC SA only): table + states, select, FORCE_SYNC, polling progress + tests — contract-first
  - Fix found by test: `@HasPermission` is a meta `@PreAuthorize`, so the SA POST inlines the permission check into one `@PreAuthorize` (two would be a 500)
- [ ] **FS-5** Prod E2E, `/security-auditor`, tracking + docs — after FS-3, FS-4

---

## Feature: Drive Sync Time Filter (relative + specific time on the DRIVE_SYNC tab)

**Plan:** `docs/plans/plan-drive-sync-time-filter.md` (Approved 2026-10-18) · server-side range on Drive `createdTime`, default last 12 h, orphans only under "All"

- [x] **BE-1** `GET /files?from&to` (ISO instants, 400 on from > to), Drive `q` with `createdTime`, filtered DB lookup, `orphansChecked` flag + tests — `cc7f8f2b`
- [x] **FE-1** `utils/timeRange.js` (`toRangeParams`, presets 1h/12h/24h/7d/30d, VN-time custom range), `getDriveSyncFiles(params)`, `useDriveSyncFiles(filter)` + tests — contract-first, parallel to BE-1 — `ad0adc82`
- [x] **FE-2** `DriveTimeFilter` control + panel wiring (default 12 h, refetch per change, clear selection, orphan note) + vi/en strings + tests — after FE-1; push only after BE-1 is deployed — `31f01e86`
- [ ] **QA-1** Local check on the real folder + prod smoke (ADMIN, SUPER_ADMIN), tracking + docs — after BE-1, FE-2

---

## Feature: Library Collections (replaces Bookmarks)

**ADR:** `docs/adr/0012-bookmark-feature-design.md` (amended 2026-10-13) · **Plan:** `docs/plans/plan-library-collections.md`

- [x] **BE-1** Migration, `VideoCollection` / `VideoCollectionItem` entities + repos — `50ade4e`
- [x] **BE-2** DTOs + `VideoCollectionService` (lazy default "Xem sau", duplicate/cap 409, admin public collections) — `50ade4e`
- [x] **BE-3** `VideoCollectionController`, optional-auth public GETs, WebMvc tests — `50ade4e`
- [x] **FE-1** `api/collections.js` + `hooks/useCollections.js` — `50ade4e`
- [x] **FE-2** `AddToCollectionModal` + "Lưu" button in `VideoSwiper` — `50ade4e`
- [x] **FE-3** `LibraryView` (public + mine, detail, CRUD, `?l=` deep link) — `50ade4e`
  - Follow-ups: `d27cdcc` adversarial review fixes; `e4018c6` search/add inside a collection; `2dc702c` "Phát" plays the whole list; `60428a0` dropped the legacy "Video riêng tư" section; `b677d60` titles on tiles
- [ ] **QA-1** Local E2E + prod smoke (guest opens `?l=` link, hidden video disappears) — not recorded in the plan

---

## Feature: Collection Reorder (drag & drop)

**Plan:** `docs/plans/plan-collection-reorder.md` · **ADR:** `docs/adr/0012-bookmark-feature-design.md` (no new ADR)

- [x] **BE-1** Migration `position` (backfill newest-first, new index) + repo ordering / add-at-top / `renumber` — `6a0a5849`; migration + `renumber` verified on prod Postgres (2026-10-18)
- [x] **BE-2** `moveVideo` service + `MoveVideoRequest` + `PATCH /collections/{id}/videos/{videoId}` + i18n errors — `33f82e64`
- [x] **FE-1** `collectionsApi.moveVideo` + `useMoveCollectionItem` (optimistic + rollback) + `moveAfterId` util — `9c53b409`
- [x] **FE-2** `@dnd-kit` "Sắp xếp" mode in `CollectionDetail` (pointer/touch/keyboard) + tech-strategy entry — `d0be64ee` (bundle +17.8 kB gz); push after BE-2 is deployed
- [x] **QA-1** Prod smoke by owner: health, `GET /collections/public`, drag reorder on desktop + mobile, reload keeps order, "Phát" order (2026-10-18)

---

## Feature: i18n (Vietnamese + English)

**PRD:** `docs/prd/PRD-i18n.md` · **ADR:** `docs/adr/0020-i18n-vietnamese-english.md` (Accepted) · **Plan:** `docs/plans/plan-i18n.md`

- [x] **FE-1** Foundation: i18next, `VI | EN` switcher, `<html lang>`, `Accept-Language`, parity test — `e445a03`
- [x] **FE-2** Core UI: AppShell, video, explore, modals, common — `ae99e44`
- [x] **FE-3** Library, comments, notifications — `ae99e44`
- [x] **FE-4** Auth + Settings, language row synced with `user_settings.locale` — `8d7d97e`, `9155ec3`
- [x] **FE-5** Admin — `ae99e44`
- [x] **BE-1** `MessageSource` + `LocaleResolver`, `ResultErrorInfo.code`, localized errors — `9bb06bd`
- [x] **BE-2** `user_settings.locale` migration + `PATCH /user/settings` validation — `9bb06bd`
- [x] **BE-3** Localized emails (magic link, password notice, digest) — `749617c`
- [x] **QA-1** E2E + prod smoke in both languages (emails, no mixed chrome) — verified by owner, recorded 2026-10-18

---

## Feature: Video Suggestions (+ button) and History → My suggestions

**PRD:** `docs/prd/PRD-video-suggestions.md` · **Plan:** `docs/plans/plan-video-suggestions.md`

- [x] **BE-1** Migration, entity, enums, repo, `VideoSuggestionService` (submit, mine, list, approve, reject) — `c11041a`
- [x] **BE-2** User + admin controllers, permissions, rate limits — `c11041a`
- [x] **BE-3** Notifications (approved / rejected / live), `SuggestionNotificationListener` — `c11041a`
- [x] **FE-1** `SuggestVideoModal` on the + button, removed legacy `ShareModal` — `ed23c5e`
- [x] **FE-2** Admin `SUGGESTIONS` tab (approve with title override, reject with reason) — `ed23c5e`
- [x] **FE-3** Notification rendering for the three types — `ed23c5e`
- [x] **FE-4** History page with "My suggestions" — `ed23c5e`
  - Follow-ups: `d01ced7` History redesign (Stitch); `1843c44` real thumbnail for suggestions that are live
- [ ] **QA-1** Local E2E + prod smoke (suggest → approve → ingest → 3 notifications) — not recorded in the plan

---

## Feature: Watch History v2

**PRD:** `docs/prd/PRD-watch-history-v2.md` · **ADR:** `docs/adr/0021-watch-history.md` (Accepted) · **Plan:** `docs/plans/plan-watch-history-v2.md` (Done)

- [x] **BE-1** `watch_history` migration, repository (upsert, trim, progress, page, summary), `WatchHistoryService` — `0a2a365`
- [x] **BE-2** `WatchHistoryController` (5 endpoints, rate limits) + hook in `VideoViewController.record` — `0a2a365`
- [x] **BE-3** Account deletion clears history — `0a2a365`
- [x] **FE-1** `api/watchHistory.js`, `keepalive`, `hooks/useWatchHistory.js` — `4c46791`
- [x] **FE-2** `VideoPlayer` progress + `startAt` resume via `AppShell.playVideo` — `4c46791`
- [x] **FE-3** History tabs + `WatchHistorySection` (search, pause, clear-all, day groups, paging) — `4c46791`
- [x] **FE-4** `WatchedBadge` on feed + Explore — `4c46791`
  - Follow-ups: `e7b8ed1` History in the mobile bottom nav; `3c00d84` icon-only nav; `d79d68f` settings menu in the bottom nav
- [x] **QA-1** Prod smoke, logged in — verified by owner 2026-10-18 (`14eeb7a`)
