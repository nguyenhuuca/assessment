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
