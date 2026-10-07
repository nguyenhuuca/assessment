# PRD: Optional Email + Password Login

<!--
Product Requirements Document
Filename: docs/prd/PRD-password-login.md
Owner: nguyenhuuca
Handoff to: Architect (ADR-0018), Builder (/builder)
-->

## Overview

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-10-07
**Related ADR:** [ADR-0018 Optional Email + Password Login](../adr/0018-password-login-option.md)
**Related Plan:** [plan-password-login](../plans/plan-password-login.md)

Users can optionally add a password to their account (after signing in once by magic link) and then sign in with email + password. Magic link login stays available for everyone.

---

## Problem Statement

Login is magic-link only: every sign-in needs an email round-trip (Gmail SMTP, 200 emails/day shared with notification digests). Users on a new device must switch to their mailbox; when the budget is exhausted nobody can log in.

### Evidence
- **Quantitative:** daily email cap 200 (`app.email-setting.max-daily-emails`), shared with the notification digest (≤ 50%).
- **Qualitative:** owner request to add username/password as an option (2026-10-07).

---

## Goals & Success Metrics

| Goal | Metric | Target (30 days) |
|------|--------|------------------|
| Faster sign-in | % of logins via password among users who set one | ≥ 60% |
| Lower email load | Magic-link emails per day | −30% |
| Safe | Accounts taken over via password | 0 |
| No enumeration | Login error responses identical across failure causes | 100% (test) |

---

## User Stories

### Persona: Viewer (has an account)
- **US-1** As a viewer signed in by magic link, I can **set a password** in Settings → Sign-in.
- **US-2** As a viewer with a password, I can **sign in with email + password** from the header login form (tab "Mật khẩu").
- **US-3** As a viewer with MFA, after a correct password I'm asked for my 6-digit code (same as today).
- **US-4** As a viewer, I can **change** my password (current password required) or **remove** it (back to magic link only).
- **US-5** As a viewer who forgot the password, I click "Quên mật khẩu? Gửi link đăng nhập", sign in by link and set a new password.
- **US-6** As a viewer, I receive an email whenever my password is set, changed or removed.
- **US-7** As a viewer, changing my password signs out my other devices.

### Persona: New visitor
- **US-8** As a new visitor, I create my account by magic link (unchanged); I cannot create an account with a password.

---

## Requirements

### Functional

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-1 | `POST /user/login {email, password}`: success → JWT (or `MFA_REQUIRED`); any failure → 401 `INVALID_CREDENTIALS` | Must |
| FR-2 | Login form has two tabs: **Link đăng nhập** (current) and **Mật khẩu** (email + password, show/hide toggle) | Must |
| FR-3 | `PUT /user/password` set (no current) / change (current required); OTP required when MFA enabled | Must |
| FR-4 | `DELETE /user/password` (current required) | Should |
| FR-5 | Policy: 10–72 bytes, not containing email local part, not in common-password list; clear Vietnamese error messages | Must |
| FR-6 | Lockout: 5 consecutive failures → 15 min; counter reset on success; magic link unaffected | Must |
| FR-7 | Password set/change/remove revokes other sessions (current session receives a new JWT) | Must |
| FR-8 | Security email on set/change/remove | Should |
| FR-9 | `passwordEnabled` per user in `/user/me` and settings; Settings shows Set / Change / Remove accordingly | Must |
| FR-10 | `/user/join` no longer auto-registers with a password (legacy path removed) | Must |
| FR-11 | Kill switch `app.auth.password-login-enabled` hides the tab and disables password endpoints | Should |

### Non-Functional

| ID | Requirement |
|----|-------------|
| NFR-1 | BCrypt cost 12; login p95 < 400 ms |
| NFR-2 | No password in logs, responses, URLs or analytics (`@Sensitive`) |
| NFR-3 | Rate limit 10 req/min/IP on `/user/login` |
| NFR-4 | Accessibility: password field has label, show/hide button with `aria-pressed`, errors announced (`role="alert"`) |

---

## Scope

### In Scope
Login tab, set/change/remove password, lockout, session revocation, security email, removal of legacy auto-register.

### Out of Scope
Password signup, separate reset-token flow, CAPTCHA, breached-password API, passkeys, social login.

---

## Dependencies

| Dependency | Status |
|------------|--------|
| Magic link login | Exists |
| MFA (TOTP) flow | Exists |
| Settings page "Sign-in" area (`passwordEnabled`) | Exists (ADR-0016) |
| MailService + daily budget | Exists |

---

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| Legacy non-null passwords exist on prod | Low | Medium | Check before migration; they keep working with the new endpoint |
| Third party locks a victim out | Medium | Low | 15 min only; magic link still works |
| Extra lookup per request for `credentials_version` | High | Low | Guava cache 60 s |
| Users expect password signup | Medium | Low | Copy on login tab: "Chưa có mật khẩu? Đăng nhập bằng link rồi đặt mật khẩu trong Cài đặt" |

---

## Open Questions

| # | Question | Proposed |
|---|----------|----------|
| OQ-1 | Allow password signup with email verification? | No (v1) — ADR-0018 option A2 deferred |
| OQ-2 | Lockout thresholds | 5 failures / 15 min |
| OQ-3 | Keep `app.use-password-less` for backward compat? | Replace with `app.auth.password-login-enabled`; read old key once with deprecation log |

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product | nguyenhuuca | | Pending |
| Engineering | nguyenhuuca | | Pending (ADR-0018) |

## Next Steps & Handoffs
1. Review ADR-0018 → Accepted
2. `/swarm-execute docs/plans/plan-password-login.md`
3. E2E on prod; update `docs/tracking.md`

## Version History

| Version | Date | Change |
|---------|------|--------|
| 0.1 | 2026-10-07 | Initial draft |
