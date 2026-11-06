# Plan: Edit video title and description (admin Vault tab)

- **Status:** Done (autopilot run 2026-11-06-video-edit-details, issue #86, PR #87)
- **Scope:** small feature, no ADR. Promoted from lite mode because the non-test diff reached ~140 LOC.

## Facts

- `VideoSource` has `title` and description in field `desc`; both columns are TEXT, so no migration (`api/src/main/java/com/canhlabs/funnyapp/entity/VideoSource.java:47-51`).
- Pattern followed: `updatePriority` and `PATCH /videos/{id}/priority` (`AdminVideoServiceImpl.java`, `AdminController.java`). `/admin/**` is ADMIN-only.
- Modal pattern: `webapp/src/components/admin/CronEditModal.jsx`.
- `StreamVideoServiceImpl.updateDesc()` (scheduled) fills every null or empty description with a generated poem, so a cleared description is regenerated. Accepted and shown as a hint in the modal.

## Tasks

| ID | Task | Acceptance criteria |
|----|------|---------------------|
| ED-1 | `AdminVideoService.updateDetails` + `PATCH /admin/videos/{id}/details` (`@AuditLog`) | Title required, trimmed, 1..200; description max 2000, blank stored as null; unknown id throws; unchanged: no save; feed cache invalidated on change; null body and non-string values rejected |
| ED-2 | `AdminVideoDto.description` | `GET /admin/videos` returns `description`; JSON contract fixture updated |
| ED-3 | `api/admin.js` `updateVideoDetails` + `useUpdateVideoDetails` | Hook calls the endpoint and refreshes `['admin','videos']` |
| ED-4 | `VideoEditModal` + "Edit" row action + en/vi i18n | Opens with current values; Save disabled when blank, unchanged or pending; Esc/backdrop close; server error shown |
| ED-5 | Tests | Service, controller, permission (403 for USER), modal, table |

Out of scope: bulk edit, other fields, non-admin editing, a marker for admin-cleared descriptions.
