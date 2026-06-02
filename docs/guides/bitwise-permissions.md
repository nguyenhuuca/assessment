# Bitwise Permissions — A General Guide

A **bitwise permission system** (also called a permission *bitmask* or *flag set*) is a technique for storing and checking a set of yes/no capabilities using the individual bits of a single integer. It is language- and framework-agnostic: the same idea works in Java, Go, C, Python, SQL, or any system that has integers and bitwise operators.

This guide explains the concept from first principles, with examples. It does **not** assume any particular web framework or library.

---

## The core idea

Suppose a user can hold any combination of these capabilities:

```
READ, WRITE, EXEC, DELETE, ADMIN
```

The naive approach is to store them as a list (`["READ", "WRITE"]`) or as rows in a join table. The bitwise approach instead **assigns each capability one bit position** and packs the whole set into a single integer.

| Permission | Bit position | Expression | Decimal | Binary  |
|------------|--------------|------------|---------|---------|
| `READ`     | 0            | `1 << 0`   | 1       | `00001` |
| `WRITE`    | 1            | `1 << 1`   | 2       | `00010` |
| `EXEC`     | 2            | `1 << 2`   | 4       | `00100` |
| `DELETE`   | 3            | `1 << 3`   | 8       | `01000` |
| `ADMIN`    | 4            | `1 << 4`   | 16      | `10000` |

Each value is a distinct power of two, so every permission owns exactly one bit and no two permissions overlap. A *set* of permissions is just the bits OR-ed together.

> **Rule of thumb:** permission values must always be powers of two (`1, 2, 4, 8, 16, …`). Never `3` — that is two permissions at once.

---

## The four operations

All permission logic reduces to four bitwise operations.

### 1. Combine permissions — OR (`|`)

Build a permission set by OR-ing the bits:

```
READ | WRITE | ADMIN
= 1 | 2 | 16
= 19            (binary 10011)
```

A user whose stored value is `19` holds READ, WRITE, and ADMIN.

### 2. Check a permission — AND (`&`)

To ask "does this set contain X?", AND the set with X's bit. The result is non-zero only if the bit is present:

```
has(19, ADMIN):   19 & 16        has(3, ADMIN):    3 & 16
   10011                            00011
 & 10000                          & 10000
 -------                          -------
   10000  = 16 ≠ 0  → YES ✅        00000  = 0     → NO  ❌
```

```
hasPermission(userBits, perm) :=  (userBits & perm) != 0
```

### 3. Grant a permission — OR (`|`)

Turn a bit *on* without disturbing the others:

```
grant(READ, WRITE) → 00001 | 00010 = 00011   (now has WRITE too)
```

### 4. Revoke a permission — AND NOT (`& ~`)

Turn a bit *off*. `~` flips all bits, so `~DELETE` is a mask with every bit set *except* DELETE; AND-ing keeps everything else:

```
revoke(27, DELETE):  27 & ~8
   11011  (READ|WRITE|DELETE|ADMIN)
 & 10111  (~DELETE)
 -------
   10011  = 19  (DELETE removed, rest intact)
```

---

## Combined check: ALL vs ANY

Two common multi-permission questions:

**Has ALL of a set** — the masked value must equal the full mask:

```
hasAll(userBits, mask) :=  (userBits & mask) == mask
```

**Has ANY of a set** — the masked value must be non-zero:

```
hasAny(userBits, mask) :=  (userBits & mask) != 0
```

Example with `userBits = 3` (READ|WRITE) and `mask = READ|ADMIN = 17`:

| Check | Computation | Result |
|-------|-------------|--------|
| `hasAll(3, 17)` | `3 & 17 = 1`, `1 != 17` | `false` (missing ADMIN) |
| `hasAny(3, 17)` | `3 & 17 = 1`, `1 != 0`  | `true`  (has READ)      |

---

## Worked example (pseudocode)

```text
# Define permissions as powers of two
READ   = 1
WRITE  = 2
EXEC   = 4
DELETE = 8
ADMIN  = 16

# A user's stored permission integer
user = READ | WRITE          # = 3

# Checks
can(user, READ)              # 3 & 1  = 1  → true
can(user, ADMIN)             # 3 & 16 = 0  → false

# Grant ADMIN
user = user | ADMIN          # 3 | 16 = 19

# Revoke WRITE
user = user & ~WRITE         # 19 & ~2 = 17  (READ|ADMIN)
```

The same logic in SQL, treating a `permissions INTEGER` column:

```sql
-- Who can delete?           (bit DELETE = 8)
SELECT id FROM users WHERE (permissions & 8) <> 0;

-- Grant WRITE to user 4     (keep existing bits)
UPDATE users SET permissions = permissions | 2  WHERE id = 4;

-- Revoke DELETE from user 5
UPDATE users SET permissions = permissions & ~8 WHERE id = 5;
```

---

## Why use it

- **Compact storage** — an entire permission set is one integer column, not N rows.
- **Fast checks** — a single CPU instruction (`AND`), no allocations, no joins, no loops.
- **Atomic grant/revoke** — `|` and `& ~` toggle one capability without touching the rest.
- **Cache- and copy-friendly** — an integer travels trivially inside a token, a session, or a struct.

## Trade-offs and pitfalls

- **Limited count** — an N-bit integer holds at most N permissions (32 for `int`, 64 for `long`). Plenty for most apps, but not unbounded.
- **Values must be powers of two** — the single most common bug is assigning `3` to a permission; it silently means "two permissions". Always use `1 << n`.
- **Opaque to humans** — `permissions = 19` is not self-describing. Keep a single source of truth (an enum/constant table) mapping names ↔ bits, and never hardcode the magic numbers elsewhere.
- **Migrating values is painful** — once data is stored against bit 3, you cannot reuse bit 3 for something else without rewriting existing rows. Append new permissions at higher bits; never recycle.
- **Coarse audit trail** — you see *what* a user can do, but the integer alone carries no history of *why* or *when* a bit was set.

## When NOT to use it

- You need **hundreds of permissions** or fully dynamic, user-defined permissions → use a relational `roles`/`permissions` table or an external authorization service.
- You need **rich relationships** (resource-scoped, hierarchical, time-bound grants) → bitmasks only express a flat global set.
- For those cases, a bitmask can still serve as a fast cache layer in front of the authoritative store.

---

## Practical conventions

1. **One source of truth.** Define the name → bit mapping in exactly one place (an enum, a constants file, or a seed table) and derive everything else from it.
2. **Keep the seed in sync.** If both code *and* a database seed list the bit values, a mismatch grants the wrong capability. Verify they agree.
3. **Default to zero.** A new user starts at `0` (no permissions). Granting is explicit.
4. **Roll out behind a switch.** When adding enforcement to an existing system, gate it so the default behavior is unchanged until permissions have been seeded — otherwise everyone (still at `0`) is locked out of newly guarded actions.
5. **Reserve growth headroom.** If you expect more than ~32 permissions, start with a 64-bit type, or group permissions into multiple integer "domains".

---

## In this project

This codebase implements exactly this pattern: a 32-bit `int` bitmask, a `Permission` enum as the single source of truth, and method-level enforcement gated behind a feature flag (default off). For the concrete, framework-specific wiring see:

- [ADR-0015: Bitwise Permission System](../adr/0015-bitwise-permission-system.md) — the architectural decisions
- [Spec: Bitwise Permissions](../specs/spec-bitwise-permissions.md) — the implemented behavior
- [Plan: Bitwise Permission System](../plans/plan-permissions-bitwise.md) — the task breakdown
