# ADR: Admin Dashboard — Content & Account Management

**Status:** Accepted  
**Date:** 2026-04-30  
**Scope:** Full-stack (Spring Boot 3.x backend + React 19 frontend)

---

## Context

The application needs an admin-only dashboard to manage video content and user accounts. Requirements:
- CRUD on `VideoSource` records (list, change status, delete)
- CRUD on `User` accounts (list, change role, delete)
- Status model: PUBLISHED / PENDING / FLAGGED per video
- Only users with ADMIN role may access any `/admin/**` endpoint or UI
- Design follows the "KINETIC NOIR" Stitch screen (`stitch_admin.png`)

Current state gaps:
- `User` entity has no `role` field
- JWT contains only `id` + `email`; `GrantedAuthority` list is always empty
- `/admin/**` rule requires `authenticated()` but no role check
- `VideoSource` has `isHide` (boolean) but no moderation status
- No admin controller or service exists

---

## Decisions

### D-1 · Role model on User

**Decision:** Add `role VARCHAR(20) NOT NULL DEFAULT 'USER'` column to `users` table.  
Map to a Java enum `UserRole { USER, ADMIN }` stored as `@Enumerated(EnumType.STRING)`.

**Rejected alternatives:**
| Option | Why rejected |
|--------|-------------|
| Boolean `isAdmin` column | Cannot extend to future roles (MODERATOR) without schema change |
| Separate `user_roles` join table | Over-engineered for a two-role system |

---

### D-2 · Authorization — defense in depth

**Decision:** Enforce `ROLE_ADMIN` at **two layers**:

1. **URL-level** (`WebSecurityConfig`):
   ```
   .requestMatchers("/admin/**").hasRole("ADMIN")
   ```
2. **Method-level** (`AdminController`):
   ```java
   @PreAuthorize("hasRole('ADMIN')")   // class-level annotation
   ```

`@EnableMethodSecurity` is already active. URL-level rejects non-admins before the controller is reached. Method-level makes intent explicit and survives URL refactors.

**Rejected alternatives:**
| Option | Why rejected |
|--------|-------------|
| URL-level only | Intent invisible in controller; breaks if URL changes |
| Method-level only | No fast-fail at filter chain; controller code must be reached first |

---

### D-3 · Video moderation status

**Decision:** Add `status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED'` to `video_sources`.  
Map to enum `VideoStatus { PUBLISHED, PENDING, FLAGGED }`.

**Keep `isHide` as-is** — it controls user-facing privacy (private videos), which is orthogonal to admin moderation state.

| Field | Owner | Purpose |
|-------|-------|---------|
| `isHide` | User | Hides video from public feed (private upload) |
| `status` | Admin | Moderation state: published / pending review / flagged |

**Rejected alternative:** Multiple boolean columns (`isPending`, `isFlagged`) create invalid combinations and are not extensible.

---

### D-4 · JWT role claim

**Decision:** Include `role` claim in JWT payload. Update `JwtProvider.generatePayload()` and `convertValue()`. Update `JWTAuthenticationFilter` to build `GrantedAuthority` list from the claim.

```
JWT payload (after change):
{ "id": 42, "email": "user@x.com", "role": "ADMIN" }
```

Filter change — replace `Collections.emptyList()`:
```java
List<GrantedAuthority> authorities = List.of(
    new SimpleGrantedAuthority("ROLE_" + user.getRole())
);
new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities)
```

`UserDetailDto` gains a `role` field (String). `JwtGenerationDto.payload` carries it.

---

### D-5 · Frontend: how to know if current user is admin

**Decision:** Expose `isAdmin` in `UserDetailDto` returned by `GET /user/me`.  
`AuthContext` reads `user.isAdmin` from the `/user/me` response (already called on mount).

**Rejected alternative:** Parse JWT in the browser (base64-decode middle segment).  
Risk: stale role if admin rights change while user is logged in. `/user/me` is always fresh.

---

### D-6 · Pagination strategy

**Decision:** Use Spring `Pageable` (offset pagination) via `@PageableDefault`.  
Returns Spring `Page<T>` which includes `content`, `totalElements`, `totalPages` — feeds the "Reviewing 1,482 total assets" header and pagination controls directly.

Cursor-based pagination is not needed — admin data is not high-velocity, and admins need random page access.

---

## API Contract

All endpoints under `/admin/**` require `Authorization: Bearer <admin-jwt>`.

### Videos

| Method | Path | Description |
|--------|------|-------------|
| GET | `/admin/videos?page=0&size=20&status=` | Paginated video list, optional status filter |
| PATCH | `/admin/videos/{id}/status` | Change moderation status |
| DELETE | `/admin/videos/{id}` | Delete video and source record |

**AdminVideoDto:**
```json
{
  "id": 1,
  "title": "NEON_DREAMSCAPE_V2",
  "thumbnailPath": "https://...",
  "creatorEmail": "niko@example.com",
  "status": "PUBLISHED",
  "viewCount": 138400,
  "createdAt": "2025-06-01T00:00:00Z"
}
```

**PATCH body:**
```json
{ "status": "FLAGGED" }
```

### Accounts

| Method | Path | Description |
|--------|------|-------------|
| GET | `/admin/accounts?page=0&size=20` | Paginated user list |
| PATCH | `/admin/accounts/{id}/role` | Promote / demote user |
| DELETE | `/admin/accounts/{id}` | Delete user account |

**AdminAccountDto:**
```json
{
  "id": 42,
  "email": "user@example.com",
  "role": "USER",
  "mfaEnabled": true,
  "createdAt": "2025-01-01T00:00:00Z"
}
```

**Safety constraint:** An admin cannot change their own role or delete their own account.  
Service layer validates: `if (targetId.equals(currentUserId)) throw ForbiddenException`.

### Stats

| Method | Path | Description |
|--------|------|-------------|
| GET | `/admin/stats` | Dashboard summary counts |

**AdminStatsDto:**
```json
{
  "totalVideos": 1482,
  "totalUsers": 320,
  "pendingCount": 12,
  "flaggedCount": 24
}
```

---

## Database Migrations

Two new SQL files following the existing naming convention (`YYYYMMDDnnnn-description.sql`):

**`202504300001-add-user-role.sql`**
```sql
ALTER TABLE users
ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
```

**`202504300002-add-video-status.sql`**
```sql
ALTER TABLE video_sources
ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';
```

---

## Backend Component Map

```
filter/
  JwtProvider.java              ← add role to generatePayload() + convertValue()
  JWTAuthenticationFilter.java  ← build GrantedAuthority list from role claim
  WebSecurityConfig.java        ← change /admin/** to hasRole("ADMIN")

entity/
  User.java                     ← add UserRole enum field
  VideoSource.java              ← add VideoStatus enum field

enums/
  UserRole.java                 ← NEW: USER, ADMIN
  VideoStatus.java              ← NEW: PUBLISHED, PENDING, FLAGGED

dto/
  UserDetailDto.java            ← add role String field
  AdminVideoDto.java            ← NEW
  AdminAccountDto.java          ← NEW
  AdminStatsDto.java            ← NEW

service/
  AdminVideoService.java        ← NEW interface
  AdminAccountService.java      ← NEW interface
  impl/
    AdminVideoServiceImpl.java  ← NEW
    AdminAccountServiceImpl.java← NEW

web/
  AdminController.java          ← NEW (@PreAuthorize("hasRole('ADMIN')"))
```

---

## Frontend Component Map

```
src/
  contexts/
    AuthContext.jsx             ← expose user.isAdmin from /user/me response
  api/
    admin.js                    ← NEW: getVideos, updateVideoStatus, deleteVideo,
                                        getAccounts, updateAccountRole, deleteAccount,
                                        getStats
  hooks/
    useAdmin.js                 ← NEW: React Query hooks wrapping admin.js
  components/
    layout/
      AppShell.jsx              ← add Admin to SIDE_NAV with requiresAdmin guard
    admin/
      AdminView.jsx             ← NEW: tabs container + stats cards
      AdminVideoTable.jsx       ← NEW: table with StatusBadge + actions
      AdminAccountTable.jsx     ← NEW: table with RoleBadge + actions
  styles/
    index.css                   ← add .admin-* classes (dark surface, badges)
```

---

## Admin Nav Guard (Frontend)

Two layers — same defense-in-depth as backend:

```js
// 1. Nav item hidden unless admin
const visibleNav = SIDE_NAV.filter(
  item => !item.requiresAdmin || user?.isAdmin
)

// 2. Content render guard
{activeNav === 'admin' && user?.isAdmin && <AdminView />}
```

---

## Status Badge Colors (from design)

| Status | Color | Hex |
|--------|-------|-----|
| PUBLISHED | Green | `#00c853` |
| PENDING | Yellow | `#ffd600` |
| FLAGGED | Red | `#ff1744` |
| ADMIN role badge | Cyan | `var(--accent-cyan)` |

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| JWT role claim stale after role change | Short JWT TTL (30 days → consider shorter for admin sessions); force re-login after role change |
| Admin deletes own account | Service-layer guard: compare targetId vs authenticated userId |
| Mass delete wipes data | DELETE is hard-delete; consider soft-delete (status = DELETED) in future |
| CORS allows admin endpoints from other origins | Existing CORS config already restricts to `canh-labs.com` + localhost |

---

## Implementation Order

```
1. Migrations (DB schema)
2. Enums + Entity changes (User.role, VideoSource.status)
3. JWT role claim (JwtProvider + JWTAuthenticationFilter + UserDetailDto)
4. WebSecurityConfig hasRole gate
5. AdminVideoService + AdminAccountService + AdminController
6. Frontend: AuthContext isAdmin
7. Frontend: AppShell admin nav item
8. Frontend: api/admin.js + useAdmin.js hooks
9. Frontend: AdminView + AdminVideoTable + AdminAccountTable
10. CSS for admin components
```
