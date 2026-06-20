# Plan: User Settings Page

<!--
Implementation Plan
Owner: Builder (/builder)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)
-->

## Overview

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-06-20
**Beads Issue:** N/A — Beads (`bd`) is not installed in this repo (no `.beads/`). Task tracking is the work-breakdown table below; convert to issues if Beads is later adopted.
**Related PRD:** [PRD-user-settings](../prd/PRD-user-settings.md)
**Related ADR:** [ADR-0016: User Settings Design](../adr/0016-user-settings-design.md)
**Related Spec:** [spec-user-settings](../specs/spec-user-settings.md)

## Objective

Implement the self-service Settings feature: a backend `user_settings` store with GET/PATCH preference endpoints and a soft-delete account endpoint, plus a frontend Settings view wired into the existing nav. Behavior is fully defined by the spec; this plan sequences the work and pins it to verified codebase patterns.

## Scope

### In Scope

- New `user_settings` table + JPA entity + repository (BIGINT PK/FK to `users`).
- `users` lifecycle columns (`status`, `deleted_at`) + deactivated-user auth guard.
- `GET /user/settings` (lazy-create defaults), `PATCH /user/settings` (atomic partial), `DELETE /user/account` (soft-delete + step-up verify).
- Capability fields (`passwordEnabled`, `mfaEnabled`, `mfaAvailable`) on `UserDetailDto`/`/user/me`.
- Display-only `accountStatus` behind a feature flag.
- Frontend Settings view (Account Identity, Notifications, Playback, Privacy, Danger Zone) wired via `AppShell` `activeNav`, `settingsApi`, React Query hooks, delete-account modal.
- Unit + controller tests meeting the coverage gate.

### Out of Scope

- Real subscription/billing integration & "View Billing History" (ADR R-2 — placeholder only).
- Hard-purge scheduled job for deactivated accounts (ADR D3 — deferred).
- Watch-history recording suppression logic (Watch History not yet implemented; `incognitoEnabled` is stored only — ADR-0013).
- "Manage Connected Apps", livestream/DM notifications, Data Export pipeline (PRD out-of-scope).

## Technical Approach

### Architecture Changes

```
Frontend (webapp)                      Backend (api)
─────────────────                      ─────────────
AppShell (activeNav==='settings')      UserSettingsController  @RestController
  └─ SettingsPage.jsx                    GET   /user/settings   (no rate limit)
       ├─ useSettings()  ──GET──┐        PATCH /user/settings   @RateLimited(permit=20)
       ├─ useUpdateSettings()─PATCH┐     DELETE/user/account    @RateLimited(permit=3) @AuditLog
       └─ DeleteAccountModal ─DELETE┐         │
                                    └────►  UserSettingsServiceImpl
                                              ├─ AppUtils.getCurrentUser() → UserDetailDto
                                              ├─ Totp.verify(otp, secret)  (delete step-up)
                                              ├─ UserSettingsRepository (JpaRepository)
                                              └─ UserRepo (status/anonymize on delete)
                                            ┌────────────┴────────────┐
                                       user_settings (new)    users (+status,+deleted_at)
```

### Key Decisions

| Decision | Rationale |
|----------|-----------|
| Frontend wiring via `AppShell` `activeNav === 'settings'`, NOT a react-router `/settings` route | Verified: app has no route-based views; a "Settings" nav button already exists rendering `<ComingSoon>`. **Spec correction:** "route `/settings`" → state-nav `settings`. |
| Error responses are `{ status: "FAILED", error: {...} }` via `RestExceptionHandler` | Verified envelope differs from the spec's bare `ResultErrorInfo`. **Spec correction:** error body is wrapped under `error`. Frontend `client.js` already normalizes to `{ message, status }`. |
| Service uses `@RequiredArgsConstructor` (comment-style) or setter `@Autowired` (share-style) | Both are accepted in the codebase; use `@RequiredArgsConstructor` for the new impl (simpler). |
| Reuse `Totp.verify(otp, user.getMfaSecret())` for delete step-up | No new MFA code; mirrors `UserServiceImpl.verifyMfa`. |
| Add `confirmation` to `logging.masking.fields`; `otp` already masked | Spec security requirement; minimal config change. |
| `UserSettings` PK = `user_id BIGINT` (1:1), entity `@Id` (not `@GeneratedValue`) | Matches ADR schema; `User.id` is BIGINT IDENTITY. Note: differs from UUID entities like `VideoComment`. |

## Implementation Steps

### Phase 1: Database & Domain Foundation

- [ ] **Step 1.1:** Create `user_settings` migration
  - Files: `api/src/main/resources/db/changelog/sql/202606200001-create-user-settings.sql`
  - Details: Exact DDL from spec (BIGINT PK/FK, defaults, `updated_at`). Auto-included via `includeAll`.
- [ ] **Step 1.2:** Create `users` lifecycle migration
  - Files: `api/src/main/resources/db/changelog/sql/202606200002-add-user-account-lifecycle.sql`
  - Details: `ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'`, `ADD COLUMN deleted_at TIMESTAMPTZ NULL`.
- [ ] **Step 1.3:** `UserSettings` entity
  - Files: `api/.../entity/UserSettings.java`
  - Details: Lombok `@Getter @Setter @SuperBuilder @NoArgsConstructor @Entity @Table(name="user_settings")`; `@Id Long userId`; `@UpdateTimestamp updatedAt`. `default_quality` as `String` (validated in service).
- [ ] **Step 1.4:** Extend `User` entity + `UserStatus` enum
  - Files: `api/.../entity/User.java`, `api/.../enums/UserStatus.java`
  - Details: Add `status` (enum `ACTIVE|DEACTIVATED`, `@Enumerated(STRING)`) and `Instant deletedAt`.
- [ ] **Step 1.5:** `UserSettingsRepository`
  - Files: `api/.../repo/UserSettingsRepository.java`
  - Details: `extends JpaRepository<UserSettings, Long>`.

### Phase 2: Backend Service & DTOs

- [ ] **Step 2.1:** DTOs in `dto/usersettings/`
  - Files: `UserSettingsDto.java` (response incl. capabilities + `accountStatus`), `UpdateUserSettingsRequest.java` (all fields nullable `Boolean`/`String` for partial PATCH), `DeleteAccountRequest.java` (`otp`, `confirmation`), `AccountStatusDto.java` (`plan`, `renewsAt`).
  - Details: Lombok POJOs with `@Builder` (codebase uses classes, not records, for DTOs). Use `@Sensitive` on `otp`/`confirmation` (belt-and-suspenders with masking config).
- [ ] **Step 2.2:** `DefaultQuality` enum + validation helper
  - Files: `api/.../enums/DefaultQuality.java` (`AUTO, Q1080P, Q4K` mapping to `AUTO/1080P/4K`)
  - Details: Used to validate PATCH `defaultQuality`; invalid → `CustomException` 400.
- [ ] **Step 2.3:** `UserSettingsService` impl
  - Files: `api/.../service/impl/UserSettingsServiceImpl.java` (+ optional `service/UserSettingsService.java` interface)
  - Methods:
    - `getSettings()` — resolve user via `AppUtils.getCurrentUser()`; lazy-create defaults row if absent (`@Transactional`).
    - `updateSettings(UpdateUserSettingsRequest)` — validate ALL provided fields first (atomic); reject whole request on any invalid field (`CustomException` 400, message `Invalid value for field '<field>'`); empty body → 400 `Empty update`; persist partial; return full DTO.
    - `deleteAccount(DeleteAccountRequest)` — load user; if already `DEACTIVATED` → 409; if `mfaEnabled` require valid `totp.verify(otp, secret)` else 403; else require `confirmation == userName` else 400; set `status=DEACTIVATED`, `deletedAt=now`, anonymize `userName` → `deleted_user_<id>`, null `password`/`mfaSecret`.
  - Details: Build `accountStatus` from `AppProperties` flag (Step 2.4); capability fields from `appProperties.isUsePasswordless()` + `user.isMfaEnabled()`.
- [ ] **Step 2.4:** Subscription feature flag
  - Files: `api/.../config/AppProperties.java`, `application.yaml`
  - Details: Add `boolean subscriptionStatusEnabled` (default false) under `app.*`. When false, `accountStatus = null`.

### Phase 3: Backend Controller, Auth Guard & Cross-Cutting

- [ ] **Step 3.1:** `UserSettingsController`
  - Files: `api/.../web/UserSettingsController.java`
  - Details: `@RestController @RequestMapping(AppConstant.API.BASE_URL + "/user")` (or `/user/settings` + `/user/account`); constructor injection; `@AuditLog` at class level; `GET /settings`, `PATCH /settings` (`@RateLimited(permit=20)`), `DELETE /account` (`@RateLimited(permit=3)`, returns `204`). Wrap success in `ResultObjectInfo`. **Note:** DELETE returns `ResponseEntity<Void>` 204, not a wrapper.
- [ ] **Step 3.2:** Deactivated-user auth guard
  - Files: `api/.../service/impl/UserServiceImpl.java` (join, `joinSystemPaswordless`, `verifyMfa`), and/or `filter`/`AppUserDetails` load path
  - Details: Reject `status == DEACTIVATED` with 403 `Account deactivated`. Verify magic-link redemption + login + MFA verify all covered (Spec FR-5 / EC-8).
- [ ] **Step 3.3:** Capability fields on identity
  - Files: `api/.../dto/user/UserDetailDto.java`, `UserServiceImpl.getCurrent()`
  - Details: Add `passwordEnabled` (= `!usePasswordless`), `mfaEnabled`, `mfaAvailable`. Additive; keep existing fields.
- [ ] **Step 3.4:** Add `confirmation` to masking
  - Files: `api/src/main/resources/application.yaml` (`logging.masking.fields`)
  - Details: Append `confirmation` (otp already present).

### Phase 4: Frontend

#### Design & CSS Sourcing (read before building UI)

**Design source (do NOT re-fetch — already downloaded):**
- `artifacts/stitch_settings_web.png` — mockup; use for layout/spacing/visual reference.
- `artifacts/stitch_settings_web.html` — reference markup; use for structure (bento grid, section order, toggle/quality-selector anatomy) and the source color palette. From Stitch project `11227470478146862224`, screen `d4111b49ad524dee8e0b6096b0f3e75c`.

**CSS source & rule:** The app does **not** use Tailwind. All styling lives in `webapp/src/styles/index.css` as CSS custom properties + `.app-*` classes. **Translate** the Stitch design into those tokens — never import Tailwind or copy `tailwind.config`/utility classes from the Stitch HTML.

**Color mapping (Stitch → app token in `index.css`):**

| Stitch (Tailwind var) | Hex | App token | Status |
|---|---|---|---|
| `background` / `surface` | `#0e0e0e` | `--bg` | ✅ identical |
| `surface-container-low` | `#131313` | `--bg-surface` | ✅ identical |
| `surface-container` | `#1a1919` | `--bg-elevated` | ✅ identical |
| `secondary` (accent) | `#00eefc` | `--accent-cyan` | ✅ identical |
| `primary` (active/like) | `#ff8d89` | `--primary` | ✅ identical |
| `on-surface` | `#ffffff` | `--text` | ✅ identical |
| `on-surface-variant` | `#adaaaa` | `--text-muted` | ✅ identical |
| `error` (Danger Zone) | `#ff7351` | _add_ `--danger` | ⚠️ verify/add |
| `tertiary` (purple, Playback icon) | `#cb9cff` | — | optional accent; reuse `--accent-cyan` if absent |

> Palette is ~90% already in the design system — mostly token reuse, only the danger color needs verifying.

**Class reuse vs new (Step 4.0 builds the new ones):**

| Need | Reuse existing `.app-*` | New CSS required |
|------|--------------------------|------------------|
| Section card | — | `.settings-card` (glass-card: `rgba(38,38,38,0.6)` + `backdrop-filter: blur(24px)`) |
| Buttons | `.app-btn.primary/secondary/danger` | — |
| Text/email inputs | `.app-input`, `.app-label` | — |
| Tabs (if used) | `.app-tabs`, `.app-tab` | — |
| Alerts | `.app-alert.error/success` | — |
| Toggle switch (Notifications/Playback) | — | `.settings-toggle` (pill + knob, on=`--primary`, off=`--bg-elevated`) |
| Quality selector (Auto/1080p/4K) | — | `.settings-segment` (segmented buttons, active=cyan border) |
| Bento grid layout | — | `.settings-grid` (CSS grid, responsive 1→12 cols) |

- [ ] **Step 4.0:** Add Settings CSS to design system
  - Files: `webapp/src/styles/index.css`
  - Details: Verify/add `--danger` token; add `.settings-card`, `.settings-toggle`, `.settings-segment`, `.settings-grid` using existing tokens only. No Tailwind. Mirror visual from `artifacts/stitch_settings_web.png`.

- [ ] **Step 4.1:** API module + re-export
  - Files: `webapp/src/api/settings.js`, `webapp/src/api/index.js`
  - Details: `settingsApi = { get: () => api.get('/user/settings'), patch: (d) => api.patch('/user/settings', d), deleteAccount: (b) => api.delete('/user/account', b) }`. **Check `client.js` supports PATCH + DELETE-with-body**; add methods if missing (Step 4.1a).
- [ ] **Step 4.1a:** Extend Axios client if needed
  - Files: `webapp/src/api/client.js`
  - Details: Ensure `api.patch` exists and `api.delete` can send a JSON body (delete-account needs `otp`/`confirmation`).
- [ ] **Step 4.2:** React Query hooks
  - Files: `webapp/src/hooks/useSettings.js`
  - Details: `useSettings()` → `['settings']`; `useUpdateSettings()` mutation, `onSuccess` writes returned full object into `['settings']`; `useDeleteAccount()` mutation.
- [ ] **Step 4.3:** `SettingsPage` component
  - Files: `webapp/src/components/settings/SettingsPage.jsx`
  - Details: Five sections following `artifacts/stitch_settings_web.{png,html}`, using the `.app-*` + `.settings-*` classes from Step 4.0 (no Tailwind). Each toggle autosaves via PATCH of its single field; revert + `.app-alert.error` on failure. "Change Password" rendered only when `passwordEnabled`. `accountStatus` panel only when present.
- [ ] **Step 4.4:** `DeleteAccountModal`
  - Files: `webapp/src/components/settings/DeleteAccountModal.jsx`
  - Details: If `mfaEnabled` → OTP input; else → type-email-to-confirm input. On success: `logout()` from `useAuth()` + redirect home.
- [ ] **Step 4.5:** Wire into nav
  - Files: `webapp/src/components/layout/AppShell.jsx`
  - Details: Replace the existing `settings` `<ComingSoon>` branch with `activeNav === 'settings' && isLoggedIn ? <SettingsPage/>`. Reuse existing Settings nav button.

### Phase 5: Testing & Docs

- [ ] **Step 5.1:** Service unit tests
  - Files: `api/src/test/java/.../service/impl/UserSettingsServiceImplTest.java`
  - Coverage: lazy-create, atomic-reject, partial update, delete OTP/confirmation/409, anonymization. `@Mock`+`@InjectMocks`, AssertJ; mock `AppUtils.getCurrentUser()` via `mockStatic`.
- [ ] **Step 5.2:** Controller tests
  - Files: `api/src/test/java/.../web/UserSettingsControllerTest.java`
  - Coverage: `@WebMvcTest` + MockMvc; assert `$.status`/`$.data`; rate-limit annotations present; 400/403/409/204 paths.
- [ ] **Step 5.3:** Auth-guard regression test
  - Files: extend `UserServiceImplTest`
  - Coverage: deactivated user blocked on join/magic-link/mfa-verify (EC-8).
- [ ] **Step 5.4:** Frontend tests
  - Files: `webapp/src/components/settings/__tests__/SettingsPage.test.jsx`
  - Coverage: toggle autosave calls PATCH; delete flow calls logout; error alert shows on failure.
- [ ] **Step 5.5:** Docs/tracking
  - Files: `docs/tracking.md` (Plan column), `mkdocs.yml` nav (PRD/ADR/Spec/Plan)

## Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `db/changelog/sql/202606200001-create-user-settings.sql` | Create | `user_settings` table |
| `db/changelog/sql/202606200002-add-user-account-lifecycle.sql` | Create | `users.status`, `users.deleted_at` |
| `entity/UserSettings.java` | Create | 1:1 settings entity |
| `entity/User.java` | Modify | Add `status`, `deletedAt` |
| `enums/UserStatus.java`, `enums/DefaultQuality.java` | Create | New enums |
| `repo/UserSettingsRepository.java` | Create | JPA repo |
| `dto/usersettings/*.java` | Create | Request/response DTOs |
| `service/impl/UserSettingsServiceImpl.java` | Create | Business logic |
| `web/UserSettingsController.java` | Create | Endpoints |
| `dto/user/UserDetailDto.java` | Modify | Capability fields |
| `service/impl/UserServiceImpl.java` | Modify | Deactivated guard + capabilities |
| `config/AppProperties.java`, `application.yaml` | Modify | Feature flag + `confirmation` masking |
| `webapp/src/api/settings.js`, `api/index.js`, `api/client.js` | Create/Modify | API module + PATCH/DELETE-body support |
| `webapp/src/hooks/useSettings.js` | Create | React Query hooks |
| `webapp/src/styles/index.css` | Modify | Add `--danger` + `.settings-card/-toggle/-segment/-grid` (translate Stitch design, no Tailwind) |
| `webapp/src/components/settings/SettingsPage.jsx`, `DeleteAccountModal.jsx` | Create | UI |
| `webapp/src/components/layout/AppShell.jsx` | Modify | Render SettingsPage on `activeNav` |
| `docs/tracking.md`, `mkdocs.yml` | Modify | Tracking + nav |

## Work Breakdown (task tracking — Beads substitute)

| ID | Task | Depends on | Est | Owner |
|----|------|-----------|-----|-------|
| T1 | Migrations (1.1, 1.2) | — | 0.5d | builder |
| T2 | Entities + enums + repo (1.3–1.5, 2.2) | T1 | 0.5d | builder |
| T3 | DTOs + feature flag (2.1, 2.4) | T2 | 0.5d | builder |
| T4 | Service impl (2.3) | T2, T3 | 1.5d | builder |
| T5 | Controller + masking (3.1, 3.4) | T4 | 0.5d | builder |
| T6 | Deactivated guard + capabilities (3.2, 3.3) | T2 | 1d | builder |
| T7 | Frontend API + hooks + client (4.1–4.2) | T5 | 0.5d | builder |
| T7b | Settings CSS / design tokens (4.0) | — | 0.5d | ui-ux-designer / builder |
| T8 | SettingsPage + DeleteModal + nav (4.3–4.5) | T7, T7b | 1.5d | builder |
| T9 | Backend tests (5.1–5.3) | T5, T6 | 1.5d | builder(testing) |
| T10 | Frontend tests (5.4) | T8 | 0.5d | builder(testing) |
| T11 | Security review (deletion/anonymization) | T4, T6 | 0.5d | security-auditor |
| T12 | Docs/tracking/nav (5.5) | T5, T8 | 0.25d | builder |

Critical path: **T1 → T2 → T3/T4 → T5 → T7 → T8 → T10**. T6 parallel after T2. T9/T11 after backend.

## Dependencies

### Code Dependencies

| Package | Version | Purpose |
|---------|---------|---------|
| (none new) | — | All within existing Spring Boot / React / React Query stack |

### Service Dependencies

| Service | Status | Notes |
|---------|--------|-------|
| `Totp` bean | Available | Reused for delete-account OTP step-up |
| `AppUtils.getCurrentUser()` | Available | Current-user resolution |
| Liquibase `includeAll` | Available | Auto-picks new migrations |
| Beads (`bd`) | **Not available** | Use work-breakdown table above |

## Testing Strategy

### Unit Tests

| Component | Test Cases | Status |
|-----------|------------|--------|
| `UserSettingsServiceImpl` | lazy-create (EC-1), atomic invalid reject (EC-2), empty PATCH (EC-4), delete OTP ok/bad (EC-6), confirmation ok/bad (EC-5), already-deactivated 409 (EC-7), anonymization fields cleared | Pending |
| `UserSettingsController` | 200/400/403/409/204 envelopes, rate-limit annotations, masking | Pending |
| `UserServiceImpl` (guard) | deactivated blocked on join/magic/mfa (EC-8) | Pending |

### Integration Tests

| Scenario | Expected Outcome | Status |
|----------|------------------|--------|
| GET then PATCH then GET | second GET reflects persisted partial change | Pending |
| Concurrent single-field PATCH (EC-3) | both fields applied, no error | Pending |
| Full delete flow | 204, row anonymized + DEACTIVATED, token rejected after | Pending |

### Manual Testing

- [ ] Settings page renders all 5 sections; toggles autosave and survive reload.
- [ ] "Change Password" hidden when `app.use-password-less=true`.
- [ ] Delete with MFA prompts OTP; without MFA prompts email confirmation; logs out on success.
- [ ] `accountStatus` hidden when flag off.

## Rollback Plan

1. Revert the two migrations (drop `user_settings`; drop `users.status`/`deleted_at`) — no existing-user backfill was done, so clean.
2. Remove `UserSettingsController` (endpoints disappear) and revert `UserDetailDto`/`UserServiceImpl` guard additions.
3. Revert frontend `AppShell` branch to `<ComingSoon>`.
4. Verify: existing auth/video flows green via `mvn verify` + `npm run test`.

## Risks

| Risk | Mitigation |
|------|------------|
| Deactivated guard misses an auth path → deleted users can still log in | Cover join, magic-link, AND mfa-verify; add explicit EC-8 tests |
| Anonymization leaves PII in related tables/logs | Security review (T11); confirm `@AuditLog` masking incl. `confirmation`; comments key on opaque user id |
| Axios `client.js` lacks PATCH / DELETE-with-body | Step 4.1a adds them before hooks depend on them |
| Coverage gate (+1%) fails due to new untested branches | Tests in Phase 5 target service+controller (DTOs/entities/repo excluded from coverage) |
| Spec/reality drift (route vs activeNav, error envelope) | Captured as Key Decisions; implement to verified reality, not spec literal |

## Checklist

### Before Starting

- [ ] PRD/ADR/Spec approved
- [ ] Branch created from `main` (do NOT commit to `main`)
- [ ] Confirm `app.use-password-less` value in target env (affects password UI)

### Before PR

- [ ] `mvn verify` passes with coverage gate met
- [ ] `npm run test` + `npm run lint` pass
- [ ] All EC-1…EC-10 covered by tests
- [ ] Self-review complete

### Before Merge

- [ ] Code review approved
- [ ] Security review (T11) signed off
- [ ] No merge conflicts

## Notes

- **Spec corrections to fold back** (optional): (a) frontend is `activeNav`-based, not a `/settings` route; (b) error envelope is `{ status:"FAILED", error:{...} }`. Consider a spec v1.1 noting both.
- `incognitoEnabled` is persisted but inert until Watch History (ADR-0013) ships — keep the field, no recording hook yet.
- Beads not installed: if adopted later, convert the Work Breakdown table to `bd` issues with the same dependency edges.

---

## Progress Log

| Date | Update |
|------|--------|
| 2026-06-20 | Initial plan from spec-user-settings; exploration via 4 parallel worker-explorers confirmed backend/test/frontend/cross-cutting patterns |
