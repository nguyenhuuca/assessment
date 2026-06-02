# ADR-0015: Bitwise Permission System — Fine-Grained Method Authorization

## Metadata

**Status:** Accepted · **Date:** 2026-06-02 · **Deciders:** nguyenhuuca · **Tags:** security, authorization, spring-security, feature-flag
**Related PRD:** N/A · **Related Spec:** [spec-bitwise-permissions](../specs/spec-bitwise-permissions.md) · **Related Plan:** [plan-permissions-bitwise](../plans/plan-permissions-bitwise.md)
**Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** ✅ Follows Golden Path — Spring Security + JWT, PostgreSQL, no new infrastructure beyond the Harness FF SDK

> Documented retroactively (AS-BUILT) for tracking. The system is implemented and merged; this ADR records the decisions actually made, which diverge from the original plan in two places (noted inline).

---

## Context

The app guards admin endpoints with a single coarse role check (`@PreAuthorize("hasRole('ADMIN')")`). This is all-or-nothing: any feature that needs a capability finer than "is admin" has nowhere to express it. We need a fine-grained, composable permission layer that can sit alongside the existing role guard without breaking it, and that can be rolled out safely (off by default).

A partially-built permission system already existed (`Permission` enum, `PermissionService`, `@HasPermission`, a seed migration) but was non-functional end-to-end: bit values were misaligned, the `User` entity had no `permissions` field, there was no custom `UserDetails` to expose permissions to SpEL, and the annotation's SpEL expression called the wrong method signature.

This ADR captures the architectural decisions taken to wire it together.

---

## Decision Drivers

- Permission checks must be expressible at the method level, declaratively
- Must coexist with the existing class-level `hasRole('ADMIN')` guard — additive, not a replacement
- Must be rolled out safely: default behavior unchanged until explicitly enabled
- Permissions must be cheap to store and check (high-frequency, on every guarded request)
- Schema must stay clean; minimize new infrastructure

---

## Decision 1: Bitmask stored as `int` (not `long`)

Permissions are modeled as a bitmask: each `Permission` enum constant owns one bit (`1 << n`), a user's granted set is a single integer column, and a check is a bitwise AND.

| Option | Pros | Cons |
|--------|------|------|
| **`int` (32 bits)** ✅ | 32 capabilities — far more than needed; PostgreSQL `INT`; no `L` noise in enum | Caps at 32 permissions (acceptable) |
| `long` (64 bits) | 64 capabilities | `BIGINT` column, `1L << n` noise, over-provisioned |
| Join table (`user_permissions`) | Unbounded, relational | N+1 reads, extra joins on every guarded request — over-engineered for a small fixed set |

**Outcome:** `int`. 32 bits is comfortably more than this app will use, a check is a single `&` with zero allocations, and storage is one `INT` column. A join table is the right answer only if permissions become dynamic/unbounded — explicitly not the case here.

---

## Decision 2: Custom `UserDetails` (`AppUserDetails`) to expose permissions to SpEL

Spring's stock `org.springframework.security.core.userdetails.User` has no `permissions` property, so `authentication.principal.permissions` cannot resolve in a `@PreAuthorize` expression.

| Option | Pros | Cons |
|--------|------|------|
| **Custom `AppUserDetails` wrapping `User`** ✅ | `principal.permissions` resolves directly in SpEL; carries `id` too | One small new class |
| Encode permissions as `GrantedAuthority` strings | No new class | Stringly-typed, loses the bitmask, awkward bitwise checks |
| Re-query the DB inside the check | Always fresh | DB hit on every guarded request — defeats the point |

**Outcome:** `AppUserDetails implements UserDetails`, wraps the `User` entity, exposes `int getPermissions()` and `Long getId()`. `UserServiceImpl.loadUserByUsername()` returns it, so the bitmask travels with the authenticated principal and is readable from SpEL with no extra query.

---

## Decision 3: Single-permission annotation via template expression + custom `PropertyAccessor`

> **Divergence from plan.** The plan proposed `@HasPermission(perms = {…})` (an array) wired to `hasAllPermissions(...)`. The as-built implementation uses a **single** `Permission perm()` and a cleaner SpEL mechanism. The array methods (`hasAllPermissions` / `hasAnyPermission`) remain on the service for future multi-permission needs but are not used by the annotation.

The annotation is:

```java
@PreAuthorize("@permissionServiceImpl.hasPermission(authentication.principal.permissions, {perm})")
public @interface HasPermission { Permission perm(); }
```

Two Spring Security beans make this work (in `WebSecurityConfig`):

1. **`AnnotationTemplateExpressionDefaults`** — expands the `{perm}` template placeholder, turning `@HasPermission(perm = Permission.ADMIN)` into the bare identifier `ADMIN` inside the SpEL expression.
2. **`PermissionEnumPropertyAccessor`** (registered on a custom `MethodSecurityExpressionHandler`) — resolves that bare identifier `ADMIN` back to the `Permission.ADMIN` constant in the evaluation context, since SpEL cannot otherwise resolve a bare enum name.

| Option | Pros | Cons |
|--------|------|------|
| **Single `perm` + template + property accessor** ✅ | Clean call site `@HasPermission(perm = Permission.ADMIN)`; type-safe enum arg; no array ceremony | Requires two security beans |
| Array `perms` + `hasAllPermissions` (plan) | Multiple perms in one annotation | Array SpEL plumbing, AND-vs-OR ambiguity at call site |
| Pass raw `int` bit to annotation | No accessor needed | Magic numbers at call site, loses enum safety |

**Outcome:** single-permission annotation. It covers every current use case (one capability per endpoint), reads cleanly, and keeps the enum type-safe at the call site. Multi-permission AND/OR semantics are available on the service if a future endpoint needs them.

---

## Decision 4: Feature-flag gate, permissive by default (Harness FF)

Enforcement is gated by the boolean Harness flag `permission_enforcement_enabled`.

| Flag | Behavior |
|------|----------|
| **OFF (default)** | All three `PermissionService` methods short-circuit to `true` — only the existing role guard applies |
| ON | Bitwise check enforced |

| Option | Pros | Cons |
|--------|------|------|
| **Feature-flag gated, default OFF** ✅ | Safe rollout; existing `hasRole('ADMIN')` still protects everything; if Harness is unreachable, `false` keeps the app working | Adds the `ff-java-server-sdk` dependency |
| Always-on | No flag plumbing | Risky big-bang; a seed/bit mistake locks out admins |
| Config property (`application.yaml`) | No SDK | No runtime toggle, no per-target rollout |

**Outcome:** Harness FF, default `false`. The `FeatureFlagService` abstraction is a no-op when the API key is blank, so local/test runs need no flag backend. This makes the new layer strictly additive: with the flag off, nothing changes.

---

## Decision Outcome Summary

| # | Decision | Choice |
|---|----------|--------|
| D1 | Bitmask width | `int` (32 bits), PostgreSQL `INT` |
| D2 | Principal carries permissions | Custom `AppUserDetails` wrapping `User` |
| D3 | Annotation shape | Single `@HasPermission(perm = X)` via template expression + `PermissionEnumPropertyAccessor` |
| D4 | Rollout | Harness FF gate, default OFF (permissive) |

---

## Consequences

**Positive:**
- Method-level, declarative, type-safe authorization: `@HasPermission(perm = Permission.ADMIN)`
- Strictly additive — role guard untouched, flag-off = no behavior change
- O(1), allocation-free permission check (single bitwise AND)
- Permission set travels with the JWT principal; no extra DB read per check

**Negative:**
- Capped at 32 permissions (acceptable headroom)
- Two non-obvious Spring Security beans required to make the SpEL resolve — documented in `WebSecurityConfig` and `HasPermission` comments
- New runtime dependency: `io.harness:ff-java-server-sdk`

**Risks:**
- Misaligned `bit_value` seed vs. `Permission` enum grants the wrong capabilities → mitigated: seed `code`/`bit_value` match the enum exactly; verified by acceptance criteria
- Flag on with empty user permission bitmask (default 0) locks users out of newly-guarded endpoints → mitigated: default OFF; permissions must be seeded before enabling

---

## Schema Reference

```sql
-- Migration: db/changelog/sql/202605010001-init_permissions.sql

CREATE TABLE permissions (
    id          INT PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,   -- matches Permission enum name
    name        VARCHAR(100) NOT NULL,
    bit_value   INT          NOT NULL UNIQUE,   -- matches Permission.getBit()
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

---

## Validation

- [x] SQL seed `bit_value` matches `Permission` enum (`READ=1, WRITE=2, EXEC=4, DELETE=8, ADMIN=16`)
- [x] `Permission` enum uses `int` with `1 << n`
- [x] `AppUserDetails` exposes `int getPermissions()`; `loadUserByUsername()` returns it
- [x] `authentication.principal.permissions` resolves in SpEL; `Permission.ADMIN` → `ADMIN` resolves via `PermissionEnumPropertyAccessor`
- [x] `@HasPermission(perm = Permission.ADMIN)` enforces access on `AdminController.getStats()`
- [x] Flag OFF → all checks return `true`; existing `hasRole('ADMIN')` guard unaffected
- [x] Tech Strategy alignment: ✅ no new infrastructure beyond FF SDK

---

## Links

- [Spec: Bitwise Permissions](../specs/spec-bitwise-permissions.md)
- [Plan: Bitwise Permission System](../plans/plan-permissions-bitwise.md)
- [ADR-0002: Backend Architecture](./0002-backend-architecture.md) — Spring Security + JWT baseline

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-06-02 | nguyenhuuca | Initial AS-BUILT draft (retroactive, for tracking) |
