# Sequence Diagrams

Mermaid sequence diagrams per feature. **Every feature with a PRD has one** (`docs/sequence/sequence-<feature>.md`, slug = PRD slug), written from `templates/artifacts/sequence.template.md` by `/spec` or `/swarm-plan` before the plan, and kept in sync with the code (update the diagram in the same commit when a flow changes).

Older platform flows (login, MFA, streaming, video job, deployment) are PlantUML images in [Architecture → Sequence Diagrams](../architecture/sequence-diagrams.md).

| Feature | Diagrams | PRD |
|---------|----------|-----|
| Admin Video Import | [sequence-admin-video-import](sequence-admin-video-import.md) | [PRD](../prd/PRD-admin-video-import.md) |
| Bookmark / Library Collections | [sequence-bookmark-feature](sequence-bookmark-feature.md) | [PRD](../prd/PRD-bookmark-feature.md) |
| Contributor Autopilot Permissions | [sequence-contributor-autopilot-permissions](sequence-contributor-autopilot-permissions.md) | [PRD](../prd/PRD-contributor-autopilot-permissions.md) |
| HLS Streaming | [sequence-hls-streaming](sequence-hls-streaming.md) | [PRD](../prd/PRD-hls-streaming.md) |
| Hot Video Priority | [sequence-hot-video-priority](sequence-hot-video-priority.md) | [PRD](../prd/PRD-hot-video-priority.md) |
| i18n (Vietnamese + English) | [sequence-i18n](sequence-i18n.md) | [PRD](../prd/PRD-i18n.md) |
| In-App Report | [sequence-in-app-report](sequence-in-app-report.md) | [PRD](../prd/PRD-in-app-report.md) |
| Password Login | [sequence-password-login](sequence-password-login.md) | [PRD](../prd/PRD-password-login.md) |
| React Migration (SPA runtime) | [sequence-react-migration](sequence-react-migration.md) | [PRD](../prd/PRD-react-migration.md) |
| User Notifications | [sequence-user-notifications](sequence-user-notifications.md) | [PRD](../prd/PRD-user-notifications.md) |
| User Settings | [sequence-user-settings](sequence-user-settings.md) | [PRD](../prd/PRD-user-settings.md) |
| Video Suggestions | [sequence-video-suggestions](sequence-video-suggestions.md) | [PRD](../prd/PRD-video-suggestions.md) |
| Watch History (v2) | [sequence-watch-history-v2](sequence-watch-history-v2.md) | [PRD](../prd/PRD-watch-history-v2.md) |

The superseded Watch History PRDs (baseline, Opus) have no diagrams; see v2.
