# ADR-0030: Granted Autopilot Labels for Contributors and Admins

## Metadata

**Status:** Accepted (2026-10-23) · **Date:** 2026-10-23 · **Deciders:** nguyenhuuca · **Tags:** api, security, agents
**Related PRD:** [PRD-contributor-autopilot-permissions](../prd/PRD-contributor-autopilot-permissions.md) · **Amends:** [ADR-0026](0026-in-app-report.md) (auto labels SUPER_ADMIN only, D6 future CONTRIBUTOR) · **Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** ✅ Follows Golden Path (Spring Security roles, bitwise permissions of ADR-0015, Liquibase)

---

## Context

- **Today only the owner can skip the gates from a report.** `autopilot:auto` and `autopilot:auto-merge` are accepted from SUPER_ADMIN only (`ReportServiceImpl.java:103,163`; ADR-0026 "Auto labels"). Every other report needs the owner at Gate 1 and Gate 2. The owner is the bottleneck (PRD Problem Statement).
- **There is no report-only role.** To send a report, a person must be ADMIN, which opens every admin tab (`WebSecurityConfig.java:54`: `/admin/**` → `hasAnyRole("ADMIN","SUPER_ADMIN")`). ADR-0026 D6 planned a `CONTRIBUTOR` role but kept auto labels SUPER_ADMIN-only.
- **The issue identity is always the owner's.** The server creates issues with the owner's PAT (ADR-0026 D1), so the workflow cannot tell who asked for a label. Whatever the server puts on the issue, the headless workflow trusts (ADR-0025). The server is the only place this rule can be enforced.
- **Permission bits today:**
  - five bits, `ALL_BITS = 0b11111` (`Permission.java:15`);
  - every admin gets all of them by default (`UserRole.defaultPermissions()`, `UserRole.java:19-20`);
  - a 2026-10-18 migration backfilled admins to 31 (`202610180003-admin-default-permissions.sql`);
  - a role change overwrites the whole mask (`AdminAccountServiceImpl.java:53`).
- **`@HasPermission` checks are flag-gated.** They run only while the Harness flag `permission_enforcement_enabled` is on (`PermissionServiceImpl.java:71`). It has been on in prod since 2026-10-23, but it can be turned off without a deploy.
- **The JWT carries a copy of the permission mask** (`JwtProvider.java:116`). It is stale until the next login.

---

## Decision Drivers

1. **Remove owner clicks** for people the owner trusts, chosen per account (PRD goal ≥ 50%).
2. **No silent privilege:** nobody gets the power to skip gates by default, by a role change or by a migration.
3. **Instant revoke:** a revoke must stop the next report, without waiting for a re-login.
4. **Not switchable by a feature flag:** turning off the Harness flag must not open the auto labels to everyone.
5. **Least privilege for contributors:** report endpoints only; no other admin tab.
6. **Small change:** keep `/admin/reports`, keep the workflow, keep ADR-0025's checks.

---

## Considered Options

### Option 1: Keep auto labels SUPER_ADMIN-only (status quo, ADR-0026)

| Pros | Cons |
|------|------|
| Smallest trust surface; the owner sees every run before prod | Owner stays the bottleneck: 2 actions per report |
| No code change | Fails driver 1 |

### Option 2: Auto labels by role (e.g. every ADMIN may use them)

| Pros | Cons |
|------|------|
| Simple check: `role >= ADMIN` | All-or-nothing: every admin, present and future, can ship to prod with no review |
| No new bits | Fails driver 2; cannot grant a contributor without making them ADMIN |

### Option 3: Per-account permission bits granted by the SUPER_ADMIN, checked in the service *(chosen)*

Two new bits, `AUTOPILOT_AUTO_MERGE` and `AUTOPILOT_AUTO`, outside the role default mask. Only the SUPER_ADMIN can set them on a CONTRIBUTOR or ADMIN account. The report service checks them against the user row on every create and retry.

| Pros | Cons |
|------|------|
| Per-person trust, off by default (drivers 1, 2) | Two more bits and a second mask to keep apart from `ALL_BITS` |
| DB read at request time, so a revoke is effective at once (driver 3) | A granted account (or its stolen session) can ship to prod through `autopilot:auto` |
| A plain service check, independent of the Harness flag (driver 4) | A role change must preserve or clear the bits on purpose (D4) |

### Option 4: Per-account bits, checked with `@HasPermission`

| Pros | Cons |
|------|------|
| Reuses the existing annotation | Skipped while `permission_enforcement_enabled` is off: everyone could use the auto labels (fails driver 4) |
| | Reads the JWT copy of the mask, so a revoke waits for re-login (fails driver 3) |

---

## Decision Outcome

**Chosen Option:** Option 3, per-account bits granted by the SUPER_ADMIN and checked in the service.

**Rationale:** it is the only option that meets drivers 1–4 together. Options 1 and 2 are the two extremes, and Option 4 fails on the flag and the stale JWT. The accepted trade-off: a non-owner can start an auto run that reaches prod. ADR-0026's comment "prod is still reachable only through `autopilot:auto`, which is SUPER_ADMIN only" no longer holds. Prod is now reachable by the owner and by the accounts the owner names.

### D1. Role `CONTRIBUTOR`

- Inserted in `UserRole` between `USER` and `ADMIN` (ADR-0026 D6).
- `roleHierarchy()` then gives `ADMIN > CONTRIBUTOR > USER`. Roles are stored as strings and ranked by `outranks`, so the shift in `ordinal()` is safe.
- The SUPER_ADMIN and ADMINs may assign and remove it, by the existing rank rule (`AdminAccountServiceImpl.java:46,105`).
- `defaultPermissions()` for CONTRIBUTOR is `0`. A contributor needs no base bit to report.

### D2. Endpoints stay at `/v1/funny-app/admin/reports`

- Owner decision: no path change, no frontend URL change.
- In `WebSecurityConfig`, a matcher for `/v1/funny-app/admin/reports/**` with `hasAnyRole("CONTRIBUTOR","ADMIN","SUPER_ADMIN")` is placed **before** the general `/admin/**` rule.
- `AdminReportController` moves from `hasRole('ADMIN')` to `hasRole('CONTRIBUTOR')`.
- Every other admin controller is unchanged. A test asserts that a CONTRIBUTOR gets 403 on each of them.

### D3. Two permission bits, outside the role default

- `AUTOPILOT_AUTO_MERGE = 1 << 5` and `AUTOPILOT_AUTO = 1 << 6`.
- `ALL_BITS` stays `0b11111` and keeps its meaning: the bits an admin gets by default. A new `AUTOPILOT_BITS` mask holds the two new bits. The valid-mask check in `updatePermissions` accepts `ALL_BITS | AUTOPILOT_BITS`.
- No migration grants them. The 2026-10-18 backfill pattern is not repeated.
- **Who may change them:** only the SUPER_ADMIN. An ADMIN's `updatePermissions` call that would change either bit is refused with 403. An ADMIN may still edit the other five bits as today.
- **What they allow:**
  - `AUTO_MERGE` label: needs `AUTOPILOT_AUTO_MERGE` or `AUTOPILOT_AUTO`;
  - `AUTO` label: needs `AUTOPILOT_AUTO`;
  - SUPER_ADMIN: every label, as today.

### D4. Role changes keep or clear the bits on purpose

`updateRole` sets the new role's default bits, then:
- **keeps** the target account's existing autopilot bits when the new role is CONTRIBUTOR or ADMIN;
- **clears** them when the new role is USER.

Keeping them means an ADMIN cannot silently wipe a grant the SUPER_ADMIN made. Clearing them means a dormant grant cannot come back on a later promotion (owner decision, 2026-10-23).

### D5. The check is in `ReportServiceImpl`, against the DB

- `create` and `retry` already load the caller from the DB (`currentUser()`). The label check reads `permissions` from that row. It does not use the JWT claim or `@HasPermission`.
- The existing pattern is kept: the label stored on the row is applied only when the **current** caller may use it (`callGitHub`, `ReportServiceImpl.java:163`). A retry by someone else, or after a revoke, drops the auto label.
- A label the caller may not use → 403 `error.report.autoLabelForbidden` (same code as today).

### D6. One shared daily cap

- The 10 per rolling 24 h cap now counts every non-SA report that carries `autopilot`: ADMIN and CONTRIBUTOR together (owner decision).
- The count query and the partial index `idx_app_report_autopilot_cap` change from `reporter_role = 'ADMIN'` to `reporter_role IN ('ADMIN','CONTRIBUTOR')`. The index change is a new Liquibase changeset.
- Over the cap, the issue is created without `autopilot` and without an auto label (unchanged).

---

## Consequences

**Positive:**
- Trusted reporters ship small fixes without the owner, so up to 2 owner actions are saved per report.
- Contributors get the Report menu without any other admin power.
- A revoke is effective on the next request, and turning off the Harness flag does not open the labels.

**Negative:**
- The owner-only reach to prod (ADR-0026 "Consequences") is widened to the accounts the owner grants.
- Two kinds of permission bits now exist (role-default and granted). Code that builds or validates masks must use the right one.
- The cap is shared: a busy contributor can use up the daily runs of the admins.

**Risks:**
- **A granted account or stolen session ships bad code to prod.** Mitigations: grants are per account and off by default; headless runs keep plan review, code review, QA ×2 and the auto-merge check (protected paths, ≤ 400 changed lines, ADR-0025); shared cap of 10; instant revoke; rollback is `git revert`.
- **The new bits leak into a default.** Mitigation: tests on `defaultPermissions()`, on role change (D4) and on `updatePermissions` by an ADMIN (D3).
- **A CONTRIBUTOR reaches another admin endpoint through matcher order.** Mitigation: a test sweeps every admin controller with a CONTRIBUTOR token.
- **The cap-index changeset touches a Liquibase changelog**, which the headless auto-merge refuses (ADR-0025 protected paths). So this feature itself must land through a normal, owner-merged run.

---

## Validation

- [ ] Security review of the trust change (`/security-auditor` or `swarm-review` with focus `security`)
- [ ] Tests: CONTRIBUTOR gets 403 on every non-report admin endpoint; ADMIN cannot change the autopilot bits; a role change keeps or clears them per D4; a revoke takes effect with an old JWT; the cap counts ADMIN and CONTRIBUTOR
- [ ] Tech Strategy alignment confirmed (no new library; Liquibase for the index)
- [ ] Related plan document created: `docs/plans/plan-contributor-autopilot-permissions.md`
- [ ] Prod check by the owner: grant `AUTOPILOT_AUTO_MERGE` to a contributor, send a report with "Auto-merge", and see the issue get `autopilot:auto-merge`

---

## Links

- [PRD-contributor-autopilot-permissions](../prd/PRD-contributor-autopilot-permissions.md)
- [ADR-0026 In-App Report](0026-in-app-report.md) (amended)
- [ADR-0025 Auto Gates](0025-autopilot-auto-gates.md)
- [ADR-0022 Headless Autopilot](0022-headless-autopilot.md)
- [ADR-0015 Bitwise Permission System](0015-bitwise-permission-system.md)
- Implementation Plan: TBD

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-10-23 | nguyenhuuca | Initial draft |
| 2026-10-23 | nguyenhuuca | Accepted |
