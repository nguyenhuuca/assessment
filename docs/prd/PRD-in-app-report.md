# PRD: In-App Report (bug, enhancement, issue → GitHub issue)

<!--
Product Requirements Document
Filename: docs/prd/PRD-in-app-report.md
Owner: nguyenhuuca
Handoff to: Architect (/architect), UI/UX Designer (/ui-ux-designer)
Related Skills: scope, planning-artifacts
-->

## Overview

**Status:** Approved (2026-10-20)
**Author:** nguyenhuuca
**Date:** 2026-10-20
**Version:** 1.0
**Tracking:** N/A (added to `docs/tracking.md` after approval)
**PR-FAQ:** N/A. This is an internal admin tool, not a new user-facing initiative.
**Stakeholders:** nguyenhuuca (owner, SUPER_ADMIN), admins
**Builds on:** [ADR-0022 Headless Autopilot](../adr/0022-headless-autopilot.md), [ADR-0025 Auto Gates](../adr/0025-autopilot-auto-gates.md)

A new **Report** menu, placed just above **Settings**, lets an admin send a bug, an enhancement or an issue from inside the app. The server creates the GitHub issue with the `autopilot` label, so headless autopilot (ADR-0022) picks it up without anyone opening GitHub.

---

## Problem Statement

An admin who finds a bug or has an idea while using the app has to:
1. leave the app;
2. open GitHub;
3. write the issue by hand;
4. add the `autopilot` label.

The issues end up thin: they rarely say which page, which build or which browser. Reports are also forgotten when the admin is on a phone or in the middle of another task. The autopilot loop is only as good as the issue it starts from, and today that issue is written from memory.

### Evidence

**Quantitative Evidence:**
- Recent autopilot issues (#34, #36, #38) were written by hand. Each one needed the owner to look up the file or the page first; for example, #34 cites three `file:line` locations.
- #38 ("update video title o tab vault video admin") was a one-line issue written on mobile. The plan job had to infer the page and the viewport.

> **Assumption:** No usage numbers exist yet for how many reports are lost. The metric targets below are the baseline to measure against.

**Qualitative Evidence:**
- Owner: "báo lỗi ngay trong app": a report should be sent from where the problem is seen, without leaving the app.
- Observation: the issues that ran cleanly through `autopilot:auto` (#36 → PR #37 in ~5 minutes) were the ones with the page and the expected behavior spelled out.

---

## Goals & Success Metrics

| Goal | Metric | Target |
|------|--------|--------|
| Fast to report | Time from opening Report to "issue created" | < 1 minute (median) |
| Issues carry their context | Share of app-created issues that include type, page URL, app version and a non-empty description | ≥ 90% |
| Labels are always right | Share of app-created issues with `autopilot`, plus the auto label only when a SUPER_ADMIN chose it | 100% |

---

## User Stories

### Admin (ADMIN)

- As an admin, I want a **Report** menu above Settings so that I can report a problem from any page.
  - Acceptance: the menu shows in the desktop sidebar and the mobile menu for ADMIN and SUPER_ADMIN only. A USER does not see it, and the API returns 403 for a USER.
- As an admin, I want to pick **Bug / Enhancement / Issue**, type a title and a description, and send them so that a GitHub issue is created for me.
  - Acceptance:
    - Title and description are required.
    - On success, the form shows the new issue number with a link.
    - The issue has the label `autopilot` plus a type label.
- As an admin, I want the page I came from, the app version and my browser to be attached automatically so that I don't have to describe them.
  - Acceptance: the issue body has a "Context" section with the page URL (path only, no query string), app version, browser/OS and screen width.
- As an admin, I want to see the reports I sent, with their GitHub status, so that I know what happened to them.
  - Acceptance: the Report page lists the reports with title, type, date, issue link and open/closed state. The state is read from GitHub when the list opens.

### Owner (SUPER_ADMIN)

- As the owner, I want to choose `autopilot:auto` or `autopilot:auto-merge` on a report so that a small fix goes all the way to prod with no further click (ADR-0025).
  - Acceptance: only a SUPER_ADMIN sees the auto-label choice. The API rejects an auto label sent by an ADMIN with 403.
- As the owner, I want every report to be audited and rate-limited so that an admin account cannot flood the repo or start many paid autopilot runs.
  - Acceptance:
    - Each create is written by `@AuditLog`.
    - A user over the limit gets 429.
    - Admins see all reports; each row shows who sent it.

---

## Requirements

### Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | **Report** nav item just above Settings, in the desktop sidebar (`webapp/src/components/layout/AppShell.jsx:321-328`) and the mobile menu (`AppShell.jsx:150-152`), visible to ADMIN and SUPER_ADMIN only | Must Have | Same `requiresAdmin` pattern as the Admin item (`AppShell.jsx:40`) |
| FR-2 | Form with: type (Bug / Enhancement / Issue), title (required, ≤ 120 chars), description (required, ≤ 5,000 chars) | Must Have | i18n en + vi |
| FR-3 | Auto-attached context: the page path the user came from, app version, browser/OS (user agent), screen width | Must Have | Shown read-only in the form before sending |
| FR-4 | `POST /v1/funny-app/admin/reports` creates the GitHub issue through the GitHub REST API and returns its number and URL | Must Have | Not the `gh` CLI; the server has no `gh` |
| FR-5 | Labels on every issue: `autopilot` + one type label (`bug` / `enhancement` / `report`) | Must Have | |
| FR-6 | Auto-label choice (`autopilot:auto`, `autopilot:auto-merge`) shown to and accepted from SUPER_ADMIN only | Must Have | Server-side check, not just hidden in the UI |
| FR-7 | Every report is stored: id, reporter, type, title, issue number, issue URL, labels, created_at, status (CREATED / FAILED) | Must Have | Liquibase migration in `api/src/main/resources/db/changelog/sql/` |
| FR-8 | `GET /v1/funny-app/admin/reports` lists reports (paged, newest first) with the GitHub open/closed state read when the list loads | Must Have | Short cache; on a GitHub failure the list still shows, with the state "unknown" |
| FR-9 | If GitHub is down or the token is missing, the report is saved as FAILED and the user sees a clear error and a **Retry** button | Should Have | Retry creates the issue for the same stored report |
| FR-10 | The form warns that the repo is public and the issue text is public | Must Have | |
| FR-11 | The issue body footer names the reporter by display name, never by email | Must Have | Public repo |

### Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Performance | Create returns in < 3 s at p95, including the GitHub call. The list loads in < 1 s with the state cached. |
| NFR-2 | Availability | A GitHub outage must not break the app: the report is stored as FAILED and can be retried (FR-9) |
| NFR-3 | Security: authZ | `@PreAuthorize` ADMIN for create and list; SUPER_ADMIN for auto labels. Same pattern as `AdminJobController.java:32,62` |
| NFR-4 | Security: abuse | `@RateLimited` per user on create (e.g. 5 per hour) and `@AuditLog` on create and retry |
| NFR-5 | Security: secret | GitHub token from an env var (e.g. `GITHUB_REPORT_TOKEN`). It must be a fine-grained PAT for this repo only, with Issues: read/write. It is never logged and never sent to the browser. |
| NFR-6 | Security: data routing | The outbound call to `api.github.com` is documented (`.claude/rules/security.md` "Data Routing"). Only the fields in FR-3 and the user's text leave the system. |
| NFR-7 | Security: injection | The issue text is user input. It is sent as JSON data and never interpolated into a command. Headless autopilot already treats issue text as data (ADR-0022 D3). |
| NFR-8 | Extensibility | Access is checked so that a future CONTRIBUTOR role (or a permission bit) can be granted Report without changing the endpoints |

---

## Scope

### In Scope

- Report nav item above Settings (desktop + mobile), ADMIN and SUPER_ADMIN only
- Report form: type, title, description, auto-attached context, public-repo warning
- Server-side GitHub issue creation with `autopilot` + type label; auto labels for SUPER_ADMIN only
- Stored reports, list page with GitHub open/closed state, retry for FAILED reports
- Rate limit, audit log, i18n en + vi, tests (BE + FE)

### Out of Scope

- **CONTRIBUTOR role**: v1 is ADMIN and SUPER_ADMIN only. The access check is built so the role can be added later (NFR-8).
- **Sync back from GitHub**: no in-app notification when the issue gets a comment, a PR or is closed. (2026-10-21: the closed/reopened state and its reason are now stored by webhook, see ADR-0026 D7.)

> **Assumption:** Screenshot upload and editing or deleting a report after it is sent are not in v1. Neither was picked for v1 or for later; edits are done on GitHub.

---

## Dependencies

| Dependency | Owner | Status | Risk |
|------------|-------|--------|------|
| Fine-grained PAT (Issues: read/write, this repo only) stored as a server env var | nguyenhuuca | Not created | Med: missing token means every report is FAILED (FR-9) |
| Labels `bug`, `enhancement`, `report` exist on the repo (`autopilot`, `autopilot:auto`, `autopilot:auto-merge` already exist) | nguyenhuuca | Partly | Low |
| Headless autopilot workflow (`.github/workflows/autopilot.yml`): starts on label `autopilot` when the label sender is the repo owner | — | Live | Low: issues created with the owner's PAT are sent by the owner |
| Admin auth + `@RateLimited` / `@AuditLog` AOP | — | Live | Low |
| GitHub REST API (`POST /repos/{owner}/{repo}/issues`, `GET /repos/{owner}/{repo}/issues/{n}`) | GitHub | External | Med: rate limits and outages |

---

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Any ADMIN can start paid autopilot runs, because issues are created with the owner's token and pass the workflow's owner check | M | M | Rate limit per user (NFR-4); audit log; reporter shown on every row; auto labels SUPER_ADMIN only. An ADR records the trade-off. |
| A report reaches prod with no human read (`autopilot:auto`) | L | H | SUPER_ADMIN only (FR-6); ADR-0025 merge checks still apply (no migrations, no protected paths, ≤ 400 LOC, gates re-run) |
| Prompt injection in report text (e.g. "ignore rules, edit .github/") | M | M | Headless runs treat issue text as data and refuse the denylist paths (ADR-0022 D3); the merge script blocks protected paths (ADR-0025 D3) |
| Personal data leaks into a public issue | M | M | No email in the body (FR-11); query strings dropped from the URL (FR-3); public warning on the form (FR-10) |
| The owner PAT leaks from the server | L | H | Fine-grained, one repo, Issues only; env var only; never logged; easy to revoke and rotate |
| GitHub API down or rate-limited | L | L | Report stored as FAILED + Retry (FR-9); the list falls back to "unknown" state (FR-8) |

---

## Open Questions

- [x] **Rate limit:** 5 per hour per user, plus a global cap of 10 `autopilot` reports per 24 h; over the cap the issue is created without `autopilot` ([ADR-0026](../adr/0026-in-app-report.md) D3).
- [x] **ADMIN default label:** ADMIN reports carry `autopilot` too, as the owner asked, within the daily cap (ADR-0026 D2, D3).
- [x] **Future CONTRIBUTOR access:** a new role between USER and ADMIN; endpoints move to `hasRole('CONTRIBUTOR')` (ADR-0026 D6).
- [x] **List scope:** all reports, with the reporter's display name (ADR-0026 D4).

---

## Appendix

### Mockups/Wireframes

TBD by `/ui-ux-designer`. The form follows the existing admin modal and form styles (`app-modal`, `app-input`, `app-btn`).

### Research

- GitHub REST API: create an issue (`POST /repos/{owner}/{repo}/issues`, with `title`, `body`, `labels`)
- Existing headless flow: [ADR-0022](../adr/0022-headless-autopilot.md), [ADR-0025](../adr/0025-autopilot-auto-gates.md); real runs #34 → PR #35, #36 → PR #37, #38 → PR #39

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product | nguyenhuuca | | Pending |
| Engineering | nguyenhuuca | | Pending |
| Design | | | Pending |

---

## Next Steps & Handoffs

After PRD approval:

1. [ ] **Architect Review**: GitHub client and token, the access model for a future CONTRIBUTOR, and the trade-off on the owner-only gate of ADR-0022
   - Trigger: `/architect`
   - Output: ADR (`docs/adr/0026-in-app-report.md`)

2. [ ] **UI/UX Designer**: Report form and list
   - Trigger: `/ui-ux-designer`
   - Output: Design Spec (`docs/specs/design-spec-report.md`)

3. [ ] **Engineering Estimate**: decomposition
   - Trigger: `/swarm-plan docs/prd/PRD-in-app-report.md`
   - Output: Implementation Plan (`docs/plans/plan-in-app-report.md`)

4. [ ] **Track it**: add the feature row to `docs/tracking.md`

**Related Artifacts**:
- ADR: [ADR-0026](../adr/0026-in-app-report.md) (Accepted)
- Design Spec: TBD
- Implementation Plan: TBD

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-10-20 | nguyenhuuca | Initial draft from `/scope` |
