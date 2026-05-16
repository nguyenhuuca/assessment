# Plan: Bitwise Permission System — Full Wiring

**Relates to:** `202605010001-init_permissions.sql`, `HasPermission.java`, `Permission.java`, `PermissionServiceImpl.java`
**Size:** Medium (1–2 days)
**Decision type:** Two-Way Door (additive columns, replaceable auth layer)
**Blocks nothing** — `@HasPermission` not yet used on any endpoint

---

## Problem Summary

The permission system has been partially built. Four issues prevent it from working end-to-end:

| # | Issue | Severity |
|---|-------|----------|
| 1 | SQL seed `bit_value` is misaligned with `Permission` enum | High — wrong users get wrong permissions |
| 2 | `User` entity has no `permissions` field (column exists in DB, entity doesn't) | High — permissions never read |
| 3 | No custom `UserDetails` impl — `authentication.principal.permissions` always fails | High — `@HasPermission` throws NPE |
| 4 | `@HasPermission` SpEL calls `hasPermission(long, Permission)` but passes `Permission[]` | High — wrong method signature |

---

## Design Decision: `int` over `long`

All permission fields use **`int`** (not `long`). Rationale:
- 32 bits = 32 possible permissions — far more than this app needs
- No `L` suffix noise in enum
- PostgreSQL `INT` instead of `BIGINT` — cleaner schema
- Since all files are uncommitted, now is the right time to align

**Affects:** `Permission.java`, `PermissionService.java`, `PermissionServiceImpl.java`, `User.java`, `UserDetailDto.java`, `AppUserDetails.java`, SQL column type.

---

## Gap Analysis

### Issue 1 — SQL bit values wrong

`Permission` enum (source of truth, updated to `int`):

| Enum | Bit | Value |
|------|-----|-------|
| READ | 1 << 0 | 1 |
| WRITE | 1 << 1 | 2 |
| EXEC | 1 << 2 | 4 |
| DELETE | 1 << 3 | 8 |
| ADMIN | 1 << 4 | 16 |

Current SQL seed (wrong):

```sql
(3, 'USER_DELETE', 'Delete User', 4, ...)   -- 4 = EXEC, not DELETE
(4, 'ADMIN_FULL',  'Admin Full',  8, ...)   -- 8 = DELETE, not ADMIN
-- EXEC (bit_value=4) is missing entirely
```

### Issue 2 — `User.java` entity missing `permissions` field

`users` table has `permissions BIGINT NOT NULL DEFAULT 0` (added by migration), but `User.java` entity has no field for it.

### Issue 3 — No custom `UserDetails`

`UserServiceImpl.loadUserByUsername()` returns Spring's generic `User`:
```java
return new org.springframework.security.core.userdetails.User(..., authorities);
```
Spring's `User` has no `permissions` field → `authentication.principal.permissions` throws `PropertyNotFoundException` at runtime.

### Issue 4 — Wrong method in SpEL expression

`@HasPermission` annotation:
```java
@PreAuthorize("@permissionServiceImpl.hasPermission(authentication.principal.permissions, #perms)")
```
- `#perms` is `Permission[]` (array — from `Permission[] perms()`)
- `hasPermission(long, Permission)` takes a **single** `Permission`
- Result: `MethodResolutionException` at runtime

---

## Implementation Steps

### Step 1 — Fix SQL seed data

**File:** `api/src/main/resources/db/changelog/sql/202605010001-init_permissions.sql`

Change `bit_value` column type to `INT` and fix seed values:

```sql
CREATE TABLE permissions
(
    id          INT PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    bit_value   INT          NOT NULL UNIQUE,   -- was BIGINT, now INT
    description TEXT,
    group_name  VARCHAR(50),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP             DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO permissions (id, code, name, bit_value, description, group_name)
VALUES (1, 'READ',   'Read',   1,  'Read data',         'GENERAL'),
       (2, 'WRITE',  'Write',  2,  'Modify data',       'GENERAL'),
       (3, 'EXEC',   'Exec',   4,  'Execute actions',   'GENERAL'),
       (4, 'DELETE', 'Delete', 8,  'Delete data',       'GENERAL'),
       (5, 'ADMIN',  'Admin',  16, 'Full admin access', 'ADMIN');

ALTER TABLE users
    ADD COLUMN permissions INT NOT NULL DEFAULT 0;  -- was BIGINT
```

> `code` values match `Permission` enum names exactly. Since this file is not yet committed, edit in place (no fixup migration needed).

### Step 2 — Switch `Permission` enum to `int`

**File:** `api/src/main/java/com/canhlabs/funnyapp/enums/Permission.java`

```java
private final int bit;

READ(1 << 0), WRITE(1 << 1), EXEC(1 << 2), DELETE(1 << 3), ADMIN(1 << 4);

Permission(int bit) { this.bit = bit; }
public int getBit() { return bit; }
```

### Step 3a — Update `PermissionService` and `PermissionServiceImpl` signatures

**Files:** `service/PermissionService.java`, `service/impl/PermissionServiceImpl.java`

Change all `long userPermissions` / `long userPerm` → `int`:

```java
boolean hasPermission(int userPermissions, Permission perm);
boolean hasAllPermissions(int userPerm, Permission[] perms);
boolean hasAnyPermission(int userPerm, Permission[] perms);
```

Bitwise operations (`&`) work identically on `int`.

### Step 4a — Add `permissions` field to `User.java` entity

**File:** `api/src/main/java/com/canhlabs/funnyapp/entity/User.java`

```java
@Column(name = "permissions", nullable = false)
private int permissions = 0;
```

### Step 5 — Create `AppUserDetails.java` (custom UserDetails)

**New file:** `api/src/main/java/com/canhlabs/funnyapp/config/AppUserDetails.java`

```java
package com.canhlabs.funnyapp.config;

import com.canhlabs.funnyapp.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AppUserDetails implements UserDetails {

    private final User user;

    public AppUserDetails(User user) {
        this.user = user;
    }

    public int getPermissions() {
        return user.getPermissions();
    }

    public Long getId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override public String getPassword()  { return user.getPassword(); }
    @Override public String getUsername()  { return user.getUserName(); }
    @Override public boolean isAccountNonExpired()   { return true; }
    @Override public boolean isAccountNonLocked()    { return true; }
    @Override public boolean isCredentialsNonExpired(){ return true; }
    @Override public boolean isEnabled()             { return true; }
}
```

### Step 6 — Update `UserServiceImpl.loadUserByUsername()`

**File:** `api/src/main/java/com/canhlabs/funnyapp/service/impl/UserServiceImpl.java`

Replace the return that builds `new User(...)` with:

```java
return new AppUserDetails(userEntity);  // userEntity is the User loaded from DB
```

### Step 7 — Fix `@HasPermission` SpEL expression

**File:** `api/src/main/java/com/canhlabs/funnyapp/aop/HasPermission.java`

Change SpEL to use `hasAllPermissions` (AND semantics — user must have ALL listed permissions):

```java
@PreAuthorize("@permissionServiceImpl.hasAllPermissions(authentication.principal.permissions, #perms)")
```

Or use `hasAnyPermission` for OR semantics. **Decision: use `hasAllPermissions`** since `perms` is an array and the common case is "user must have all of these".

### Step 8 — Update `UserDetailDto`

**File:** `api/src/main/java/com/canhlabs/funnyapp/dto/UserDetailDto.java`

Add:
```java
private int permissions;
```

Update `AppUtils.getCurrentUser()` to populate it from `AppUserDetails`.

### Step 9 — Demo usage in `AdminController`

Add `@HasPermission` to one endpoint to validate end-to-end:

```java
@GetMapping("/stats")
@HasPermission(perms = {Permission.ADMIN})
public ResponseEntity<ResultObjectInfo<AdminStatsDto>> getStats() { ... }
```

---

## Acceptance Criteria

- [ ] SQL seed bit values match `Permission` enum exactly
- [ ] `Permission` enum uses `int` with `1 << n` (no `L` suffix)
- [ ] `PermissionService` / `PermissionServiceImpl` use `int` for all permission params
- [ ] `User.java` has `permissions` (`int`) field mapped to `permissions` column
- [ ] `AppUserDetails` wraps `User` and exposes `getPermissions()` as `int`
- [ ] `loadUserByUsername()` returns `AppUserDetails`
- [ ] `authentication.principal.permissions` resolves correctly in SpEL
- [ ] `@HasPermission(perms = {Permission.ADMIN})` on a method enforces access
- [ ] Existing `@PreAuthorize("hasRole('ADMIN')")` class-level guard still works
- [ ] `./unittest.sh` passes

---

## File Checklist

| # | File | Action |
|---|------|--------|
| 1 | `db/changelog/sql/202605010001-init_permissions.sql` | Fix seed data (5 rows, correct bit values), `BIGINT` → `INT` |
| 2 | `enums/Permission.java` | `long bit` → `int bit`, `1L << n` → `1 << n` |
| 3 | `service/PermissionService.java` | `long` → `int` in all method signatures |
| 4 | `service/impl/PermissionServiceImpl.java` | `long` → `int` in all method params |
| 5 | `entity/User.java` | Add `permissions int` field |
| 6 | `config/AppUserDetails.java` | Create — custom UserDetails with `int getPermissions()` |
| 7 | `service/impl/UserServiceImpl.java` | Return `AppUserDetails` from `loadUserByUsername()` |
| 8 | `aop/HasPermission.java` | Fix SpEL: `hasPermission` → `hasAllPermissions` |
| 9 | `dto/UserDetailDto.java` | Add `permissions int` field |
| 10 | `web/AdminController.java` | Add `@HasPermission` on `getStats()` as demo |

---

## Feature Flag Gate (Harness FF)

The bitwise permission enforcement is controlled by a Harness FF boolean flag `permission_enforcement_enabled`.

| Flag state | Behavior |
|------------|----------|
| OFF (default) | All permission checks return `true` — only role guard applies |
| ON | Bitwise check enforced via `PermissionServiceImpl` |

**Default = `false`** (permissive) — existing `@PreAuthorize("hasRole('ADMIN')")` still protects all endpoints while the flag rolls out. If Harness is unreachable at startup, `false` keeps the app working.

### Flag target
Uses current authenticated user (`SecurityContextHolder`) as target identifier; falls back to `"system"`.

### Additional files

| # | File | Action |
|---|------|--------|
| A | `pom.xml` | Add `io.harness:ff-java-server-sdk:1.9.3` |
| B | `application.yaml` | Add `harness.ff.api-key: ${FF_API_KEY:}` |
| C | `utils/AppConstant.java` | Add inner class `Flags` with `PERMISSION_ENFORCEMENT` constant |
| D | `service/FeatureFlagService.java` | New interface: `isEnabled(String flag, boolean defaultValue)` |
| E | `service/impl/FeatureFlagServiceImpl.java` | `@PostConstruct` init CfClient, `@PreDestroy` close, no-op when key blank |
| F | `service/impl/PermissionServiceImpl.java` | Inject `FeatureFlagService`, short-circuit all 3 methods when flag OFF |
