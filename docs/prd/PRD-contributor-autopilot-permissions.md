# PRD: Contributor Role and Autopilot Permissions for Reports

<!--
Product Requirements Document
Filename: docs/prd/PRD-contributor-autopilot-permissions.md
Owner: nguyenhuuca
Handoff to: Architect (/architect), UI/UX Designer (/ui-ux-designer)
Related Skills: scope, planning-artifacts
-->

## Overview

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-10-23
**Version:** 1.1
**Tracking:** N/A (added to `docs/tracking.md` after approval)
**PR-FAQ:** N/A. This extends an internal tool ([PRD-in-app-report](PRD-in-app-report.md)); it is not a new user-facing initiative.
**Stakeholders:** nguyenhuuca (owner, SUPER_ADMIN), admins, contributors
**Builds on:** [ADR-0026 In-App Report](../adr/0026-in-app-report.md) (D6 "Future CONTRIBUTOR"), [ADR-0025 Auto Gates](../adr/0025-autopilot-auto-gates.md), [ADR-0015 Bitwise Permissions](../adr/0015-bitwise-permission-system.md)

## Problem Statement

The owner is the bottleneck for every autopilot run that comes from a report.

- Only the SUPER_ADMIN can put `autopilot:auto` or `autopilot:auto-merge` on a report (`ReportServiceImpl.java:103`, ADR-0026 line 67). A report from anyone else gets plain `autopilot`, so the owner must comment `/approve` at Gate 1 and merge at Gate 2 before a fix reaches prod.
- There is no role for a trusted person who should send reports but not manage the site. The only way to give someone the Report menu today is ADMIN, which also opens every admin tab: videos, accounts, jobs and drive sync (`WebSecurityConfig.java:54`, `AppShell.jsx:160-161`).

### Evidence

**Quantitative Evidence:**
- Each plain `autopilot` report needs **2 owner actions** (Gate 1 `/approve`, Gate 2 merge) before it ships (ADR-0022, ADR-0025).
- Admin reports are capped at 10 a day (`ReportServiceImpl.java:59`). At full use, that is up to 20 owner actions a day that this feature can remove for trusted reporters.

**Qualitative Evidence:**
- Owner (2026-10-23): "the role contributor có thể có permission auto, auto-merge dựa trên SA cấp quyền cho … tùy theo permission được cấp mà trigger autopilot full auto hoặc auto."
- ADR-0026 D6 already planned a `CONTRIBUTOR` role between `USER` and `ADMIN` for reports, but kept the auto labels SUPER_ADMIN-only. This PRD revisits that part.

## Goals & Success Metrics

| Goal | Metric | Target |
|------|--------|--------|
| Remove owner clicks for trusted reporters | Share of reports from granted non-SA users that pass both gates with no owner action | ≥ 50% within 30 days of launch |
| Keep prod safe | Reverts or prod incidents traced to an auto / auto-merge run started by a non-SA report | 0 in the first 30 days |
| Least privilege | CONTRIBUTOR accounts that can reach a non-report admin endpoint | 0 (covered by tests) |

## User Stories

### Owner (SUPER_ADMIN)

- As the owner, I want to give a person the CONTRIBUTOR role so that they can send reports without getting the admin tabs.
  - Acceptance: in the account table, the SUPER_ADMIN or an ADMIN can set a USER to CONTRIBUTOR and back. The CONTRIBUTOR sees the Report menu and no Admin menu.
- As the owner, I want to grant or revoke `auto-merge` and `auto` per account (CONTRIBUTOR or ADMIN) so that only people I trust can skip my gates.
  - Acceptance: two toggles in the account's permission editor. Only the SUPER_ADMIN can change them. They are off for every account until granted, including existing admins.
- As the owner, I want a revoke to take effect on the next report so that I can stop someone at once.
  - Acceptance: after a revoke, the next create or retry by that account gets 403 for an auto label, with no re-login needed.

### Contributor / Admin with a grant

- As a granted reporter, I want to pick an autopilot option on the report form, limited to what I was granted, so that my fix ships without waiting for the owner.
  - Acceptance: the form shows "No auto" (default), plus "Auto-merge" if granted `auto-merge` or `auto`, plus "Full auto" if granted `auto`. The issue gets the matching label.
- As a reporter with no grant, I want my report to still start autopilot so that the owner only has to approve.
  - Acceptance: the form shows no autopilot option; the issue gets `autopilot` and the type label, as an ADMIN report does today.

### Contributor

- As a contributor, I want to see all reports and their GitHub state so that I know whether a problem was already reported and handled.
  - Acceptance: the Report list shows every report, as it does for admins today (owner decision, 2026-10-23). The issues are public on GitHub anyway.

## Requirements

### Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | New role `CONTRIBUTOR` between `USER` and `ADMIN` in `UserRole` | Must Have | ADR-0026 D6. Roles are stored as strings and ranked by `outranks`, so inserting it is safe |
| FR-2 | CONTRIBUTOR can call only the report endpoints (create, list, retry). Every other `/admin/**` endpoint stays ADMIN or above | Must Have | The endpoints **stay at `/v1/funny-app/admin/reports`** (owner decision). Today `/admin/**` is `hasAnyRole("ADMIN","SUPER_ADMIN")` (`WebSecurityConfig.java:54`), so `/admin/reports/**` needs its own rule placed before it, and `AdminReportController.java:32` moves to `hasRole('CONTRIBUTOR')` |
| FR-3 | The SUPER_ADMIN **and ADMINs** can set or remove the CONTRIBUTOR role | Must Have | Owner decision. Already allowed by the rank rule: an ADMIN may assign roles lower than its own (`AdminAccountServiceImpl.java:46,105`). An ADMIN still cannot touch the autopilot bits (FR-5) |
| FR-4 | Two new permission bits: `AUTOPILOT_AUTO_MERGE` and `AUTOPILOT_AUTO` | Must Have | `AUTOPILOT_AUTO` implies auto-merge when checked (owner decision Q6). They are stored as independent bits |
| FR-5 | Only the SUPER_ADMIN can set or clear the two autopilot bits, on CONTRIBUTOR or ADMIN accounts | Must Have | `updatePermissions` (`AdminAccountServiceImpl.java:73`) must reject a change to these bits by an ADMIN |
| FR-6 | New bits are **not** granted by default: not by `defaultPermissions()`, not by a role change, and not by a backfill migration of existing admins | Must Have | `UserRole.defaultPermissions()` returns `ALL_BITS` today. `ALL_BITS` must not grow to include them, or a split mask must be used |
| FR-7 | Report create and retry accept `autoLabel` when the caller holds the right bit at request time: `AUTO_MERGE` needs `AUTOPILOT_AUTO_MERGE` or `AUTOPILOT_AUTO`; `AUTO` needs `AUTOPILOT_AUTO`. Otherwise 403 `error.report.autoLabelForbidden` | Must Have | Read from the user row in the DB, not from the JWT claim (`JwtProvider.java:116`), so a revoke is immediate. SUPER_ADMIN keeps every option |
| FR-8 | A CONTRIBUTOR or ADMIN report with no auto label gets `autopilot` + type label (current ADMIN behaviour) | Must Have | Owner decision Q4b |
| FR-9 | Daily cap: one shared cap of **10 per rolling 24 h** across all non-SA reports that carry `autopilot` (ADMIN and CONTRIBUTOR). Over the cap, the issue is created with neither `autopilot` nor an auto label | Must Have | Owner decision Q4c. The cap query and its index count only `reporter_role = 'ADMIN'` today (`ReportServiceImpl.java:197`, `202610200001-create-app-report.sql:27-28`) |
| FR-10 | Report form shows the autopilot options allowed by the caller's bits; no option when there is none | Must Have | `ReportPage.jsx:52,88` shows it only to SUPER_ADMIN today. The server check in FR-7 is the real gate |
| FR-11 | Account table: role choice includes CONTRIBUTOR; permission editor shows the two autopilot toggles to the SUPER_ADMIN only | Must Have | `AdminAccountTable.jsx:11,68`, `utils/permissions.js` |
| FR-12 | Report menu visible to CONTRIBUTOR, ADMIN and SUPER_ADMIN; Admin menu stays ADMIN and above | Must Have | `AppShell.jsx:160-161,382` use `isAdmin` for both today |
| FR-13 | Report list: CONTRIBUTOR sees all reports, like admins | Must Have | Owner decision; no change to `list()` |
| FR-14 | Issue body says who reported by role: "the SUPER_ADMIN", "an ADMIN" or "a CONTRIBUTOR" | Should Have | `ReportIssueContent.java:45`. Still no email, name or id (BR-7) |
| FR-15 | Grant, revoke and role changes are written to the audit log | Must Have | Reuse `@AuditLog`; no new screen (history UI is out of scope) |
| FR-16 | Role change keeps the autopilot bits when the new role is CONTRIBUTOR or ADMIN, and clears them when the new role is USER | Must Have | Owner decision. `updateRole` overwrites the whole mask today (`AdminAccountServiceImpl.java:53`). Keeping prevents an ADMIN from silently wiping a SA grant; clearing on USER stops a dormant grant from coming back on a later promotion |

### Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Performance | Report create p95 stays < 3 s (PRD-in-app-report NFR-1). The permission check adds no extra DB call beyond loading the current user |
| NFR-2 | Security: authZ | Server-side checks for every rule (FR-2, FR-3, FR-5, FR-7); hiding UI is never the check. Tests prove a CONTRIBUTOR gets 403 on every non-report admin endpoint |
| NFR-3 | Security: least privilege | New bits default to off everywhere (FR-6); revocation is effective on the next request (FR-7) |
| NFR-4 | Compatibility | Existing accounts keep their role and current bits; existing JWTs keep working. No change to the headless workflow or its labels |
| NFR-5 | i18n | All new UI strings in `en` and `vi` (ADR-0020) |
| NFR-6 | Coverage | LINE coverage stays ≥ `api/.coverage-threshold` |

## Scope

### In Scope

- `CONTRIBUTOR` role (backend enum, security rules, account table, navigation)
- Two autopilot permission bits, grantable by the SUPER_ADMIN to CONTRIBUTOR and ADMIN accounts
- Report create/retry: auto-label check by bit instead of by role; shared daily cap across ADMIN and CONTRIBUTOR
- Report form and list changes for contributors
- ADR amending ADR-0026 (auto labels from non-owner accounts)

### Out of Scope

- A history screen for grants and revokes (v1 relies on `@AuditLog`)
- Self sign-up or "request access" for contributors; the SUPER_ADMIN assigns role and bits
- Notifying a person when they are granted or revoked
- Auto-revoking a grant after a revert of a run they started
- A per-user cap or a separate cap for auto-labelled reports (owner chose one shared cap)
- Any change to `.github/workflows/autopilot.yml`, the auto-merge script, or the ADR-0025 checks

## Dependencies

| Dependency | Owner | Status | Risk |
|------------|-------|--------|------|
| In-app report (`ReportServiceImpl`, `AdminReportController`, `ReportPage.jsx`) | nguyenhuuca | Live (ADR-0026) | Low |
| Bitwise permissions + `PermissionService` (`Permission.java`, ADR-0015) | nguyenhuuca | Live | Med: `ALL_BITS` and `defaultPermissions()` grant every bit to admins today |
| Harness flag `permission_enforcement_enabled` | nguyenhuuca | **On** in prod since 2026-10-23 13:31 UTC | Med: with the flag off, `@HasPermission` checks are skipped. FR-7 must not depend on that flag |
| Headless autopilot + auto labels (ADR-0022, ADR-0025) | nguyenhuuca | Live | Low: unchanged; it trusts any label on an issue created with the owner's PAT |
| GitHub PAT of the owner on the server (ADR-0026 D1) | nguyenhuuca | Live | Med: every granted account acts as the owner toward autopilot |

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| A granted non-owner account (or a stolen session) ships bad code to prod through `autopilot:auto` | M | H | Grants are per account, off by default, SUPER_ADMIN-only, revocable at once (FR-5/6/7). Headless runs keep plan review, code review, QA ×2 and the auto-merge check (protected paths, LOC limit). Shared daily cap of 10 |
| New bits leak to all admins through `ALL_BITS` / `defaultPermissions()` or the 2026-10-18 backfill pattern | M | H | FR-6, with tests on role change and on `updatePermissions` |
| CONTRIBUTOR reaches other admin endpoints because `/admin/**` is matched by role | M | H | FR-2 and NFR-2 tests over every admin controller |
| Permission checks skipped when the Harness flag is turned off | L | H | The auto-label check is a plain service check, not `@HasPermission`, so it does not depend on the flag |
| Stale JWT claim keeps a revoked grant alive | M | M | FR-7 reads the bits from the DB at request time |
| Prompt injection in a contributor's report text | M | M | Unchanged from ADR-0022 D3: issue text is data, denylist paths are refused, the merge script blocks protected paths |
| Inserting `CONTRIBUTOR` shifts `ordinal()` | L | M | Roles are stored as strings and ranked by `outranks` (ADR-0026 D6). Check for any `ordinal()` use or DB check constraint on `role` |

## Open Questions

- [x] May an ADMIN make a USER a CONTRIBUTOR? **Yes** (FR-3)
- [x] Report endpoint path? **Keep `/admin/reports`** with a path-specific rule (FR-2)
- [x] Can contributors see all reports? **Yes** (FR-13)
- [x] Do the autopilot bits survive a role change? **Kept between CONTRIBUTOR and ADMIN, cleared on USER** (FR-16)

## Appendix

### Mockups/Wireframes

No new screens. Two existing screens change:
- **Report form** (`ReportPage.jsx`): the autopilot select, today SUPER_ADMIN only, is shown with the options the caller is allowed.
- **Account table** (`AdminAccountTable.jsx`): CONTRIBUTOR badge and role choice, plus two "Autopilot" toggles in the permission editor (SUPER_ADMIN only).

### Research

- [PRD-in-app-report](PRD-in-app-report.md): FR-6 (auto labels SUPER_ADMIN only), NFR-8 (future CONTRIBUTOR)
- [ADR-0026](../adr/0026-in-app-report.md): D1 PAT trust model, D3 cap, D6 future CONTRIBUTOR
- [ADR-0025](../adr/0025-autopilot-auto-gates.md): what `autopilot:auto` and `autopilot:auto-merge` do

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product | nguyenhuuca | | Pending |
| Engineering | nguyenhuuca | | Pending |
| Design | N/A (no new screens) | | N/A |

---

## Next Steps & Handoffs

After PRD approval:

1. [ ] **Architect Review**: trust model for non-owner auto labels, report endpoint path, bit layout vs `ALL_BITS`
   - Trigger: `/architect`
   - Output: ADR (`docs/adr/0030-contributor-autopilot-permissions.md`), amending ADR-0026

2. [ ] **UI/UX Designer**: not needed; two existing screens get small changes (see Mockups)

3. [ ] **Spec + sequence diagrams**: `/spec` → `docs/specs/spec-contributor-autopilot-permissions.md`, `docs/sequence/sequence-contributor-autopilot-permissions.md`

4. [ ] **Engineering Estimate**: Implementation Plan (`docs/plans/plan-contributor-autopilot-permissions.md`)

5. [ ] **Track it**: add the feature row to `docs/tracking.md`
   - Then decompose: `/swarm-plan <spec or PRD path>`

**Related Artifacts**:
- ADR: [ADR-0030](../adr/0030-contributor-autopilot-permissions.md) (Accepted)
- Design Spec: N/A
- Implementation Plan: TBD

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-10-23 | nguyenhuuca | Initial draft from `/scope` (Q1–Q6 answered by the owner) |
| 1.1 | 2026-10-23 | nguyenhuuca | Open questions answered: ADMIN may assign CONTRIBUTOR, endpoints stay at `/admin/reports`, contributors see all reports, autopilot bits on role change (FR-16) |
