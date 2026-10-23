# Sequence Diagrams: Contributor Role and Autopilot Permissions for Reports

- **PRD:** [PRD-contributor-autopilot-permissions](../prd/PRD-contributor-autopilot-permissions.md)
- **ADR:** [0030-contributor-autopilot-permissions](../adr/0030-contributor-autopilot-permissions.md)
- **Spec:** [spec-contributor-autopilot-permissions](../specs/spec-contributor-autopilot-permissions.md)
- **Status:** As-built (2026-10-23)

## 1. SUPER_ADMIN grants an autopilot bit

The owner gives a contributor `AUTOPILOT_AUTO_MERGE` (bit 32) in the account table. Spec BR-5, BR-6.

```mermaid
sequenceDiagram
    autonumber
    actor SA as SUPER_ADMIN
    participant FE as Browser (AdminAccountTable.jsx)
    participant API as AdminController
    participant S as AdminAccountServiceImpl
    participant DB as PostgreSQL (users)
    participant C as credentialsVersionCache

    SA->>FE: toggle "Autopilot: auto-merge" on a CONTRIBUTOR row
    FE->>API: PATCH /v1/funny-app/admin/accounts/{id}/permissions {permissions: 32}
    API->>S: updatePermissions(id, actorId, 32)
    S->>S: mask ⊆ ALL_BITS | AUTOPILOT_BITS (127)
    S->>DB: SELECT role of actor, target
    DB-->>S: actor SUPER_ADMIN, target CONTRIBUTOR
    S->>S: autopilot bits changed → actor is SUPER_ADMIN ✔ (BR-5)<br/>target role is CONTRIBUTOR or ADMIN ✔ (BR-6)
    S->>DB: UPDATE users SET permissions = 32, credentials_version + 1
    S->>C: evict(id)
    S-->>API: AdminAccountDto
    API-->>FE: 200 ResultObjectInfo<AdminAccountDto>
    FE-->>SA: row shows the toggle on, admin accounts query invalidated
```

## 2. ADMIN tries to change an autopilot bit

Spec BR-5, EC-4.

```mermaid
sequenceDiagram
    autonumber
    actor A as ADMIN
    participant API as AdminController
    participant S as AdminAccountServiceImpl
    participant DB as PostgreSQL (users)

    A->>API: PATCH /admin/accounts/{id}/permissions {permissions: 64}
    API->>S: updatePermissions(id, actorId, 64)
    S->>DB: SELECT actor, target
    DB-->>S: actor ADMIN, target CONTRIBUTOR (current mask 0)
    S->>S: autopilot bits differ (0 → 64) and actor ≠ SUPER_ADMIN
    S-->>API: CustomException 403 "Only the SUPER_ADMIN can change autopilot permissions"
    API-->>A: 403 ResultErrorInfo (nothing saved)
```

## 3. Contributor sends a report with "Auto-merge" (happy path)

Spec BR-8, BR-11, FR-2, FR-3, FR-6.

```mermaid
sequenceDiagram
    autonumber
    actor U as CONTRIBUTOR (mask 32)
    participant FE as Browser (ReportPage.jsx)
    participant SEC as WebSecurityConfig
    participant API as AdminReportController
    participant S as ReportServiceImpl
    participant DB as PostgreSQL
    participant GH as GitHub REST API
    participant WF as autopilot.yml (headless)

    U->>FE: open Report, select shows No auto + Auto-merge (allowedAutoLabels)
    U->>FE: Send, autoLabel = AUTO_MERGE
    FE->>SEC: POST /v1/funny-app/admin/reports (JWT)
    SEC->>SEC: /admin/reports/** → hasAnyRole(CONTRIBUTOR, ADMIN, SUPER_ADMIN) ✔
    SEC->>API: create(request)
    API->>API: @PreAuthorize hasRole('CONTRIBUTOR') ✔, @RateLimited 5/h, @AuditLog
    API->>S: create(request)
    S->>DB: load current user (role, permissions)
    DB-->>S: CONTRIBUTOR, 32
    S->>S: allowsAutoLabel(32, AUTO_MERGE) ✔ (BR-8)
    S->>DB: INSERT app_report (FAILED, 'pending', auto_label AUTO_MERGE)
    S->>DB: COUNT CREATED autopilot reports, role IN (ADMIN, CONTRIBUTOR), last 24 h
    DB-->>S: 4 (< 10, not capped)
    S->>GH: POST /issues labels [autopilot, autopilot:auto-merge, bug]<br/>body "…Reported in-app by a CONTRIBUTOR…"
    GH-->>S: issue 71
    S->>DB: UPDATE app_report SET status CREATED, issue 71, labels
    S-->>API: ReportDto
    API-->>FE: 200 ResultObjectInfo<ReportDto>
    FE-->>U: Issue 71 created
    GH-)WF: issues.labeled autopilot (sender = owner PAT)
    WF-)WF: plan → Gate 1 owner /approve → build → Gate 2 auto-merge (ADR-0025)
```

## 4. Contributor asks for a label above the grant

Spec BR-9.

```mermaid
sequenceDiagram
    autonumber
    actor U as CONTRIBUTOR (mask 32)
    participant API as AdminReportController
    participant S as ReportServiceImpl
    participant DB as PostgreSQL

    U->>API: POST /admin/reports {autoLabel: AUTO} (crafted request, the UI never offers it)
    API->>S: create(request)
    S->>DB: load current user
    DB-->>S: CONTRIBUTOR, 32
    S->>S: allowsAutoLabel(32, AUTO) ✘
    S-->>API: 403 error.report.autoLabelForbidden
    API-->>U: 403 ResultErrorInfo (no row saved, no GitHub call)
```

## 5. Retry after a partial revoke (label downgrade)

Spec BR-10, EC-1.

```mermaid
sequenceDiagram
    autonumber
    actor U as CONTRIBUTOR
    actor SA as SUPER_ADMIN
    participant API as AdminReportController
    participant S as ReportServiceImpl
    participant DB as PostgreSQL
    participant GH as GitHub REST API

    Note over U,GH: Report R was saved FAILED with auto_label AUTO while U held mask 64
    SA->>DB: (flow 1) permissions 64 → 32, credentials_version + 1
    U->>API: POST /admin/reports/{R}/retry (new JWT after re-login)
    API->>S: retry(R)
    S->>DB: load current user, load R with reporter
    DB-->>S: CONTRIBUTOR mask 32, R FAILED, auto_label AUTO, reporter = U
    S->>S: reporter = caller ✔, cap AUTO to what 32 allows → AUTO_MERGE
    S->>GH: POST /issues labels [autopilot, autopilot:auto-merge, bug]
    GH-->>S: issue 72
    S->>DB: UPDATE R: CREATED, issue 72 (auto_label stays AUTO)
    S-->>API: ReportDto
    API-->>U: 200
```

## 6. Contributor on another admin endpoint

Spec BR-2, EC-8.

```mermaid
sequenceDiagram
    autonumber
    actor U as CONTRIBUTOR
    participant SEC as WebSecurityConfig
    participant API as AdminController

    U->>SEC: GET /v1/funny-app/admin/accounts (JWT)
    SEC->>SEC: not /admin/reports/** → /admin/** needs ADMIN or SUPER_ADMIN ✘
    SEC-->>U: 403 (controller never reached)
```

## 7. Role change keeps or clears the bits

Spec BR-7, EC-5, EC-6.

```mermaid
sequenceDiagram
    autonumber
    actor X as SUPER_ADMIN or ADMIN
    participant API as AdminController
    participant S as AdminAccountServiceImpl
    participant DB as PostgreSQL (users)

    X->>API: PATCH /admin/accounts/{id}/role {role}
    API->>S: updateRole(id, actorId, role)
    S->>DB: SELECT actor, target
    S->>S: actor outranks old and new role
    alt role = ADMIN or CONTRIBUTOR
        S->>S: mask = role.defaultPermissions() | (old & AUTOPILOT_BITS)
    else role = USER
        S->>S: mask = 0 (autopilot bits cleared)
    end
    S->>DB: UPDATE users SET role, permissions = mask, credentials_version + 1
    S-->>API: ok
    API-->>X: 200 "Role updated"
```
