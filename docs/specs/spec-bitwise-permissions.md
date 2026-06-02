# Feature Specification: Bitwise Permission System

## Metadata

**Status:** Implemented
**Author:** nguyenhuuca
**Date:** 2026-06-02
**Related PRD:** N/A
**Related ADR:** [ADR-0015: Bitwise Permission System](../adr/0015-bitwise-permission-system.md)
**Related Plan:** [plan-permissions-bitwise](../plans/plan-permissions-bitwise.md)

> Documented retroactively (AS-BUILT) for tracking. Describes the behavior actually shipped.

---

## Overview

The system provides fine-grained, method-level authorization on top of the existing role guard. Each capability is a single bit in a 32-bit mask; a user's granted capabilities are stored as one `int` column. Endpoints declare a required capability with `@HasPermission(perm = Permission.X)`, and access is granted only when the user's bitmask contains that bit. Enforcement is gated by a feature flag and is permissive (no-op) by default.

---

## Business Rules

### Rule 1
A `Permission` is one bit: `READ=1`, `WRITE=2`, `EXEC=4`, `DELETE=8`, `ADMIN=16` (`1 << n`). The `permissions` table seed (`code`, `bit_value`) must match the `Permission` enum exactly.

### Rule 2
A user's granted permissions are a single `int` bitmask stored in `users.permissions`, defaulting to `0` (no permissions).

### Rule 3
A single-permission check (`hasPermission`) grants access when `(userBits & perm.bit) != 0`.

### Rule 4
When the feature flag `permission_enforcement_enabled` is OFF (the default), all permission checks return `true` — only the class-level role guard (`hasRole('ADMIN')`) applies. The permission layer is additive and never weakens an existing guard.

### Rule 5
If the Harness FF backend is unreachable or the API key is blank, the flag resolves to its default (`false`) — the application continues to function with role-only authorization.

---

## Functional Requirements

### FR-1: Declarative method guard
The system must provide a `@HasPermission(perm = Permission.X)` annotation usable on a controller method or type. It is a meta-annotation over `@PreAuthorize` that calls `permissionServiceImpl.hasPermission(principal.permissions, perm)`.

### FR-2: Permission resolution in SpEL
`authentication.principal.permissions` must resolve to the user's `int` bitmask, and the bare enum identifier (e.g. `ADMIN`) in the expanded template expression must resolve to the corresponding `Permission` constant.

### FR-3: Single-permission check
`PermissionService.hasPermission(int userBits, Permission perm)` must return `true` iff the flag is enforced AND `(userBits & perm.getBit()) != 0`. When the flag is off, it returns `true` unconditionally.

### FR-4: Multi-permission checks (available, not yet wired to annotation)
`hasAllPermissions(int, Permission[])` (AND) and `hasAnyPermission(int, Permission[])` (OR) must be available on `PermissionService` for future use. Both short-circuit to `true` when the flag is off.

### FR-5: Principal carries permissions
`UserServiceImpl.loadUserByUsername()` must return an `AppUserDetails` that wraps the `User` entity and exposes `int getPermissions()` and `Long getId()`.

### FR-6: Feature-flag gate
`FeatureFlagService.isEnabled(String flag, boolean defaultValue)` gates enforcement. `PermissionServiceImpl` must consult `permission_enforcement_enabled` (default `false`) before performing any bitwise check.

### FR-7: Demo enforcement
`GET /admin/stats` must be annotated `@HasPermission(perm = Permission.ADMIN)` to validate the path end-to-end.

---

## Components

### Backend

| Component | File | Role |
|-----------|------|------|
| `Permission` enum | `enums/Permission.java` | Source of truth — bit per capability (`int`, `1 << n`) |
| `@HasPermission` | `aop/HasPermission.java` | Meta-`@PreAuthorize`; `Permission perm()` |
| `PermissionService` / `Impl` | `service/PermissionService.java`, `service/impl/PermissionServiceImpl.java` | `hasPermission` / `hasAllPermissions` / `hasAnyPermission`; flag short-circuit |
| `AppUserDetails` | `config/AppUserDetails.java` | Custom `UserDetails` exposing `getPermissions()` / `getId()` |
| `WebSecurityConfig` | `filter/WebSecurityConfig.java` | `AnnotationTemplateExpressionDefaults` + `PermissionEnumPropertyAccessor` on a custom `MethodSecurityExpressionHandler`; `@EnableMethodSecurity` |
| `FeatureFlagService` / `Impl` | `service/FeatureFlagService.java`, `service/impl/FeatureFlagServiceImpl.java` | Harness FF wrapper; no-op when key blank |
| `AppConstant.Flags` | `utils/AppConstant.java` | `PERMISSION_ENFORCEMENT` flag key |

### Annotation contract

```java
@HasPermission(perm = Permission.ADMIN)
public ResponseEntity<...> getStats() { ... }
```

Expands (via `AnnotationTemplateExpressionDefaults`) to:

```
@PreAuthorize("@permissionServiceImpl.hasPermission(authentication.principal.permissions, ADMIN)")
```

`PermissionEnumPropertyAccessor.read(...)` maps the bare `ADMIN` → `Permission.ADMIN` so SpEL can pass it to `hasPermission`.

---

## API Changes

### Modified Endpoint

#### GET `/admin/stats`

**Change:** Added `@HasPermission(perm = Permission.ADMIN)` (in addition to the existing class-level `hasRole('ADMIN')` guard).

**Behavior:**

| Flag | User bitmask has ADMIN bit (16)? | Result |
|------|----------------------------------|--------|
| OFF (default) | — | Allowed (role guard only) |
| ON | Yes | Allowed |
| ON | No | 403 Forbidden (`AccessDeniedException`) |

No request/response body change. No new public endpoints introduced by this feature.

---

## Database Changes

```sql
-- Migration: db/changelog/sql/202605010001-init_permissions.sql

CREATE TABLE permissions (
    id          INT PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    bit_value   INT          NOT NULL UNIQUE,
    description TEXT,
    group_name  VARCHAR(50),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP             DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO permissions (id, code, name, bit_value, description, group_name) VALUES
  (1, 'READ',   'Read',   1,  'Read data',         'GENERAL'),
  (2, 'WRITE',  'Write',  2,  'Modify data',       'GENERAL'),
  (3, 'EXEC',   'Exec',   4,  'Execute actions',   'GENERAL'),
  (4, 'DELETE', 'Delete', 8,  'Delete data',       'GENERAL'),
  (5, 'ADMIN',  'Admin',  16, 'Full admin access', 'ADMIN');

ALTER TABLE users ADD COLUMN permissions INT NOT NULL DEFAULT 0;
```

`users.permissions` is an additive `INT` column; existing rows default to `0`.

---

## Configuration

| Key | Source | Default | Purpose |
|-----|--------|---------|---------|
| `harness.ff.api-key` | `application.yaml` ← `${FF_API_KEY:}` | empty | Harness FF SDK key; blank ⇒ `FeatureFlagService` is a no-op |
| `permission_enforcement_enabled` | Harness FF | `false` | Master switch for bitwise enforcement |

New dependency: `io.harness:ff-java-server-sdk`.

---

## Security Requirements

- **Additive only:** The permission layer must never weaken the existing class-level `hasRole('ADMIN')` guard. With the flag off, authorization behavior is identical to before.
- **Principal source:** Permission bits come from the authenticated `AppUserDetails`, populated at `loadUserByUsername()` — never from request input.
- **Fail-safe default:** Unreachable flag backend ⇒ `false` ⇒ role-only authorization (no lockout, no silent allow of newly-guarded endpoints since enforcement simply stays off).
- **No secrets in code:** `FF_API_KEY` is supplied via environment, not hardcoded.

---

## Edge Cases

### EC-1: User with `permissions = 0`, flag ON, accesses `@HasPermission(perm = ADMIN)` endpoint
`(0 & 16) == 0` → denied → 403. (Role guard may still allow if it is the only annotation; here both apply, the stricter wins.)

### EC-2: Flag OFF
`hasPermission` returns `true` immediately; bitwise check never runs. Logged as "permission check skipped (flag off)".

### EC-3: Harness key blank (local/test)
`FeatureFlagServiceImpl` is a no-op and returns the supplied default (`false`). App runs without a flag backend.

### EC-4: Bare enum name not a valid `Permission`
`PermissionEnumPropertyAccessor.canRead` returns `false` (catches `IllegalArgumentException`) → SpEL falls through; only valid `Permission` names resolve.

### EC-5: Multiple bits set (e.g. user has READ|WRITE|ADMIN = 19)
`19 & 16 = 16 ≠ 0` → ADMIN check passes; unrelated bits do not interfere.

---

## Acceptance Criteria

- [x] SQL seed `bit_value` matches `Permission` enum exactly
- [x] `Permission` enum uses `int` with `1 << n`
- [x] `PermissionService`/`Impl` use `int` for all permission params
- [x] `User.java` has an `int permissions` field mapped to the `permissions` column
- [x] `AppUserDetails` wraps `User` and exposes `int getPermissions()`
- [x] `loadUserByUsername()` returns `AppUserDetails`
- [x] `authentication.principal.permissions` resolves in SpEL
- [x] `Permission.ADMIN` resolves to the enum constant via `PermissionEnumPropertyAccessor`
- [x] `@HasPermission(perm = Permission.ADMIN)` enforces access on `getStats()` when flag ON
- [x] Flag OFF → all checks return `true`; existing role guard unaffected
- [x] FF backend unreachable / key blank → defaults to `false`, app functions
- [x] `./unittest.sh` / `mvn verify` passes

---

## Open Questions

- [ ] Resolved: `int` vs `long` → `int` (32 bits, ADR-0015 D1)
- [ ] Resolved: array vs single perm on annotation → single `perm` (ADR-0015 D3); array service methods retained for future
- [ ] Future: wire `hasAllPermissions` / `hasAnyPermission` to a multi-permission annotation variant if an endpoint needs AND/OR semantics

---

## Version History

| Version | Date | Author | Change |
|---------|------|--------|--------|
| 1.0 | 2026-06-02 | nguyenhuuca | Initial AS-BUILT draft (retroactive, for tracking) |
