# Plan: Optional Email + Password Login

<!--
Implementation Plan
Filename: docs/plans/plan-password-login.md
Owner: Builder (/builder)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer), Security Auditor (/security-auditor)
-->

## Overview

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-10-07
**Related PRD:** [PRD-password-login](../prd/PRD-password-login.md)
**Related ADR:** [ADR-0018](../adr/0018-password-login-option.md)

## Verified Facts (exploration 2026-10-07)

| Area | Fact |
|------|------|
| Config | `app.use-password-less: true` (yaml:113) binds to `AppProperties.usePasswordless` (relaxed binding) — prod is magic-link only |
| Legacy path | `UserController.signIn` → `joinSystem(LoginDto)` when not passwordless: auto-registers unknown emails with BCrypt password (no verification) |
| Auth | `AuthenticationManager` + `loadUserByUsername` (rejects null password); BCrypt bean `bCrypt` |
| MFA | `MFA_REQUIRED` + `MFASessionStore` session token → `POST /user/mfa/verify` |
| Rate limit | `@RateLimited(permit, windowInSeconds)`, key `IP:` or `USER:` |
| JWT | `JwtProvider.generateToken(JwtGenerationDto{payload: UserDetailDto})`; filter `JWTAuthenticationFilter` |
| Settings | `UserSettingsServiceImpl.buildDto` exposes `passwordEnabled`; FE `SettingsPage` shows "Change Password" vs "Manage Sign-in" |
| FE | `LoginForm.jsx` email-only; `ProfileModal` tabs User Info / MFA; `MFALoginModal` reusable; no password input component |
| Mail | `MailService.sendSimpleMail`; `EmailCacheLimiter` daily budget 200 |

---

## Implementation Steps

### Phase 1 — Data & config (0.5 d) — `PW-1`
- [ ] Pre-check on prod (read-only): `SELECT count(*) FROM users WHERE password IS NOT NULL` — record result in this plan
- [ ] Migration `202610070001-users-password-login.sql`: `password_set_at`, `failed_login_count`, `locked_until`, `credentials_version` (ADR-0018)
- [ ] `User`: new fields with `@Builder.Default` where defaulted (see `ee6ad36` lesson)
- [ ] `AppProperties.auth.passwordLoginEnabled` (`app.auth.password-login-enabled`, default `true`); remove reads of `usePasswordless` (log a deprecation warning if the old key is present)
- [ ] `PasswordPolicy` utility: length 10..72 **bytes** (UTF-8), not containing email local part (case-insensitive, ≥ 3 chars), not in `common-passwords.txt` (10k, classpath, loaded once into a `Set`)
- [ ] BCrypt encoder strength 12 (check existing bean; keep compatible with existing hashes)

### Phase 2 — Login endpoint & lockout (1 d) — `PW-2` (after PW-1)
- [ ] `POST /user/login` in `UserController` (`@RateLimited(permit = 10)`, `@AuditLog`), `PasswordLoginRequest {@Sensitive email, @Sensitive password}`
- [ ] Service `PasswordLoginService.login`:
  - flag off → 404
  - find user; if null / password null / DEACTIVATED / `locked_until > now` → run a dummy BCrypt match (timing) → throw `INVALID_CREDENTIALS`
  - BCrypt match fail → `failed_login_count++`; at 5 → `locked_until = now + 15m`, reset count → `INVALID_CREDENTIALS`
  - success → reset count/lock; MFA → existing `MFA_REQUIRED` flow; else JWT
- [ ] All failures → identical `401 {"error":{"message":"INVALID_CREDENTIALS"}}` via `CustomException(UNAUTHORIZED)`
- [ ] Remove legacy branch in `UserController.signIn` (`joinSystem`) — `/user/join` = magic link only; delete/deprecate `joinSystem` + `toEntity`
- [ ] Tests: success, wrong password, unknown email, null password, deactivated, locked, 5th failure locks, success resets, MFA path, identical error body for every failure, flag off

### Phase 3 — Set / change / remove password (1 d) — `PW-3` (after PW-1)
- [ ] `PUT /user/password` `{currentPassword?, newPassword, otp?}`, `DELETE /user/password` `{currentPassword, otp?}` (auth, `@RateLimited(permit = 5)`, `@AuditLog`, all fields `@Sensitive`)
- [ ] Rules: has password → `currentPassword` must match (wrong → 400 `CURRENT_PASSWORD_INVALID`, counts toward lockout); MFA enabled → valid `otp`; policy errors → 400 with code (`PASSWORD_TOO_SHORT`, `PASSWORD_TOO_LONG`, `PASSWORD_CONTAINS_EMAIL`, `PASSWORD_TOO_COMMON`)
- [ ] Save hash, `password_set_at`, `credentials_version++`; return new JWT for the current session
- [ ] Security email (plain text, Vietnamese) via `MailService.sendSimpleMail`; counts against the daily budget but **never blocked** by the digest's 50% cap (security mail has priority) — if budget exhausted, log warning
- [ ] `passwordEnabled` per user in `getToken`, `/user/me`, `UserSettingsServiceImpl.buildDto`; add `passwordLoginAvailable` (flag)
- [ ] Tests for every rule + email sent + version bump

### Phase 4 — Session revocation (0.5 d) — `PW-4` (after PW-1; integrates with PW-3)
- [ ] JWT claim `cv` = `credentials_version` (in `UserDetailDto` / `JwtProvider` payload)
- [ ] `CredentialsVersionCache` (Guava, `expireAfterWrite 60s`, `userId → version`); evict on change
- [ ] `JWTAuthenticationFilter`: after verify, if `token.cv != cache.get(userId)` → 401 `TOKEN_REVOKED` (tokens without `cv` treated as `0` for backward compat)
- [ ] Tests: old token rejected after change; new token accepted; cache eviction; legacy token without `cv`

### Phase 5 — Frontend (1.25 d) — `PW-5` (contract-first)
- [ ] `api/auth.js`: `login(email, password)`, `setPassword(body)`, `removePassword(body)`
- [ ] `components/common/PasswordInput.jsx` (label, show/hide with `aria-pressed`, `autocomplete` `current-password` / `new-password`)
- [ ] `LoginForm.jsx`: tabs **Link đăng nhập | Mật khẩu** (desktop header + mobile popover); password tab only when `passwordLoginAvailable` (from `/user/settings` capability or a public config — use `GET /user/auth-options` if needed, else always show and let 404 hide it); generic error text "Email hoặc mật khẩu không đúng"; link "Quên mật khẩu? Gửi link đăng nhập" switches tab and pre-fills email; MFA → existing `MFALoginModal`
- [ ] Settings → Sign-in (`ProfileModal` new tab "Đăng nhập" or SettingsPage section): Set password (new + confirm), Change (current + new + confirm), Remove (current); OTP field when `mfaEnabled`; client-side policy hints mirroring server codes; on success store the returned JWT (`AuthContext.login`) and toast "Đã cập nhật mật khẩu. Các thiết bị khác đã đăng xuất."
- [ ] Handle `TOKEN_REVOKED` globally in `client.js` → logout + message
- [ ] Tests: tabs, login success/MFA/error, forgot link, set/change/remove flows, policy messages, revoked-token logout
- [ ] `npm run lint && npx vitest run && npm run build`

### Phase 6 — Verification & docs (0.25 d) — `PW-6`
- [ ] Prod E2E: magic link → set password → logout → password login → wrong ×5 → locked (magic link still works) → change password on device A → device B logged out within 60 s → security emails received
- [ ] `/security-auditor` review of the diff
- [ ] Update `docs/tracking.md`, mkdocs nav

---

## Dependency Graph

```
PW-1 ──┬──► PW-2 ──┐
       ├──► PW-3 ──┼──► PW-6
       └──► PW-4 ──┘
PW-5 (contract-first) ─┘
```

Suggested swarm: one BE builder PW-1 → PW-2 → PW-3 → PW-4 (shared files: `UserController`, `UserServiceImpl`, `JwtProvider`); one FE builder PW-5 in parallel.

**Estimate:** ≈ 4.5 days (≈ 3 days critical path).

## Risks

| Risk | Mitigation |
|------|-----------|
| Breaking magic-link login while removing the legacy branch | Keep `/user/join` + `/user/verify-magic` tests green; E2E first step is magic link |
| Filter change rejects all existing JWTs | Missing `cv` = 0 = default column value |
| BCrypt cost 12 CPU on 1 vCPU | ~250 ms per check; login rate-limited; acceptable |
| `@Builder` defaults forgotten on new `User` fields | Explicit `@Builder.Default` + test asserting defaults |
