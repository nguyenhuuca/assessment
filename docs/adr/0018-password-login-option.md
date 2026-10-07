# ADR-0018: Optional Email + Password Login

**Status:** Accepted
**Date:** 2026-10-07
**Deciders:** nguyenhuuca
**Related:** [ADR-0016 User Settings](0016-user-settings-design.md) (`passwordEnabled` capability, R-1), [PRD-user-notifications](../prd/PRD-user-notifications.md) (email budget), [PRD-password-login](../prd/PRD-password-login.md), [plan-password-login](../plans/plan-password-login.md)

---

## Context

Today users sign in **only** with a magic link (`POST /user/join` → email → `GET /user/verify-magic`). Users want the option to sign in with **email + password** (faster, works when email is slow or the daily email budget of 200 is exhausted).

Current code (verified 2026-10-07):

| Fact | Where | Consequence |
|------|-------|-------------|
| Auth mode is a **global switch** `app.use-password-less: true` (binds to `AppProperties.usePasswordless` via relaxed binding) | `application.yaml:113`, `UserController.signIn` | Either *all* users use magic links or *all* use passwords — not an option per user |
| Legacy password path `UserServiceImpl.joinSystem(LoginDto)`: unknown email → **auto-registers** with the supplied password, **no email verification** | `UserServiceImpl:95-119, 228-232` | **Account pre-hijacking**: an attacker registers `victim@x.com` with their own password; when the victim later signs in by magic link they land in the same account, and the attacker keeps password access |
| `users.password` nullable, BCrypt bean exists; `loadUserByUsername` rejects null passwords | `User`, `UserServiceImpl:122-128` | Storage + hashing ready |
| `passwordEnabled` in JWT/`/user/me`/settings = `!usePasswordless` (global) | `UserServiceImpl.getToken`, `UserSettingsServiceImpl` | Settings UI already switches "Change password" vs "Magic link" on this flag |
| MFA (TOTP) flow returns `MFA_REQUIRED` + session token, shared by both login paths | `UserServiceImpl`, `MFASessionStore` | Reusable as-is |
| Brute-force protection = `@RateLimited` sliding window **per IP** only; no per-account lockout | `RateLimitAspect` | Distributed guessing against one account is unbounded |
| JWTs are stateless, TTL from `app.token-expired`; no revocation | `JwtProvider` | A password change cannot log out other sessions today |
| No set/change/reset password endpoints | — | All new |
| `LoginDto.password` is `@Sensitive`; `logging.masking.fields` includes `password` | `LoginDto`, `application.yaml:145` | Audit logs already mask passwords |

## Decision Drivers

1. **Email ownership must be proven before a password can exist** (prevents pre-hijacking).
2. Password is **optional per user**; magic link keeps working for everyone.
3. Resist online guessing (per-account + per-IP), no user enumeration.
4. A password change must be able to **revoke other sessions**.
5. Golden Path only: Spring Security + BCrypt + JWT, Liquibase, Guava cache; no new infra.

---

## Options

### A. How does a password come into existence?

| Option | Description | Verdict |
|--------|-------------|---------|
| A1. Sign up with email + password (legacy `joinSystem`) | Unknown email + password → account created immediately | ❌ Pre-hijacking; unverified emails; spam accounts |
| A2. Sign up with email + password **+ email verification** before activation | Account pending until link clicked | Adds a new pending state + verification email per signup (email budget) — more code, same email cost as magic link |
| **A3. Set password after a magic-link sign-in** (Settings → Sign-in) | First login is always by magic link (proves email); then user may add a password | ✅ Chosen — zero new trust paths; reuses existing verification; simplest |

### B. Login endpoint

| Option | Verdict |
|--------|---------|
| B1. Reuse `POST /user/join` with optional `password` field | ❌ Mixes "send me a link" and "check my password" semantics, error shapes and rate limits |
| **B2. New `POST /user/login {email, password}`** | ✅ Clear contract; own rate limit and lockout; `/user/join` stays magic-link only |

### C. Brute force

| Option | Verdict |
|--------|---------|
| C1. IP rate limit only (today) | ❌ Distributed attacks unbounded |
| **C2. IP rate limit + per-account lockout** (5 consecutive failures → locked 15 min; counter in DB) + generic error | ✅ Chosen. Lockout is time-boxed so it cannot be abused for permanent DoS; magic link still works while locked |
| C3. CAPTCHA | Deferred — new third-party dependency |

### D. Revoking sessions on password change

| Option | Verdict |
|--------|---------|
| D1. Do nothing (stateless JWT) | ❌ Stolen token survives a password change |
| **D2. `credentials_version` on `users`, copied into JWT claim `cv`; `JWTAuthenticationFilter` rejects tokens whose `cv` ≠ current (Guava cache, 60 s TTL, evicted on change)** | ✅ Chosen. One cached lookup per request; revocation within ≤ 60 s on other instances, immediate locally |
| D3. Server-side session store / token blacklist | Heavier; not needed |

### E. Password policy

NIST SP 800-63B style: **min 10 characters, max 72 bytes** (BCrypt limit), any characters, must not contain the email local part, rejected if in a bundled list of the 10k most common passwords. **No** composition rules, **no** periodic expiry. BCrypt cost 12.

---

## Decision

1. **Per-user password, opt-in, set only after a magic-link (or existing) authenticated session.** Remove the global either/or switch: replace `app.use-password-less` with `app.auth.password-login-enabled` (default `true`) — a kill switch for the password endpoints only; magic link is always on.
2. **Retire the legacy auto-register path** (`UserController.signIn` → `joinSystem`). `POST /user/join` only sends magic links.
3. **New endpoints**
   ```
   POST   /user/login              {email, password}                 → UserInfoDto | MFA_REQUIRED   (public, @RateLimited 10/min/IP)
   PUT    /user/password           {currentPassword?, newPassword, otp?} → {jwt}                    (auth)
   DELETE /user/password           {currentPassword, otp?}           → {jwt}  (fresh token; removal also bumps credentials_version)  (auth)
   ```
   - `PUT` first-time set: no `currentPassword`; requires `otp` if MFA enabled.
   - `PUT` change: requires `currentPassword` (and `otp` if MFA).
   - `DELETE`: back to magic-link-only.
   - Every set/change/delete: bump `credentials_version`, return a fresh JWT for the current session, send a plain-text security email ("Mật khẩu của bạn vừa được thay đổi…").
4. **Login semantics**
   - Unknown email, no password set, wrong password, deactivated, locked → **same** `401 {"message":"INVALID_CREDENTIALS"}` (no enumeration). Locked additionally logs server-side.
   - Spring `DaoAuthenticationProvider` (already performs a dummy BCrypt check for unknown users → similar timing).
   - Success: reset counter; MFA → existing `MFA_REQUIRED` flow.
   - Failure: `failed_login_count++`; at 5 → `locked_until = now + 15 min`, counter reset.
5. **`passwordEnabled`** in JWT / `/user/me` / settings becomes **per user**: `password-login-enabled && user.password != null`. New capability `passwordLoginAvailable` = the global flag (FE shows the password tab).
6. **Forgot password** = request a magic link ("Quên mật khẩu? Gửi link đăng nhập"), then set a new password in Settings. No separate reset token flow.

## Data Model

```sql
ALTER TABLE users
    ADD COLUMN password_set_at      TIMESTAMPTZ,
    ADD COLUMN failed_login_count   INT         NOT NULL DEFAULT 0,
    ADD COLUMN locked_until         TIMESTAMPTZ,
    ADD COLUMN credentials_version  INT         NOT NULL DEFAULT 0;
```

Existing rows: **no account has a password** (confirmed by the owner on prod, 2026-10-07) — schema-only migration, no data migration or backfill. Existing JWTs carry no `cv` claim and are treated as `cv = 0` (the column default), so nobody is logged out by the deploy.

## Security Notes

| Threat | Mitigation |
|--------|-----------|
| Account pre-hijacking (CWE-287) | No password signup; password only from an authenticated session |
| Online guessing (CWE-307) | 10/min/IP + 5 failures → 15 min lock per account |
| User enumeration (CWE-204) | Single generic error for all failure causes; same status & shape |
| Credential storage (CWE-916) | BCrypt cost 12; 72-byte cap enforced (no silent truncation) |
| Session persistence after change (CWE-613) | `credentials_version` claim check |
| Password in logs (CWE-532) | `@Sensitive` on all password DTO fields; masking list already contains `password` |
| Stolen session sets a password | `PUT` change requires current password; MFA users also need OTP; security email notifies the owner |

## Consequences

**Positive**
- Users get a fast login option without weakening email-verified identity.
- Email budget pressure drops (fewer magic links).
- Fixes the latent pre-hijacking hole by removing the auto-register path.

**Negative**
- Users must sign in by magic link once before they can use a password.
- One extra cached lookup per authenticated request (`credentials_version`).
- Lockout can be triggered by a third party for 15 min (magic link remains available).

**Follow-ups**
- CAPTCHA after N failures if abuse appears.
- Breached-password check (HIBP k-anonymity) — needs outbound call; deferred.
- Passkeys/WebAuthn as a later option.
