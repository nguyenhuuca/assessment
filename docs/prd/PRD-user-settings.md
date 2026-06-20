# PRD: User Settings Page

<!--
Product Requirements Document
Filename: docs/prd/PRD-user-settings.md
Owner: Architect (/architect)
Handoff to: Architect (/architect), UI/UX Designer (/ui-ux-designer)
Related Skills: writing-prds, decomposing-tasks, requirements-analysis
-->

## Overview

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-06-20
**Version:** 1.0
**Beads Issue:** N/A
**PR-FAQ:** N/A
**Stakeholders:** Product, Engineering, Design

> **Source design:** Stitch project *Shorts Feed - Web View* (`11227470478146862224`), screen *Settings - Web View* (`d4111b49ad524dee8e0b6096b0f3e75c`). Downloaded assets: `artifacts/stitch_settings_web.png` (mockup) and `artifacts/stitch_settings_web.html` (reference markup). The design is branded "VibeStream"; this PRD adapts it to the Funny Movies app and its actual capabilities.

## Problem Statement

Funny Movies users have **no self-service screen to manage their account or preferences**. Today there is no UI surface to view profile details, manage sign-in/MFA security, control notifications, set playback defaults, manage privacy of their watch history, or delete their account. Any such change requires developer intervention or is simply impossible.

This matters because:
- Users cannot independently secure their account (enable MFA, manage sign-in).
- Preferences (notifications, default playback quality, watch-history privacy) cannot be expressed or persisted, so the product feels static and impersonal.
- Account deletion / data control is a baseline expectation (and a privacy-compliance concern) with no current path.

### Evidence

> **Assumption:** No formal analytics or ticket data was supplied for this assessment feature. The evidence below is illustrative and should be validated before approval.

**Quantitative Evidence:**
- 0 of the existing screens expose any account/preference controls (verified against `webapp/src/`).
- The `User` entity stores only `userName`, `password`, `mfaEnabled`, `mfaSecret`, `role`, `permissions` — no preference fields exist (`api/.../entity/User.java`), so no preference is currently persistable.

**Qualitative Evidence:**
- > **Assumption:** "I enabled MFA once but there's no way to see or change it" — representative user expectation for a streaming product.
- Competitive baseline: every comparable video platform (YouTube, Vimeo) ships an account-settings surface; its absence is a noticeable gap.

## Goals & Success Metrics

| Goal | Metric | Target |
|------|--------|--------|
| Enable self-service account/preference management | % of active users who change ≥1 setting without support contact | ≥ 30% within 60 days of launch |
| Reliable preference persistence | Saved settings correctly applied on next session/device load (no silent loss) | 100% functional correctness; save error rate < 0.5% |
| Responsive settings UX | p95 save round-trip latency | < 300 ms |

## User Stories

### Authenticated User (primary persona)

- As a logged-in user, I want to **view my profile (email/username) and account status**, so that I can confirm who I'm signed in as.
  - Acceptance: Settings page shows the authenticated user's email/username and (display-only) subscription/account status.
- As a logged-in user, I want to **manage my sign-in security (MFA, and password where applicable)**, so that I can keep my account secure.
  - Acceptance: User can enable/disable MFA; security actions require the user to be authenticated and re-verify per existing flows.
- As a logged-in user, I want to **toggle notification preferences**, so that I only receive alerts I care about.
  - Acceptance: Each toggle persists server-side and is reflected on reload.
- As a logged-in user, I want to **set a default playback quality**, so that videos start at my preferred resolution.
  - Acceptance: Selected quality persists and is applied on next playback.
- As a logged-in user, I want to **control the privacy of my watch history** (e.g. Incognito / pause history), so that my activity isn't recorded when I don't want it to be.
  - Acceptance: Toggling privacy stops new watch-history entries while active; ties into the existing watch-history feature.
- As a logged-in user, I want to **delete my account**, so that I can remove my data from the platform.
  - Acceptance: A confirmed, explicit Danger Zone action removes/deactivates the account; behavior governed by the resolution of Open Question OQ-1.

### Admin (secondary persona)

- As an admin, I want account deletions to follow a defined data-handling policy, so that we stay compliant and auditable.
  - Acceptance: Deletion path is audit-logged (`@AuditLog`) and follows the soft/hard-delete decision in OQ-1.

## Requirements

### Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | Display the authenticated user's identity (email/username) read-only | Must Have | From `User.userName` |
| FR-2 | Allow the user to manage sign-in security: enable/disable MFA; expose "Change password" only if applicable to the auth method | Must Have | See risk R-1 (passwordless conflict) |
| FR-3 | Persist and edit **notification preferences** (e.g. new-content alerts, email alerts) | Must Have | Requires new `user_settings` storage; drop design's livestream/DM rows (no backend) |
| FR-4 | Persist and edit a **default playback quality** preference (Auto / 1080p / 4K subset supported by player) | Should Have | New preference field |
| FR-5 | Persist and edit **watch-history privacy** (Incognito / pause history) | Must Have | Integrates with existing watch-history feature |
| FR-6 | Provide a **Delete Account** action behind explicit confirmation (Danger Zone) | Must Have | Behavior per OQ-1; must be audit-logged |
| FR-7 | Display **account/subscription status** as read-only/placeholder | Could Have | No billing backend — static/placeholder in v1 (see R-2) |
| FR-8 | All settings reads/writes scoped to the authenticated user via JWT | Must Have | Authorization in service layer |
| FR-9 | Settings page reachable from primary navigation | Must Have | Matches design's side-nav "Settings" entry |
| FR-10 | "Data Export" request action | Nice to Have | Design shows it; defer implementation unless prioritized |

### Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Performance | p95 settings save/read < 300 ms |
| NFR-2 | Availability | Inherits app SLO (no new external dependency) |
| NFR-3 | Security | JWT-authenticated; user can only read/write own settings; destructive actions confirmed + audit-logged; sensitive fields masked in logs |
| NFR-4 | Persistence | Preferences durable in PostgreSQL via Liquibase-managed schema; survive sessions/devices |
| NFR-5 | Accessibility | Toggles/controls keyboard-navigable and labeled (design uses Material symbols + visible labels) |

## Scope

### In Scope

- **Account Identity** section: view email/username, manage MFA/sign-in security.
- **Notifications** section: persistable notification toggles (realistic subset).
- **Playback & Performance** section: default playback quality preference (display-only for unsupported toggles like hardware acceleration).
- **Privacy & Visibility** section: watch-history privacy / Incognito.
- **Danger Zone**: Delete Account (behavior per OQ-1).
- **Subscription/Account Status**: read-only/placeholder display.
- New `user_settings` storage + Liquibase migration.
- Frontend Settings route/page in `webapp/` matching the Stitch layout (adapted to Funny Movies branding).

### Out of Scope

- Real subscription/billing integration, "View Billing History", payment management (no backend; defer to v2+).
- Livestream invites and Direct Message notifications (features don't exist).
- "Manage Connected Apps" / OAuth app management (no backend).
- Hardware Acceleration / Low Latency Mode as functional toggles (client-only/unsupported — display-only if shown).
- Full GDPR-style Data Export pipeline (FR-10 is a stub unless prioritized).

## Dependencies

| Dependency | Owner | Status | Risk |
|------------|-------|--------|------|
| `User` entity + new `user_settings` schema (Liquibase) | Backend | Not started | Med |
| Existing watch-history feature (for privacy toggle) | Backend | Exists | Low |
| Existing MFA/TOTP flow | Backend | Exists | Low |
| Auth/JWT (`SecurityContextHolder`) | Backend | Exists | Low |
| React settings page + React Query wiring | Frontend | Not started | Low |

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| R-1: Passwordless (magic-link) auth conflicts with design's "Change Password" field | H | M | Replace "Change Password" with "Manage Sign-in / MFA"; only show password controls if the account actually has a password credential |
| R-2: Subscription/billing section has no data source | H | L | Render static/placeholder "Account Status" in v1; gate behind feature flag; defer real billing to v2+ |
| R-3: `user_settings` schema churns as more preferences are added | M | M | Model preferences flexibly (typed columns for v1 set; consider key/value or JSONB if it grows); migrations in `db/changelog/sql/` |
| R-4: Delete Account is irreversible / cascades to watch-history, comments, share-links | M | H | Resolve OQ-1 (soft vs hard delete + cascade policy) before build; require explicit confirmation; audit-log via `@AuditLog` |
| R-5: Design uses "VibeStream" branding & features beyond this app | M | L | Adapt copy/branding to Funny Movies; only build sections with real backing |

## Open Questions

- [ ] **OQ-1:** Delete Account — soft-delete (deactivate + retain) or hard-delete with cascade to watch-history/comments/share-links? Irreversible immediately or grace period?
- [ ] **OQ-2:** Which notification channels are actually deliverable today (email via Spring Mail only?) — defines the realistic notification toggle set.
- [ ] **OQ-3:** Does any account have a real password credential, or is auth purely passwordless? Determines whether "Change Password" ships at all (R-1).
- [ ] **OQ-4:** Should preferences be stored as typed columns or a flexible JSONB/key-value structure (R-3)?
- [ ] **OQ-5:** Is "Data Export" (FR-10) in or out for this release?

## Appendix

### Mockups/Wireframes

- Mockup: `artifacts/stitch_settings_web.png`
- Reference markup: `artifacts/stitch_settings_web.html`
- Source: Stitch project `11227470478146862224`, screen `d4111b49ad524dee8e0b6096b0f3e75c`

### Research

- Existing entity reference: `api/src/main/java/com/canhlabs/funnyapp/entity/User.java`
- Related feature PRDs: `docs/prd/PRD-watch-history.md`, `docs/prd/PRD-bookmark-feature.md`

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product | | | Pending |
| Engineering | | | Pending |
| Design | | | Pending |

---

## Next Steps & Handoffs

After PRD approval:

1. [ ] **Architect Review**: Technical feasibility + `user_settings` schema/API design
   - Trigger: `/architect`
   - Output: ADR (`docs/adr/00NN-user-settings.md`)

2. [ ] **UI/UX Designer**: Adapt Stitch layout to Funny Movies branding & realistic sections
   - Trigger: `/ui-ux-designer`
   - Output: Design Spec

3. [ ] **Engineering Estimate**: Decompose into work items
   - Trigger: `/builder` or `/architect`
   - Output: Implementation Plan (`docs/plans/plan-user-settings.md`)

**Related Artifacts**:
- ADR: [Link after architect review]
- Spec: [Link after `/spec`]
- Implementation Plan: [Link after decomposition]

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-06-20 | nguyenhuuca | Initial draft from Stitch "Settings - Web View" design |
