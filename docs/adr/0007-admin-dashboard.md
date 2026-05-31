# ADR-0007: Admin Dashboard — Content and Account Management

## Metadata
**Status:** Accepted
**Date:** 2026-04-30
**Deciders:** nguyenhuuca
**Related PRD:** N/A
**Tech Strategy Alignment:**
- [x] Decision follows Golden Path in `.claude/rules/tech-strategy.md`
**Domain Tags:** security, api, frontend
**Supersedes:** N/A
**Superseded By:** N/A

---

## Context

The application needs an admin-only dashboard for video moderation and user account management. At the time of this decision:

- The `User` entity has no role field — all users are treated equally
- The JWT payload contains only `id` and `email`; the `GrantedAuthority` list is always empty
- The `/admin/**` route rule requires only `authenticated()` — no role check exists
- `VideoSource` has an `isHide` boolean (user privacy) but no moderation state
- No admin controller, service, or frontend view exists

Six sub-decisions were required to implement this feature correctly.

---

## Decision Drivers

- Admin routes must be inaccessible to non-admin users at both the HTTP filter layer and the controller layer
- The role model must be extensible beyond a two-value boolean
- Video moderation state must be orthogonal to user-controlled privacy (`isHide`)
- The frontend must not rely on stale data to determine admin status

---

## Considered Options

### Option A: Single-layer authorization (URL-level only)
Enforce `ROLE_ADMIN` only in `WebSecurityConfig` via `.requestMatchers("/admin/**").hasRole("ADMIN")`.

| Pros | Cons |
|------|------|
| Simple, one place to maintain | Authorization intent invisible in controller code |
| | Breaks silently if URL pattern changes or is refactored |
| | Controller code must be reached before intent is visible |

### Option B: Dual-layer defense-in-depth (chosen)
Enforce `ROLE_ADMIN` at both URL-level (`WebSecurityConfig`) AND method-level (`@PreAuthorize` on `AdminController`).

| Pros | Cons |
|------|------|
| Non-admins rejected before controller is reached | Slightly more boilerplate |
| Authorization intent explicit in controller | |
| Survives URL refactors — method annotation travels with class | |

---

### Option A: Boolean `isAdmin` column on User
Add a single boolean column to the `users` table.

| Pros | Cons |
|------|------|
| Minimal schema change | Cannot extend to future roles (MODERATOR) without schema change |
| | Boolean semantics inappropriate for a multi-value concept |

### Option B: `role VARCHAR(20)` with Java enum (chosen)
Add `role VARCHAR(20) NOT NULL DEFAULT 'USER'` mapped to `UserRole { USER, ADMIN }`.

| Pros | Cons |
|------|------|
| Extensible to future roles without schema change | Enum changes require migration |
| Type-safe in Java layer | |
| Consistent with existing string-enum pattern in codebase | |

A separate `user_roles` join table was also considered and rejected as over-engineered for a two-role system.

---

### Option A: Parse JWT in browser to detect admin status
Decode the JWT's middle segment in JavaScript to read the `role` claim.

| Pros | Cons |
|------|------|
| No extra HTTP call | Role claim is stale if admin rights change while user is logged in |
| | Requires client-side JWT parsing logic |

### Option B: Expose `isAdmin` via `GET /user/me` (chosen)
Return `isAdmin: true/false` in the `UserDetailDto` from the existing `/user/me` endpoint already called on app mount.

| Pros | Cons |
|------|------|
| Always reflects current server-side role | Requires one extra field in the DTO |
| No extra HTTP call (endpoint already used) | |
| Role changes take effect on next page load | |

---

## Decision Outcome
**Chosen Option:** Option B across all three contested sub-decisions, plus three uncontested sub-decisions.

**Rationale:**
- **Dual-layer authorization** (D-2): Defense-in-depth ensures no path to admin functionality exists without an explicit role check. URL-level rejects at the filter chain; method-level makes intent self-documenting.
- **`role` VARCHAR enum** (D-1): A `UserRole` enum is extensible and consistent with other string-enum fields in the schema. A boolean cannot grow to support future roles without a migration.
- **`/user/me` for admin detection** (D-5): Avoids stale role data in the browser. The endpoint is already called on mount, so no additional request is needed.

Uncontested sub-decisions rationale:
- **Video moderation status** (D-3): A `status` column (`PUBLISHED`/`PENDING`/`FLAGGED`) is orthogonal to `isHide`. The `isHide` field is user-controlled privacy; `status` is admin-controlled moderation. Multiple boolean columns (`isPending`, `isFlagged`) were rejected because they permit invalid combinations and are not extensible.
- **JWT role claim** (D-4): Including `role` in the JWT payload allows Spring Security's `GrantedAuthority` mechanism to work correctly. Without it, `@PreAuthorize("hasRole('ADMIN')")` always fails because the authority list is empty.
- **Offset pagination** (D-6): Spring `Pageable` (offset-based) is sufficient — admin data is not high-velocity and admins need random page access. Cursor-based pagination would prevent jumping to arbitrary pages.

---

## Consequences
**Positive:**
- Defense-in-depth authorization — two independent enforcement points for admin access
- Extensible role model supports future roles (e.g., MODERATOR) without schema redesign
- Clean separation between user privacy state (`isHide`) and admin moderation state (`status`)
- JWT now carries a real `GrantedAuthority`, enabling method-level security annotations throughout the codebase

**Negative:**
- JWT role claim is stale until the user re-authenticates after a role change
- Admin-facing pagination uses offset — performance degrades at very high page numbers (not a current concern)

**Risks:**
- **Stale JWT after role change:** Mitigate with short JWT TTL or forced re-login after role promotion/demotion
- **Admin deletes own account:** Service layer must guard against this with a check comparing `targetId` vs authenticated `userId`
- **Mass delete:** Current implementation is hard-delete; consider soft-delete (`status = DELETED`) in a future iteration

---

## Validation
- [ ] Tech Strategy alignment confirmed
- [ ] Related plan document created: [docs/plans/plan-admin-dashboard.md](../plans/plan-admin-dashboard.md)

---

## Links
- Plan document: `docs/plans/plan-admin-dashboard.md`
- Spring Security `@PreAuthorize`: https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html

---

## Changelog
| Date | Author | Change |
|------|--------|--------|
| 2026-04-30 | nguyenhuuca | Initial draft |
| 2026-05-31 | nguyenhuuca | Restructured to new ADR template; technical details moved to plan doc |
