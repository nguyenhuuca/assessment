# Code Review — webapp/src

**Date:** 2026-04-04
**Health Score: C** | **Critical: 6** | **Total Issues: 28**

---

## Critical Issues

| # | File | Issue | Remediation |
|---|------|-------|-------------|
| C-1 | `components/layout/VideoTabs.jsx` | **Dead component** — exported but never imported anywhere; duplicates tab logic already in `AppShell.jsx` | Delete the file |
| C-2 | `components/auth/MFALoginModal.jsx` | **Bootstrap remnant** — still uses `Modal`, `Button`, `Spinner`, `Alert` from react-bootstrap; visually inconsistent | Migrate to `app-modal-*` / `app-btn` pattern |
| C-3 | `components/modals/DeleteConfirmModal.jsx` | **Bootstrap remnant** — same as above | Migrate to custom design system |
| C-4 | `components/video/VideoFeed.jsx:38` | `className="spinner-border text-light"` — Bootstrap class | Replace with `<div className="vid-spinner" />` |
| C-5 | `components/comments/CommentPanel.jsx:95` | `className="spinner-border-sm"` — Bootstrap class | Same fix |
| C-6 | `package.json` | `@fortawesome/*` packages installed but **never imported** anywhere in `src/` | Remove 3 packages |

---

## SOLID Violations

| Principle | File | Description | Remediation |
|-----------|------|-------------|-------------|
| SRP | `AppShell.jsx:26-268` | 7 useState, renders header + sidenav + mobile nav + FAB + all 4 modals + feeds | Extract `useModalState()` hook; split `<AppHeader>`, `<MobileNav>` |
| SRP | `ProfileModal.jsx` | MFA lifecycle (setup→QR→enable→disable) mixed with user info display | Extract `<MFASettings>` sub-component |
| OCP | `AppShell.jsx:184-207` | Three hardcoded `activeTab === '...'` blocks — adding a tab requires modifying code | Add `component` field to `TABS` config and render data-driven |
| OCP | `useVideos.js` | `useVideos` and `usePrivateVideos` are near-identical; new feed = copy-paste | Refactor into `useVideoQuery(key, fetcher)` factory |
| DIP | `videoModel.js:3` `buildStreamUrl` | Reads `localStorage` directly inside a utility function — untestable | Accept `{ jwt, guestToken }` as params; move `localStorage` reads to call sites |
| DIP | `MFALoginModal` + `DeleteConfirmModal` vs `ShareModal` + `ProfileModal` | Two incompatible modal systems coexist | Standardise on custom `app-modal-*` system |

---

## DRY Violations

| Type | Files | Pattern | Remediation |
|------|-------|---------|-------------|
| **Knowledge** | `ShareModal.jsx:7-14`, `ProfileModal.jsx:5-14` | Identical `Spinner` component defined twice | Extract to `src/components/common/Spinner.jsx` |
| **Knowledge** | `VideoPlayer.jsx:65`, `videoModel.js:32` | Poster URL `https://images.canh-labs.com/${fileId}.jpg` hardcoded in two places | `mapApiVideo` already sets `poster`; remove from `VideoPlayer` |
| **Knowledge** | `LoginForm.jsx:27-28`, `MagicLinkHandler.jsx:24` | `localStorage.setItem('user', ...)` written outside `AuthContext` in 2 places | Add `setPendingMfaUser()` action to `AuthContext` |
| **Knowledge** | `ProfileModal.jsx:41-42`, `MFALoginModal.jsx:13-14` | Same 6-digit OTP validation logic + same error message duplicated | Extract `isValidOtp(code)` + `OTP_ERROR_MSG` constant |

---

## Code Smells & React Anti-patterns

| Smell | File:Line | Description | Remediation |
|-------|-----------|-------------|-------------|
| Data Clump | `AppShell.jsx` → `VideoSwiper.jsx` | `{ onShowComments, onDeleteVideo, currentUser }` always travel together through 2 layers | Bundle into `videoActions` context or object prop |
| Feature Envy | `VideoPlayer.jsx:65-69` | Slices `video.userShared` and constructs poster URL — data formatting that belongs in `mapApiVideo` | Add `handle` field to `mapApiVideo()` |
| Stale Placeholder | `ProfileModal.jsx:122-123` | "Member Since" and "Last Login" always show **today's date** | Wire up real fields from user object or remove rows |
| `window.confirm` | `CommentPanel.jsx:60` | Blocks main thread, untestable in jsdom | Replace with inline confirm state in component |
| Unused ref | `CommentPanel.jsx:37` | `inputRef` declared, attached to textarea, but never read | Use it to auto-focus on panel open, or remove |
| setTimeout leak | `AppShell.jsx:39-41` | 5s timeout has no cleanup on unmount | Clear in `useEffect` cleanup |
| Optimistic state drift | `VoteButtons.jsx:11-12` | Local count state drifts from React Query cache on refetch | Derive displayed value as `video.upvotes + localDelta` |
| eslint-disable | `AuthContext.jsx:39`, `MagicLinkHandler.jsx:34` | `react-hooks/exhaustive-deps` suppressed — hides stale closure bugs | Fix dependency arrays instead of silencing |
| Silent errors | `VoteButtons.jsx:29,41`, `CommentPanel.jsx:55,63` | `catch {}` swallows errors silently, no rollback | Roll back optimistic update on error; surface to user |

---

## Consistency Issues

| Area | Finding | Recommendation |
|------|---------|----------------|
| Async style | `AuthContext.jsx`, `MagicLinkHandler.jsx` use `.then().catch()`; everything else uses `async/await` | Standardise on `async/await` + `try/catch` |
| Inline styles | `AppShell.jsx`, `VideoPlayer.jsx`, `CommentPanel.jsx` bypass CSS classes with inline styles | Follow `ProfileModal`/`ShareModal` pattern — use `.app-*` classes |
| CSS tokens | `CommentPanel.jsx:78,87,115` uses `--bg-secondary` and `--text-primary` which **don't exist** in `index.css` | Fix to `--bg-elevated` and `--text`; light mode won't work otherwise |
| Import style | Some files import from `api/index.js` barrel, others import directly | Pick one style and enforce it |
| Error throwing | `api/client.js:21` throws plain object literal | `throw Object.assign(new Error(msg), { status })` to preserve stack traces |
| CLAUDE.md accuracy | `webapp/CLAUDE.md` says "Axios" but `client.js` uses native `fetch` | Update CLAUDE.md |

---

## Prioritised Fix List

| Priority | Action |
|----------|--------|
| 🔴 **Now** | Delete `VideoTabs.jsx` (dead code) |
| 🔴 **Now** | Fix undefined CSS tokens in `CommentPanel.jsx` (`--bg-secondary` → `--bg-elevated`, `--text-primary` → `--text`) |
| 🔴 **Now** | Remove `@fortawesome/*` from `package.json` |
| 🔴 **Now** | Migrate `MFALoginModal` + `DeleteConfirmModal` off Bootstrap; replace `spinner-border` in `VideoFeed` + `CommentPanel` |
| 🟡 **Soon** | Extract shared `Spinner` component; remove duplicate poster URL in `VideoPlayer` |
| 🟡 **Soon** | Fix `CommentPanel` `window.confirm` → inline confirmation |
| 🟡 **Soon** | Add `setTimeout` cleanup in `AppShell.showMsg` |
| 🟢 **Later** | Refactor `useVideos`/`usePrivateVideos` into factory hook |
| 🟢 **Later** | Extract `AppShell` modal state into `useModalState()` hook |
| 🟢 **Later** | Fix suppressed `exhaustive-deps` in `AuthContext` + `MagicLinkHandler` |
