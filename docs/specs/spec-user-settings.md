# Feature Specification: User Settings Page

<!--
Feature Specification
Filename: docs/specs/spec-user-settings.md
Owner: Builder (/builder)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)

Related:
- PRD: docs/prd/PRD-user-settings.md
- ADR: docs/adr/0016-user-settings-design.md
-->

## Metadata

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-06-20
**Related PRD:** [PRD-user-settings](../prd/PRD-user-settings.md)
**Related ADR:** [ADR-0016: User Settings Design](../adr/0016-user-settings-design.md)

---

## Overview

Adds a self-service Settings page where an authenticated user can view their identity and (display-only) account status, edit notification/playback/privacy preferences, manage MFA (via existing endpoints), and delete their account. Preferences are stored in a new 1:1 `user_settings` table; account deletion is a soft-delete with anonymization. Affects all authenticated users; no impact on anonymous/guest flows.

---

## Business Rules

### Rule 1 — One settings row per user, lazily created

Each user has exactly one `user_settings` row, keyed by `user_id`. The row does not exist until the user's first `GET /user/settings`, which creates it with system defaults. No backfill of existing users.

### Rule 2 — Default preference values

On lazy creation, fields take these exact defaults:

| Field | Default |
|-------|---------|
| `notifyNewContent` | `true` |
| `notifyEmail` | `true` |
| `defaultQuality` | `"AUTO"` |
| `incognitoEnabled` | `false` |
| `profilePrivate` | `false` |

### Rule 3 — `defaultQuality` is a closed enum

`defaultQuality` must be one of exactly `AUTO`, `1080P`, `4K` (case-sensitive). Any other value is invalid.

### Rule 4 — PATCH is partial and atomic

`PATCH /user/settings` applies only the fields present in the request body (partial merge). If **any** provided field is invalid, the entire request is rejected (HTTP 400) and **no** field is persisted. Fields absent from the body are left unchanged.

### Rule 5 — Account deletion is a soft-delete with anonymization

`DELETE /user/account` sets `users.status = 'DEACTIVATED'`, sets `users.deleted_at = now()`, and anonymizes PII (`user_name` → an irreversible placeholder, e.g. `deleted_user_<id>`; clears `password`, `mfa_secret`). The row and referential data (comments, watch-history, share-links) are retained. Physical purge is out of scope (deferred — ADR-0016 D3).

### Rule 6 — Deletion requires step-up verification

- If the user has MFA enabled (`mfa_enabled = true`): the request must include a valid current TOTP `otp`. Invalid/missing → reject.
- If the user does not have MFA: the request must include `confirmation` equal to the user's exact email (`user_name`). Mismatch/missing → reject.

### Rule 7 — Deactivated users cannot authenticate

Login (`POST /user/join`) and magic-link redemption (`GET /user/verify-magic`) must reject any account whose `status = 'DEACTIVATED'`. MFA verify must likewise reject deactivated users.

### Rule 8 — Settings are strictly owner-scoped

All settings reads/writes and the delete operation act only on the user resolved from the JWT (`SecurityContextHolder`). There is no cross-user access and no `userId` path/query parameter.

### Rule 9 — Subscription/account status is display-only

`accountStatus` in the settings response is a static, non-persisted object (`plan`, `renewsAt`) gated by a feature flag. It is never writable via PATCH. When the flag is off it is omitted/null.

### Rule 10 — Incognito suppresses watch-history recording

When `incognitoEnabled = true`, watch-history recording is suppressed. (Watch History — ADR-0013 — is not yet implemented; this is the forward-looking contract: the recording path must check this flag before writing.)

---

## Functional Requirements

### FR-1: Read settings (lazy create)

The system **must** expose `GET /user/settings` returning the authenticated user's settings. If no row exists, it **must** create one with Rule 2 defaults and return it. Response **must** include identity capabilities (`email`, `passwordEnabled`, `mfaEnabled`, `mfaAvailable`) and, when the feature flag is on, a display-only `accountStatus`.

### FR-2: Update settings (partial, atomic, validated)

The system **must** expose `PATCH /user/settings` accepting any subset of the editable fields (`notifyNewContent`, `notifyEmail`, `defaultQuality`, `incognitoEnabled`, `profilePrivate`). It **must** validate all provided fields before writing (Rule 4), reject the whole request on any invalid field (Rule 3), persist valid partial updates, bump `updated_at`, and return the full updated settings. It **must not** allow writing `accountStatus` or any identity field.

### FR-3: Delete account (soft, step-up verified)

The system **must** expose `DELETE /user/account` performing the soft-delete + anonymization of Rule 5, gated by the step-up verification of Rule 6. On success it **must** return `204 No Content`, audit-log the action, and invalidate the caller's session/token server-side where applicable.

### FR-4: Capability-driven security surface

The settings/identity response **must** expose `passwordEnabled` (true only when `appProperties.usePasswordless == false`) and `mfaEnabled`/`mfaAvailable`, so the frontend renders "Change Password" only when `passwordEnabled = true` and otherwise shows "Manage Sign-in (Magic Link)". MFA management **must** reuse existing `/user/mfa/*` endpoints (no new MFA endpoints).

### FR-5: Block deactivated-user authentication

The authentication paths (`/user/join`, `/user/verify-magic`, `/user/mfa/verify`) **must** reject users with `status = 'DEACTIVATED'` (Rule 7).

### FR-6: Settings page (frontend)

The webapp **must** provide a `/settings` route (auth-required) rendering Account Identity, Notifications, Playback, Privacy, and Danger Zone sections per the Stitch design (adapted to Funny Movies tokens). Toggles autosave individually via PATCH; Delete Account opens a confirm flow collecting OTP or the email confirmation phrase.

---

## API Changes

> Base path: `AppConstant.API.BASE_URL` = `/v1/funny-app`. All responses use `ResultObjectInfo<T>`. Errors use `ResultErrorInfo { message, status, subCode, timestamp }` produced by `CustomException` via `RestExceptionHandler`. (`status` = HTTP status int; `subCode` = app-specific int; there is no string `code` field in this codebase.)

### New Endpoints

#### GET `/v1/funny-app/user/settings`

**Description:** Returns the authenticated user's settings, creating a defaults row if none exists.

**Auth:** Required (JWT Bearer)
**Rate limit:** None

**Request:** _(no body)_

**Response — Success (200)**

```json
{
  "status": "SUCCESS",
  "data": {
    "email": "alex.vibe@noir.stream",
    "passwordEnabled": false,
    "mfaEnabled": true,
    "mfaAvailable": true,
    "notifyNewContent": true,
    "notifyEmail": true,
    "defaultQuality": "AUTO",
    "incognitoEnabled": false,
    "profilePrivate": false,
    "accountStatus": { "plan": "Free", "renewsAt": null },
    "updatedAt": "2026-06-20T10:15:00Z"
  }
}
```

> `accountStatus` is omitted/null when the feature flag is off (Rule 9).

**Response — Error**

| HTTP Status | subCode | Message | Condition |
|-------------|---------|---------|-----------|
| 401 | 401 | `Unauthorized` | Missing or invalid JWT |

#### PATCH `/v1/funny-app/user/settings`

**Description:** Partially updates editable preference fields (atomic — Rule 4).

**Auth:** Required (JWT Bearer)
**Rate limit:** `@RateLimited(permit = 20)` per window

**Request** (any subset; example shows two fields)

```json
{
  "defaultQuality": "1080P",
  "incognitoEnabled": true
}
```

**Response — Success (200):** full updated settings object (same shape as GET `data`).

**Response — Error**

| HTTP Status | subCode | Message | Condition |
|-------------|---------|---------|-----------|
| 400 | 400 | `Invalid value for field '<field>'` | Any provided field fails validation (e.g. `defaultQuality` not in `AUTO/1080P/4K`, non-boolean toggle, attempt to set `accountStatus`/identity field) — nothing persisted |
| 400 | 400 | `Empty update` | Body present but contains no editable field |
| 401 | 401 | `Unauthorized` | Missing or invalid JWT |
| 429 | 429 | `Rate limit exceeded` | More than 20 writes per window |

#### DELETE `/v1/funny-app/user/account`

**Description:** Soft-deletes and anonymizes the authenticated user's account (Rule 5), after step-up verification (Rule 6).

**Auth:** Required (JWT Bearer)
**Rate limit:** `@RateLimited(permit = 3)` per window
**Audit:** `@AuditLog` (inherited at controller level); `otp`/`confirmation` masked

**Request**

```json
{ "otp": "123456" }
```
_or, for non-MFA users:_
```json
{ "confirmation": "alex.vibe@noir.stream" }
```

**Response — Success (204):** no body.

**Response — Error**

| HTTP Status | subCode | Message | Condition |
|-------------|---------|---------|-----------|
| 400 | 400 | `Confirmation does not match` | Non-MFA user and `confirmation` != account email, or missing |
| 401 | 401 | `Unauthorized` | Missing or invalid JWT |
| 403 | 403 | `Invalid OTP` | MFA-enabled user and `otp` invalid or missing |
| 409 | 409 | `Account already deactivated` | `status` is already `DEACTIVATED` |
| 429 | 429 | `Rate limit exceeded` | More than 3 delete attempts per window |

### Modified Endpoints

#### POST `/v1/funny-app/user/join`, GET `/v1/funny-app/user/verify-magic`, POST `/v1/funny-app/user/mfa/verify`

**Change:** Add a deactivated-account guard (Rule 7). Any account with `status = 'DEACTIVATED'` is treated as non-authenticatable.

**Before:** authentication proceeds based on credentials/magic-link/OTP only.
**After:** authentication additionally rejects when `status = 'DEACTIVATED'` →

| HTTP Status | subCode | Message | Condition |
|-------------|---------|---------|-----------|
| 403 | 403 | `Account deactivated` | Resolved user has `status = 'DEACTIVATED'` |

#### GET `/v1/funny-app/user/me`

**Change:** Extend `UserDetailDto` to include `passwordEnabled`, `mfaEnabled`, `mfaAvailable` so the frontend can drive the capability-based security UI (FR-4). Additive only; existing fields unchanged.

---

## Database Changes

### New Tables

```sql
-- Migration: api/src/main/resources/db/changelog/sql/202606200001-create-user-settings.sql
CREATE TABLE user_settings (
    user_id             BIGINT      PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    notify_new_content  BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_email        BOOLEAN     NOT NULL DEFAULT TRUE,
    default_quality     VARCHAR(10) NOT NULL DEFAULT 'AUTO',
    incognito_enabled   BOOLEAN     NOT NULL DEFAULT FALSE,
    profile_private     BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

> `user_id` is both PK and FK (1:1 with `users`), matching `User.id` `BIGINT`. No separate index needed — PK covers owner lookup.

### Modified Tables

```sql
-- Migration: api/src/main/resources/db/changelog/sql/202606200002-add-user-account-lifecycle.sql
ALTER TABLE users ADD COLUMN status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'; -- ACTIVE | DEACTIVATED
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ NULL;
```

| Table | Change | Column | Type | Notes |
|-------|--------|--------|------|-------|
| `users` | Add column | `status` | `VARCHAR(20)` | `NOT NULL DEFAULT 'ACTIVE'`; enum `ACTIVE`/`DEACTIVATED` |
| `users` | Add column | `deleted_at` | `TIMESTAMPTZ` | nullable; set on soft-delete |

> Migrations are auto-included via `includeAll` on `sql/` in `db.changelog-master.yaml`; ordering by filename timestamp.

---

## Security Requirements

### Authentication

All three new endpoints require a valid JWT Bearer token; the user is resolved server-side from `SecurityContextHolder`. No endpoint accepts a `userId` parameter (Rule 8). No public access.

### Authorization

No role restriction — any authenticated `USER` may manage **their own** settings and delete **their own** account. There is no cross-user or admin path in this spec.

### Data Validation

| Field | Rule | Error |
|-------|------|-------|
| `defaultQuality` | ∈ {`AUTO`,`1080P`,`4K`} | 400 `Invalid value for field 'defaultQuality'` |
| `notifyNewContent`,`notifyEmail`,`incognitoEnabled`,`profilePrivate` | boolean | 400 `Invalid value for field '<field>'` |
| `accountStatus` / identity fields in PATCH | not writable | 400 (rejected as unknown/forbidden field) |
| `otp` (MFA delete) | valid current TOTP | 403 `Invalid OTP` |
| `confirmation` (non-MFA delete) | equals account email | 400 `Confirmation does not match` |

Validation uses `Contract.require(...)` / `ContractDSL` per project convention; whole-request atomicity per Rule 4.

### Sensitive Data

- `otp` and `confirmation` in the DELETE request **must** be masked in audit logs (`@AuditLog`).
- On deletion, `password` and `mfa_secret` **must** be cleared; `user_name` anonymized to a non-PII placeholder.
- Error messages must not leak whether an email exists beyond the caller's own account.

---

## Caching Impact

No cache impact. Per ADR-0016 D4, settings are **not** cached in v1 (low read volume, JWT-scoped, direct indexed read). No Guava cache (`VideoCacheImpl`, `ChunkIndexCacheImpl`, `StatsCacheImpl`, `MFASessionStoreImpl`) is read, written, or invalidated by this feature.

| Cache | Impact | Action |
|-------|--------|--------|
| (none) | None | No new cache introduced |

---

## Frontend Changes

### New Routes

| Path | Component | Auth Required |
|------|-----------|---------------|
| `/settings` | `SettingsPage.jsx` | Yes |

### New / Modified Components

| Component | File | Change |
|-----------|------|--------|
| `SettingsPage` | `webapp/src/components/settings/SettingsPage.jsx` | New — renders Account Identity, Notifications, Playback, Privacy, Danger Zone (CSS tokens in `src/styles/index.css`; no Tailwind/Bootstrap) |
| `DeleteAccountModal` | `webapp/src/components/settings/DeleteAccountModal.jsx` | New — collects OTP (MFA) or email confirmation, calls DELETE |
| `settingsApi` | `webapp/src/api/settings.js` | New — `get()`, `patch(partial)`, `deleteAccount(body)` using Axios `client.js` |
| `api/index.js` | `webapp/src/api/index.js` | Re-export `settingsApi` |
| Navigation | `webapp/src/components/layout/AppShell.jsx` | Add "Settings" entry to desktop side nav / mobile nav |
| `ProfileModal` | `webapp/src/components/auth/ProfileModal.jsx` | Reuse existing MFA setup/disable flows from the Account Identity section (no duplication) |

### State Management

```javascript
// React Query keys
['settings']                       // useQuery → settingsApi.get()

// Mutations
patchSettings → settingsApi.patch(partialFields)
  onSuccess: queryClient.setQueryData(['settings'], updated)   // server returns full object

deleteAccount → settingsApi.deleteAccount({ otp | confirmation })
  onSuccess: clear AuthContext (jwt, user) → redirect to login
```

Toggles autosave: each toggle fires a PATCH with only its own field. Optimistic update optional; on error, revert to server value and show `.app-alert.error`.

---

## Event / Job Changes

### Domain Events

| Event | Change | Schema |
|-------|--------|--------|
| (none) | No change | — |

### Scheduled Jobs

No `AppScheduler` changes in v1. The hard-purge job for `DEACTIVATED` accounts is explicitly deferred (ADR-0016 D3) and is **not** part of this spec.

---

## Non-Functional Requirements

### Performance

| Operation | Target | Notes |
|-----------|--------|-------|
| `GET /user/settings` | < 300 ms p95 | Single PK lookup (+ insert on first call) |
| `PATCH /user/settings` | < 300 ms p95 | Single partial `UPDATE` |
| `DELETE /user/account` | < 500 ms p95 | One `UPDATE` (status/anonymize) + OTP verify |

### Availability

No impact on existing video/auth flows except the additive deactivated-user guard. Rollback path: revert the two migrations (drop `user_settings`, drop `users.status`/`deleted_at`) and remove the endpoints — no data migration of existing users was performed, so rollback is clean.

### Scalability

`user_settings` grows at most 1 row per user (PK-bounded). No unbounded growth. Soft-deleted rows accumulate until the deferred purge job exists — acceptable at current scale.

---

## Edge Cases

### EC-1: First read creates defaults

**Condition:** Authenticated user with no `user_settings` row calls `GET /user/settings`.
**Expected:** A row is created with Rule 2 defaults; 200 returns those defaults. A second GET returns the same row without creating another.

### EC-2: Invalid value in a multi-field PATCH

**Condition:** `PATCH { "defaultQuality": "8K", "notifyEmail": false }`.
**Expected:** 400 `Invalid value for field 'defaultQuality'`; **neither** field persisted (atomic — Rule 4); `updated_at` unchanged.

### EC-3: Concurrent PATCHes (two tabs)

**Condition:** Same user sends `PATCH {notifyEmail:false}` and `PATCH {defaultQuality:'4K'}` simultaneously.
**Expected:** Both succeed; each updates only its own field; final row reflects both changes (per-field last-write-wins, no cross-field invariant). No error.

### EC-4: Empty PATCH body

**Condition:** `PATCH {}` (or body with only unknown fields).
**Expected:** 400 `Empty update`; nothing persisted.

### EC-5: Delete with wrong confirmation (non-MFA user)

**Condition:** Non-MFA user calls DELETE with `confirmation` ≠ their email.
**Expected:** 400 `Confirmation does not match`; account remains `ACTIVE`; no anonymization.

### EC-6: Delete with invalid OTP (MFA user)

**Condition:** MFA-enabled user calls DELETE with an invalid/expired `otp`.
**Expected:** 403 `Invalid OTP`; account remains `ACTIVE`.

### EC-7: Delete already-deactivated account

**Condition:** A valid (still-held) token calls DELETE when `status` is already `DEACTIVATED`.
**Expected:** 409 `Account already deactivated`; idempotent — no further changes.

### EC-8: Deactivated user attempts login / magic-link

**Condition:** User with `status = 'DEACTIVATED'` attempts `/user/join`, `/user/verify-magic`, or `/user/mfa/verify`.
**Expected:** 403 `Account deactivated`; no token issued.

### EC-9: Incognito while watch-history not implemented

**Condition:** User sets `incognitoEnabled = true`.
**Expected:** Persisted successfully; no error. Recording suppression is a forward-looking contract (Rule 10) consumed once Watch History (ADR-0013) ships.

### EC-10: Subscription flag off

**Condition:** Feature flag for account status is disabled.
**Expected:** `GET /user/settings` returns `accountStatus: null` (or omitted); PATCH never accepts `accountStatus`.

---

## Acceptance Criteria

- [ ] User can GET settings; first call creates a defaults row matching Rule 2 (EC-1).
- [ ] User can PATCH a single field; only that field changes and `updatedAt` is bumped.
- [ ] System rejects a PATCH with any invalid field (400) and persists nothing (EC-2, Rule 4).
- [ ] System rejects an empty/unknown-only PATCH with 400 (EC-4).
- [ ] Concurrent single-field PATCHes both apply (EC-3).
- [ ] MFA user can delete account only with a valid OTP; invalid OTP → 403 (EC-6).
- [ ] Non-MFA user can delete account only with matching email confirmation; mismatch → 400 (EC-5).
- [ ] Successful delete soft-deletes: `status=DEACTIVATED`, `deleted_at` set, `user_name` anonymized, `password`/`mfa_secret` cleared; returns 204.
- [ ] Deleting an already-deactivated account returns 409 (EC-7).
- [ ] Deactivated users cannot log in, redeem magic links, or verify MFA → 403 (EC-8).
- [ ] `GET /user/me` and settings response expose `passwordEnabled`/`mfaEnabled`/`mfaAvailable`; "Change Password" shows only when `passwordEnabled=true` (FR-4).
- [ ] PATCH is rate-limited (permit=20) and DELETE is rate-limited (permit=3); GET is not limited.
- [ ] `otp`/`confirmation` are masked in audit logs.
- [ ] All settings/delete operations act only on the JWT user; no `userId` parameter exists (Rule 8).
- [ ] No Guava cache is read/written/invalidated by this feature.
- [ ] Frontend `/settings` route renders all five sections; toggles autosave; Delete flow collects OTP/confirmation.
- [ ] Edge cases EC-1…EC-10 are covered by integration tests.
- [ ] Existing video/auth flows are unaffected (regression).
- [ ] `mvn verify` passes with the coverage gate met; `npm run test` passes.

---

## Open Questions

- [ ] OQ-2 (PRD): Confirm the realistic notification channels — v1 assumes only `notifyNewContent` + `notifyEmail` are deliverable (email via Spring Mail). Other channels deferred.
- [ ] OQ-5 (PRD): "Data Export" (PRD FR-10) is **out of scope** for this spec; confirm it stays deferred.

> **Assumption:** Step-up "re-verify" for non-MFA deletion uses the account email as the confirmation phrase (chosen in scoping). If product prefers a literal `"DELETE"` string, only Rule 6 / EC-5 wording changes.

---

## Version History

| Version | Date | Author | Change |
|---------|------|--------|--------|
| 1.0 | 2026-06-20 | nguyenhuuca | Initial draft from PRD-user-settings + ADR-0016 |
