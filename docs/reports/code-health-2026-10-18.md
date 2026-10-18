# Codebase Health Report — 2026-10-18

Scope: `api/src/main/java` (291 files, ~16k LOC) and `webapp/src` (122 files, ~10k LOC).
Method: `/code-check all` — 4 parallel read-only reviewers (backend quality, backend DRY/consistency/security,
frontend quality, frontend DRY/dead code), jscpd 4.0.5, ESLint. The top findings were re-checked by hand.

## Executive Summary

**Health Score**: B
**Critical Issues**: 0
**High**: 5 · **Medium**: 17 · **Low**: ~20

Quality gates are clean: ESLint 0 errors / 3 warnings, no `synchronized`, no string-built SQL,
all `/admin/**` controllers carry class-level `@PreAuthorize`, yt-dlp runs with `--` and a host allowlist,
no `dangerouslySetInnerHTML`. Duplication is low (jscpd: backend 1.83%, webapp 2.45%, half of it in tests).
The debt is concentrated in a few large classes/components and in small rules repeated across files.

## Fix first (High + security Medium)

| # | Area | File:Line | Finding | Remediation |
|---|------|-----------|---------|-------------|
| 1 | BE bug | `service/impl/UserServiceImpl.java:96-97` | `loadUserByUsername` dereferences `user` without a null check; an unknown username throws NPE (500) instead of `UsernameNotFoundException` | Null-check, throw `UsernameNotFoundException`; regression test |
| 2 | BE error mapping | `service/impl/VideoCommentServiceImpl.java:183,203` + `exception/RestExceptionHandler.java` | Service throws `NoSuchElementException` / `SecurityException`; no handler maps them, so comment not-found / not-owner returns 500 | Throw `CustomException` / `UnauthorizedException` (or add 404/403 handlers) |
| 3 | BE security (CWE-306) | `web/ThreadController.java`, `utils/AppConstant.java:54` | Leftover debug endpoints `/thread/name`, `/thread/load` are public; `/load` sleeps 1 s per call with no rate limit | Delete controller + whitelist entry |
| 4 | BE security (CWE-312) | `service/impl/VideoCommentServiceImpl.java:110,197-198` | Guest token stored in plaintext in column `guest_token_hash`, compared with `String.equals` | Store SHA-256, compare with `MessageDigest.isEqual`, migrate rows |
| 5 | FE API | `webapp/src/api/admin.js:3-26` | Own `fetch`/headers for PATCH bypasses `client.js`: no `Accept-Language`, no `TOKEN_REVOKED` → `AUTH_REVOKED_EVENT`, no 204 handling | Use `api.patch` from `client.js` |
| 6 | FE data | `components/comments/CommentPanel.jsx:22-33,82` | Comments fetched in `useEffect` (no cancel → race when switching videos, no cache); delete error swallowed by `catch {}` | `useQuery(['comments', id])` + `useMutation`; show the error |
| 7 | FE a11y | `components/video/VideoPlayer.jsx:178,298-347` | Seek bar, description and play toggle are clickable `div`/`p` with no keyboard or ARIA | `<input type="range">` or `role="slider"`; buttons |
| 8 | FE SRP | `components/layout/AppShell.jsx` (449 lines, ~15 `useState`) | Deep links, modals, nav, feed, mute, banner and layout in one component; nav is state-driven, not routes | Extract hooks (`useDeepLink`, `useFlashMessage`) and `TopHeader`/`SideNav`/`MobileNav` |

## SOLID Violations

| Principle | File:Line | Description | Remediation |
|-----------|-----------|-------------|-------------|
| SRP | `service/impl/UserServiceImpl.java:44-267` | UserDetailsService + MFA + passwordless + JWT + DTO building; 8 setter-injected deps | Split `MfaService` / `TokenService`; constructor injection |
| DIP | `UserServiceImpl.java:50,65`; `VideoSuggestionServiceImpl.java:70,246-251` | Injects `InviteServiceImpl`, `VideoImportWorker`; reads constants from `VideoImportServiceImpl` | Depend on interfaces; shared `ImportErrorCode` |
| SRP | `service/impl/DriveSyncServiceImpl.java:56,237-258` | Holds Google `Drive` SDK + query building + run-state tracking | `DriveFolderClient` in `client/`; separate run tracker |
| SRP | `service/impl/VideoImportWorker.java:42-376` | Poll/claim, semaphore, job run, persistence, disk checks, cleanup | Extract `JobWorkspace`, `JobStateUpdater` |
| SRP | `web/AdminController.java:75-225` | Videos + accounts + comments in one controller | Split per domain |
| OCP | `client/YtDlpClientImpl.java:416-430`, `DriveSyncServiceImpl.java:213-218` | String error codes mapped by if-chains | `ImportErrorCode` enum with status + match rules |

## DRY Violations

| Type | Files | Pattern | Remediation |
|------|-------|---------|-------------|
| Knowledge | `VideoCollectionServiceImpl:54`, `VideoSuggestionServiceImpl:51,318`, `WatchHistoryServiceImpl:22,59`, `NotificationController:38,54` | `MAX_PAGE_SIZE = 50` ×4, clamping written 3 ways | `PageUtils.clamp` + one constant |
| Knowledge | `VideoCollectionServiceImpl:274`, `VideoCommentServiceImpl:177`, `WebSecurityConfig:54`, `@PreAuthorize` strings | "is admin" = ADMIN or SUPER_ADMIN spelled out 4× | `UserRole.isAdmin()` |
| Knowledge | 8+ services (`VideoImportServiceImpl:276`, `AdminCommentServiceImpl:277`, …) | `private static CustomException error(...)` copy-pasted, mixed English text vs i18n keys | One `ServiceErrors` factory, i18n keys only |
| Knowledge | `AdminVideoImportController:95`, `AdminVideoSuggestionController:78`, `NotificationController:93` vs `CommentController` | `ok()` helper copied 3×, hand-built responses elsewhere | `ResultObjectInfo.ok(data)` |
| Knowledge (FE) | ~10 files (`client.js:13`, `admin.js:7`, `AuthContext.jsx:12,104`, `watchHistory.js:4`, `LoginForm.jsx:47`, `MagicLinkHandler.jsx:26`) | `localStorage` keys `jwt`/`user`/`guestToken` read/written directly; MFA hand-off duplicated | `authStorage` module + `AuthContext.beginMfa` |
| Knowledge (FE) | `hooks/useSuggestions.js:7-18`, `useWatchHistory.js:9-20`, `useCollections.js:8` | `unwrap` ×3, `normalizePage` ×2 | `utils/pagination.js` |
| Knowledge (FE) | `AdminAccountTable.jsx:10,49,61`, `AppShell.jsx:130,151,304,360`, `AddToCollectionModal.jsx:16` | Role checks inline although `utils/roles.js` exists | Use `roles.js` |
| Knowledge (FE) | `commentUtils.js:18`, `importUtils.js`, `i18n/format.js`, `watchHistoryUtils.js:26` | Two different `formatTime` + duration helper beside `i18n/format.js` | Route through `i18n/format.js` |
| Incidental (FE) | Admin tables (Suggestion/Account/Video/Comment/Import) | Pager + "showing X of Y" markup ×5; two hand-rolled modal shells | `<AdminPager>`, shared `Modal` |

## Code Smells

| Smell | Location | Severity | Suggestion |
|-------|----------|----------|------------|
| Uncleared timer | `AppShell.jsx:139-142` (`showMsg`) | Medium | One timer ref; clear before set and on unmount |
| Uncleared timer / global listener | `VideoSwiper.jsx:45-62` | Medium | Throttle in `useRef`; listen on the container, not `window` |
| Uncleared timers | `CommentPanel.jsx:37`, `AdminVideoTable.jsx:77`, `SuggestionCard.jsx:30` | Low | Cleanup in effect |
| Large component / prop drilling | `LibraryView.jsx` (476 lines, 7 components; `muted`/`onMutedChange`/`currentUser` through 4 levels) | Medium | Split files; `PlaybackContext` |
| Derived state | `VideoPlayer.jsx:70-104`; `LibraryView.jsx:255-258` (page clamp effect) | Low | Assign refs in effects; compute `safePage` |
| Feature envy | `VideoCollectionServiceImpl.java:347-377` (`toDtos`), actor checks in 9 methods | Low | `CollectionMapper`, `ActorContext` |
| Wrong log | `FfmpegServiceImpl.java:28-33` | Low | Logs "Thumbnail generated" after a non-zero exit; add `else`. Also drain stdout |
| Dead deps | `webapp/package.json:17-20` | Medium | 4 `@fortawesome/*` packages never imported (UI uses Material Symbols); remove or update `tech-strategy.md` |
| Dead code | `YtDlpClientImpl.java:113-118` | Low | Stray `{ }` block |

## Consistency Issues

| Area | Finding | Recommendation |
|------|---------|----------------|
| Security config | `WebSecurityConfig.java:54-58` ends in `anyRequest().permitAll()`; non-admin endpoints rely on per-method checks | Deny by default + explicit public list |
| Validation | `Contract`/`ContractDSL` (project convention) used in few files; most services hand-roll if/throw | `@Valid` in controllers, `Contract` in services |
| Errors (FE) | Errors shown as inline `e.message` (~21 places), no shared display | One error-display hook/component |
| a11y (FE) | `SettingsPage.jsx` labels without `htmlFor`, unnamed `Toggle` switches; admin modals without focus trap/Escape; pager arrows without `aria-label` | Fix labels; shared `Modal` |
| Logging (CWE-532) | Full emails at INFO/WARN in `MailServiceImpl:96,134,152`, `UserServiceImpl:158`, `InviteServiceImpl:56` | Mask or log user id |
| Styling | Heavy inline `style={{}}` (LibraryView 25, AppShell 24, SettingsPage 21) and hard-coded colours | Move to `index.css` classes + CSS variables |
| Token storage | JWT + guest token in `localStorage` (XSS-readable; no innerHTML sinks today) | Longer term: httpOnly SameSite cookie |
| URLs | `UserController` and `UserSettingsController` both own `/user` | Document or merge |

## Complexity Hotspots

| File | Function | Size | Action |
|------|----------|------|--------|
| `YtDlpClientImpl` | `run` | ~58 lines, ~10 branches, 7 params | Extract `ProcessWatchdog`, `RunOptions` record |
| `VideoImportWorker` | `runJob` / `drain` | ~50 / ~36 lines | Split workspace + state updates |
| `YtDlpClientImpl` | `fetchMetadata` | ~48 lines | `acquireMetadataPermit`, `parseMetadata` |
| `DriveSyncServiceImpl` | `startRun` / `listFolder` | ~38 / ~31 lines | Move Drive query to client |
| `VideoCollectionServiceImpl` | `update` / `toDtos` | ~35 / ~30 lines | Mapper + actor context |
| `AdminCommentServiceImpl` | `hardDelete` | ~30 lines, query per tree level | Recursive CTE in repo or `collectReplies` |

## Verified clean

No `synchronized`; native queries all bound with `:param`; yt-dlp/ffmpeg use argument arrays (no shell);
import URLs validated against an https host allowlist; every admin controller protected; React Query keys
consistent with matching invalidations; confirm dialogs all via `useConfirm`; no orphan FE files;
i18n namespaces used consistently. Lint warnings: `SearchModal.jsx:64` (missing dep), `AuthContext.jsx:137`
and `ThemeContext.jsx:30` (react-refresh).
