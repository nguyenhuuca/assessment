# Plan: Admin Dashboard — Content & Account Management

**ADR:** `artifacts/adr_admin_dashboard.md`  
**Design:** `D:\DO\assessment\stitch_admin.png` ("KINETIC NOIR" dark theme)  
**Estimated effort:** 5–7 days

---

## Phase 1 — DB & Entity Foundation (Day 1)

### BE-1: DB migrations (2 SQL files)
File: `db/changelog/sql/202504300001-add-user-role.sql`
```sql
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
```
File: `db/changelog/sql/202504300002-add-video-status.sql`
```sql
ALTER TABLE video_sources ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';
```

### BE-2: New enums
- `enums/UserRole.java` — `USER, ADMIN`
- `enums/VideoStatus.java` — `PUBLISHED, PENDING, FLAGGED`

### BE-3: Entity changes
- `entity/User.java` — add `@Enumerated(EnumType.STRING) UserRole role = UserRole.USER`
- `entity/VideoSource.java` — add `@Enumerated(EnumType.STRING) VideoStatus status = VideoStatus.PUBLISHED`

---

## Phase 2 — Auth: JWT Role Claim (Day 1–2)

### BE-4: UserDetailDto
- Add `String role` field

### BE-5: JwtProvider
- `generatePayload()` — add `payload.put("role", request.getRole())`
- `convertValue()` — read role claim back into `UserDetailDto`

### BE-6: JWTAuthenticationFilter
- Replace `Collections.emptyList()` with:
  ```java
  List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
  ```

### BE-7: WebSecurityConfig
- Change:
  ```java
  .requestMatchers("/admin/**").authenticated()
  ```
  To:
  ```java
  .requestMatchers("/admin/**").hasRole("ADMIN")
  ```

---

## Phase 3 — Backend Admin API (Day 2–3)

### BE-8: DTOs
- `dto/AdminVideoDto.java` — `id, title, thumbnailPath, creatorEmail, status, viewCount, createdAt`
- `dto/AdminAccountDto.java` — `id, email, role, mfaEnabled, createdAt`
- `dto/AdminStatsDto.java` — `totalVideos, totalUsers, pendingCount, flaggedCount`

### BE-9: AdminVideoService
Interface + impl:
- `Page<AdminVideoDto> getVideos(Pageable pageable, VideoStatus status)`
- `void updateStatus(Long id, VideoStatus status)`
- `void deleteVideo(Long id)`

### BE-10: AdminAccountService
Interface + impl:
- `Page<AdminAccountDto> getAccounts(Pageable pageable)`
- `void updateRole(Long targetId, Long currentUserId, UserRole role)` — guard: cannot change own role
- `void deleteAccount(Long targetId, Long currentUserId)` — guard: cannot delete self

### BE-11: AdminController
```
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")   ← class-level (defense in depth)
```

Endpoints:
| Method | Path | Service call |
|--------|------|-------------|
| GET | `/admin/videos` | `getVideos(pageable, status)` |
| PATCH | `/admin/videos/{id}/status` | `updateStatus(id, status)` |
| DELETE | `/admin/videos/{id}` | `deleteVideo(id)` |
| GET | `/admin/accounts` | `getAccounts(pageable)` |
| PATCH | `/admin/accounts/{id}/role` | `updateRole(id, currentUserId, role)` |
| DELETE | `/admin/accounts/{id}` | `deleteAccount(id, currentUserId)` |
| GET | `/admin/stats` | `getStats()` |

---

## Phase 4 — Frontend (Day 3–5)

### FE-1: AuthContext — expose isAdmin
- `contexts/AuthContext.jsx` — read `isAdmin` from `/user/me` response (already called on mount)
- Set `user.isAdmin = data.role === 'ADMIN'`

### FE-2: AppShell — admin nav item + guard
- Add to `SIDE_NAV`:
  ```js
  { key: 'admin', icon: 'admin_panel_settings', label: 'Admin', requiresAdmin: true }
  ```
- Filter: `SIDE_NAV.filter(item => !item.requiresAdmin || user?.isAdmin)`
- Render guard in main content:
  ```jsx
  {activeNav === 'admin' && user?.isAdmin && <AdminView />}
  ```

### FE-3: api/admin.js (new file)
```js
export const getVideos    = (params) => api.get('/admin/videos', { params })
export const updateStatus = (id, status) => api.patch(`/admin/videos/${id}/status`, { status })
export const deleteVideo  = (id) => api.delete(`/admin/videos/${id}`)
export const getAccounts  = (params) => api.get('/admin/accounts', { params })
export const updateRole   = (id, role) => api.patch(`/admin/accounts/${id}/role`, { role })
export const deleteAccount = (id) => api.delete(`/admin/accounts/${id}`)
export const getStats     = () => api.get('/admin/stats')
```

### FE-4: useAdmin.js (new hooks file)
- `useAdminVideos(page, status)` → `useQuery`
- `useAdminAccounts(page)` → `useQuery`
- `useAdminStats()` → `useQuery`
- `useUpdateVideoStatus()` → `useMutation` + invalidate videos
- `useDeleteVideo()` → `useMutation` + invalidate videos
- `useUpdateAccountRole()` → `useMutation` + invalidate accounts
- `useDeleteAccount()` → `useMutation` + invalidate accounts

### FE-5: AdminView.jsx
- Header: `CONTENT_MANAGER` title + total count from stats
- Tabs: `VIDEO_VAULT` | `USER_ACCOUNTS` | `FLAGGED_LOGS`
- Stat cards row: Storage · User Growth · Security Alerts (flaggedCount)
- `UPLOAD NEW` button → existing `ShareModal`

### FE-6: AdminVideoTable.jsx
- Columns: thumbnail+title | creator email | STATUS badge | view count | actions
- Status badge colors: PUBLISHED `#00c853` | PENDING `#ffd600` | FLAGGED `#ff1744`
- Actions: status change dropdown + delete (reuse `DeleteConfirmModal`)
- Pagination controls

### FE-7: AdminAccountTable.jsx
- Columns: email | role badge | MFA | joined date | actions
- Role badge: ADMIN → `var(--accent-cyan)` | USER → muted
- Actions: promote/demote role, delete (with confirm)
- Guard: disable action buttons for own account row

### FE-8: CSS in index.css
New `.admin-*` classes:
- `.admin-table` — dark surface, monospace-style header cells
- `.admin-badge.published/pending/flagged/admin/user` — status/role color badges
- `.admin-stat-card` — bottom stat cards (dark surface, neon label)
- `.admin-action-btn` — small icon buttons in table rows

---

## Files Changed / Created

### Backend — New
| File | Purpose |
|------|---------|
| `db/changelog/sql/202504300001-add-user-role.sql` | DB migration |
| `db/changelog/sql/202504300002-add-video-status.sql` | DB migration |
| `enums/UserRole.java` | USER, ADMIN |
| `enums/VideoStatus.java` | PUBLISHED, PENDING, FLAGGED |
| `dto/AdminVideoDto.java` | Admin video response |
| `dto/AdminAccountDto.java` | Admin account response |
| `dto/AdminStatsDto.java` | Dashboard stats |
| `service/AdminVideoService.java` | Interface |
| `service/AdminAccountService.java` | Interface |
| `service/impl/AdminVideoServiceImpl.java` | Implementation |
| `service/impl/AdminAccountServiceImpl.java` | Implementation |
| `web/AdminController.java` | REST controller |

### Backend — Modified
| File | Change |
|------|--------|
| `entity/User.java` | Add `role` field |
| `entity/VideoSource.java` | Add `status` field |
| `dto/UserDetailDto.java` | Add `role` field |
| `filter/JwtProvider.java` | Add role to payload + convertValue |
| `filter/JWTAuthenticationFilter.java` | Build GrantedAuthority from role |
| `filter/WebSecurityConfig.java` | `/admin/**` → `hasRole("ADMIN")` |

### Frontend — New
| File | Purpose |
|------|---------|
| `src/api/admin.js` | Admin API calls |
| `src/hooks/useAdmin.js` | React Query hooks |
| `src/components/admin/AdminView.jsx` | Main container |
| `src/components/admin/AdminVideoTable.jsx` | Video CRUD table |
| `src/components/admin/AdminAccountTable.jsx` | Account CRUD table |

### Frontend — Modified
| File | Change |
|------|--------|
| `src/contexts/AuthContext.jsx` | Expose `isAdmin` |
| `src/components/layout/AppShell.jsx` | Admin nav item + render guard |
| `src/styles/index.css` | `.admin-*` CSS classes |

---

## Dependency Order

```
BE-1 migrations
  └── BE-2/3 enums + entities
        └── BE-4/5/6 JWT role claim
              └── BE-7 security gate
                    └── BE-8 DTOs
                          └── BE-9/10 services
                                └── BE-11 AdminController

FE-1 AuthContext isAdmin        ← needs BE-4 (role in /user/me)
FE-2 AppShell nav               ← needs FE-1
FE-3/4 api + hooks              ← needs BE-11
FE-5/6/7 UI components          ← needs FE-3/4
FE-8 CSS                        ← parallel with FE-5/6/7
```
