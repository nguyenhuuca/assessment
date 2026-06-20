# ADR-0016: User Settings — Storage, API Shape, and Account Deletion

## Metadata

**Status:** Proposed · **Date:** 2026-06-20 · **Deciders:** nguyenhuuca · **Tags:** user-settings, schema, api-design, account-lifecycle, frontend-state  
**Related PRD:** [PRD-user-settings](../prd/PRD-user-settings.md) · **Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** ✅ Follows Golden Path — PostgreSQL + JPA + Liquibase + Spring Boot + React, no new infrastructure (no Redis, no new external service)

---

## Context

The [User Settings PRD](../prd/PRD-user-settings.md) introduces a self-service Settings page (adapted from the Stitch *Settings - Web View* design) with four functional sections — Account Identity, Notifications, Playback, Privacy/Danger Zone — plus a display-only Account Status panel.

The PRD leaves several decisions to architecture (its Open Questions OQ-1, OQ-3, OQ-4). This ADR resolves the ones that are genuinely architectural:

1. **Where and how to store preferences** — the `User` entity (`api/.../entity/User.java`) holds only auth-critical fields (`userName`, `password`, `mfaEnabled`, `mfaSecret`, `role`, `permissions`) and is loaded on every authenticated request. There is no preference storage today (verified — no settings/preference entity exists). (OQ-4)
2. **API shape** — a single page edits many independent fields across sections; we need a contract that supports per-toggle autosave without clobbering sibling fields.
3. **Account deletion semantics** — the design's Danger Zone "Delete Account" is destructive and cascades to related data (watch-history, comments, share-links). Behavior was left open as PRD OQ-1.
4. **Auth-surface variability** — sign-in is config-gated: `appProperties.isUsePasswordless()` switches between password login and magic-link (`UserController.signIn`). The design's "Change Password" field only applies in one mode. (OQ-3, PRD risk R-1)

Constraints from the existing codebase to honor:
- `User.id` is `BIGINT` (`GenerationType.IDENTITY`) — new FKs reference `users(id)` as BIGINT (unlike the UUID PKs used by `video_comments` / watch-history).
- Controllers use `@RequestMapping(AppConstant.API.BASE_URL + "/user")`, `ResultObjectInfo<T>` wrappers, `@AuditLog`, `@RateLimited`, `@WithSpan`.
- MFA endpoints already exist: `GET /user/mfa/setup`, `POST /user/mfa/enable|disable|verify`.
- Migrations live in `db/changelog/sql/` and are auto-included via `includeAll` in `db.changelog-master.yaml` (ordered by filename `YYYYMMDDHHMM-*.sql`).
- No subscription/billing backend exists (PRD risk R-2).

---

## Decision Drivers

- Must not bloat the auth-critical `Users` row read on every request.
- Preference set is small and known for v1 but will grow (PRD risk R-3) — schema should extend cheaply.
- Per-toggle autosave UX → partial updates must not clobber concurrently-edited sibling fields.
- Account deletion must be reversible within a support window and preserve audit/referential integrity (PRD risk R-4).
- Auth controls must adapt to `usePasswordless` mode without shipping dead UI.
- No new infrastructure (Tech Strategy: PostgreSQL + JPA, no Redis until horizontal scaling — ADR-0003).
- Reuse existing MFA endpoints and controller conventions.

---

## Decision 1: Settings Storage Model

### Option A: Add columns to `Users` table

Store preferences as new columns on the existing `Users` entity.

| Pros | Cons |
|------|------|
| No new table/join | Pollutes auth-critical entity loaded on every request |
| Simplest JPA change | Mixes security identity with mutable UI prefs (poor cohesion) |
| | Every new preference touches the hottest table |

### Option B: New 1:1 `user_settings` table with typed columns

Dedicated entity, one row per user, FK to `users(id)`, lazily created with defaults on first read.

| Pros | Cons |
|------|------|
| Isolates prefs from auth identity | Extra table + 1:1 mapping |
| Type-safe columns with DB defaults + constraints | Lazy-create logic on first GET |
| Migrates cleanly via Liquibase; indexable | |
| Can add a JSONB `extra` column later for open-ended prefs | |

### Option C: JSONB blob column

Single `settings JSONB` column (on Users or a small table).

| Pros | Cons |
|------|------|
| Maximally flexible — add keys with no migration | Loses column-level defaults/constraints/validation |
| One column | Partial validation in app code; weaker type safety |
| | Harder to query/report per-preference |

### Option D: EAV key/value rows

A `user_setting (user_id, key, value)` table.

| Pros | Cons |
|------|------|
| Infinitely extensible | Over-engineered for a small fixed set |
| | No type safety; multi-row read/write per save; awkward joins |

---

## Decision 2: API Shape

### Option A: GET + full PUT (replace whole object)

`GET /user/settings`, `PUT /user/settings` with the complete settings body.

| Pros | Cons |
|------|------|
| Simple, idempotent | Per-toggle save must send the whole object |
| | Clobbers sibling fields changed concurrently (lost update across sections) |

### Option B: GET + PATCH (partial merge)

`GET /user/settings`, `PATCH /user/settings` merging only provided fields. Account/security actions stay as their own explicit endpoints.

| Pros | Cons |
|------|------|
| Matches per-toggle autosave — send only the changed field | PATCH semantics need clear "only non-null fields applied" contract |
| No clobbering of sibling fields | |
| Single preference endpoint; sensitive actions kept separate | |

### Option C: Per-section endpoints

`PUT /user/settings/notifications`, `/playback`, `/privacy`, etc.

| Pros | Cons |
|------|------|
| Clean separation per section | More endpoints to build/test/secure |
| | Still coarse within a section (a section's PUT clobbers its own fields) |

---

## Decision 3: Account Deletion (resolves PRD OQ-1)

### Option A: Hard delete + cascade

`DELETE` the user row; DB cascades remove watch-history, etc.

| Pros | Cons |
|------|------|
| Simplest; true erasure immediately | Irreversible — no support recovery window |
| | Destroys audit trail; unexpected cascade to comments/shares |
| | `video_comments.user_id` is a VARCHAR (no FK) → orphaned rows |

### Option B: Soft delete + anonymize

Set `status = DEACTIVATED` + `deleted_at`, anonymize PII (email/userName), retain referential data.

| Pros | Cons |
|------|------|
| Reversible within support window | Data physically retained (until purge) |
| Preserves audit trail + FK integrity | Needs status checks in auth/login path |
| Satisfies "remove my identity" via anonymization | |

### Option C: Soft delete + scheduled purge after grace period

Option B, plus an `AppScheduler` job that hard-purges after N days.

| Pros | Cons |
|------|------|
| Reversible window AND eventual true erasure (GDPR-style) | Extra scheduled job + purge logic |
| | More to build/test for v1 |

---

## Decision Outcome

### D1 — Storage: **Option B — 1:1 `user_settings` table with typed columns**

**Rationale:** The v1 preference set is small and known, so typed columns give the best safety (DB defaults, `NOT NULL`, enums) without the query/validation cost of JSONB (Option C) or EAV (Option D). Isolating settings from `Users` (rejecting Option A) keeps the auth-critical row lean — settings are not loaded on every authenticated request, only on the Settings page. Growth (risk R-3) is handled by adding columns via Liquibase, with the explicit escape hatch of a future `extra JSONB` column if truly open-ended preferences emerge. Row is **lazily created with defaults** on first `GET`, so existing users need no backfill migration.

### D2 — API: **Option B — GET + PATCH for preferences; explicit endpoints for sensitive actions**

**Rationale:** PATCH with "only non-null fields applied" maps directly to the page's per-toggle autosave and avoids the lost-update problem of a full PUT (Option A) across independent sections. A single preference endpoint is less surface than per-section PUTs (Option C). Crucially, **sensitive account operations are NOT folded into the settings PATCH** — MFA reuses the existing `/user/mfa/*` endpoints and account deletion gets its own explicit, confirmed `DELETE /user/account`. This separates low-risk idempotent preference edits from high-risk identity operations.

### D3 — Deletion: **Option C — Soft delete + anonymize now, scheduled purge designed (job deferred)**

**Rationale:** Hard delete (Option A) is irreversible, destroys the audit trail, and orphans `video_comments` (which key on a VARCHAR `user_id` with no FK). Soft delete + anonymize (Option B) preserves auditability and referential integrity while satisfying "erase my identity" by scrubbing email/userName. We adopt **Option C's design** — `status = DEACTIVATED` + `deleted_at` + anonymization immediately, with a true-erasure purge job — but **defer the purge job implementation to v2** to keep v1 scope tight. The deletion endpoint is `@AuditLog`-wrapped and requires explicit confirmation (and re-verification where MFA is enabled). This resolves PRD OQ-1.

### D4 — Auth surface & subscription (resolves OQ-3 / risks R-1, R-2)

- **Capability-driven security UI:** the settings/identity response exposes auth capabilities (`passwordEnabled`, `mfaEnabled`, `mfaAvailable`). The frontend renders "Change Password" **only** when `passwordEnabled = true` (i.e. `usePasswordless = false`); otherwise it shows "Manage Sign-in (Magic Link)". No dead UI ships in either mode. MFA reuses existing endpoints.
- **Subscription is display-only:** `GET /user/settings` returns a static `accountStatus` (e.g. `plan = "Free"`) behind a feature flag — no new table, no billing integration. Clearly marked placeholder; real billing deferred to v2+ (risk R-2).
- **No caching in v1:** settings are read once per page load and JWT-scoped; a direct indexed DB read is cheap. We deliberately add **no Guava cache** to avoid invalidation complexity for a low-read-volume resource (aligns with ADR-0003 — no premature caching).

### Quantified Impact

| Metric | Value | Notes |
|--------|-------|-------|
| Extra columns on hot `Users` table | 0 | Prefs isolated in `user_settings` |
| Settings rows loaded per authenticated request | 0 | Only loaded on Settings page GET |
| DB ops per toggle save (PATCH) | 1 | Single partial `UPDATE` |
| Backfill migration for existing users | 0 | Lazy-create defaults on first GET |
| New infrastructure | None | PostgreSQL + JPA only |

---

## Consequences

**Positive:**
- `Users` (auth-critical) stays lean; settings evolve independently.
- Per-toggle autosave is race-safe (PATCH merges only changed fields).
- Account deletion is reversible within a support window and keeps audit + referential integrity.
- Security UI adapts to `usePasswordless` mode — no dead controls.
- Zero new infrastructure; consistent with existing controller/migration patterns.

**Negative:**
- Lazy-create adds a small branch on first `GET /user/settings`.
- Soft delete retains data until a (deferred) purge job runs — not immediate physical erasure.
- PATCH "non-null = changed" cannot express "set field to null"; v1 preferences have no nullable user-settable values, so acceptable (revisit if such a field appears).

**Risks:**
- **Concurrent PATCHes from two tabs** → both partial updates apply; last-write-wins per field. Acceptable for independent toggles (no cross-field invariants in v1).
- **Deactivated-user login** must be blocked in the auth path — login/magic-link flow must check `status != DEACTIVATED` (add to validation; flagged for Builder).
- **Anonymization completeness** — ensure no PII leaks remain in logs/related tables; covered by existing `@AuditLog` masking, to be verified in security review.

---

## Schema Reference

```sql
-- Migration: db/changelog/sql/202606200001-create-user-settings.sql

CREATE TABLE user_settings (
    user_id              BIGINT      PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    -- Notifications
    notify_new_content   BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_email         BOOLEAN     NOT NULL DEFAULT TRUE,
    -- Playback
    default_quality      VARCHAR(10) NOT NULL DEFAULT 'AUTO',   -- AUTO | 1080P | 4K
    -- Privacy
    incognito_enabled    BOOLEAN     NOT NULL DEFAULT FALSE,    -- pauses watch-history recording
    profile_private      BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
    -- extra JSONB  -- reserved escape hatch for open-ended future prefs (R-3)
);

-- Account lifecycle additions on Users (separate migration):
-- ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'; -- ACTIVE | DEACTIVATED
-- ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ NULL;
```

> **Note:** `incognito_enabled` is the integration point for the Watch History feature (ADR-0013) — when true, the watch-history recording POST is suppressed client-side and/or rejected server-side. Watch-history is not yet implemented, so this is a forward-looking contract.

## API Contract Reference

```
GET    /api/v1/user/settings          → ResultObjectInfo<UserSettingsDto>   (lazy-create defaults)
PATCH  /api/v1/user/settings          body: partial UserSettingsDto         (only provided fields applied)
DELETE /api/v1/user/account           → 204  (soft delete + anonymize; @AuditLog; confirm + re-verify)

-- Reused (existing):
GET    /api/v1/user/mfa/setup
POST   /api/v1/user/mfa/enable | /disable | /verify
GET    /api/v1/user/me                (identity; extend to expose passwordEnabled/mfaEnabled capabilities)
```

`UserSettingsDto` includes a display-only `accountStatus` object (`plan`, `renewsAt`) behind a feature flag — no persistence in v1.

---

## Validation

- [ ] `GET /user/settings` lazily creates a defaults row for a user with none — verified by test.
- [ ] `PATCH` with a single field updates only that field; sibling fields unchanged — verified by test.
- [ ] `DELETE /user/account` sets `status=DEACTIVATED`, `deleted_at`, anonymizes `userName`; row retained.
- [ ] Deactivated user cannot log in / redeem magic link.
- [ ] Security UI: "Change Password" hidden when `usePasswordless=true`; shown when false.
- [ ] No new Guava cache introduced; settings read is a direct indexed DB hit.
- [ ] Security review completed (anonymization completeness, audit masking).
- [ ] Tech Strategy alignment confirmed: ✅ PostgreSQL + JPA + Liquibase, no new infra.
- [ ] Related plan document created: `docs/plans/plan-user-settings.md`.

---

## Links

- [PRD: User Settings](../prd/PRD-user-settings.md)
- [ADR-0013: Watch History Design](./0013-watch-history-design.md) — `incognito_enabled` integration point
- [ADR-0003: Use Cache](./0003-use-cache.md) — no premature caching rationale
- [ADR-0015: Bitwise Permission System](./0015-bitwise-permission-system.md) — existing `User.permissions` model
- Design assets: `artifacts/stitch_settings_web.png`, `artifacts/stitch_settings_web.html`

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-06-20 | nguyenhuuca | Initial draft — storage model, API shape, account deletion, auth-surface decisions |
