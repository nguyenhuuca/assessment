# Implementation Plan: Migrate Frontend to React

**Status:** Ready for execution
**Estimated effort:** 14–17 days
**Dependencies:** ADR-0006 accepted, PRD-react-migration approved
**Handoff:** Use `/swarm-execute` with this plan

---

## Summary

Migrate `webapp/` from ~4,400 lines of vanilla JS + jQuery to React 19 + Vite.
**No backend changes.** Feature parity only — no new features.
Migration follows a bottom-up approach: infrastructure → auth → video core → social → layout → CI/CD → QA.

---

## Phase 1: Foundation (3 days)

### Task 1: Initialize Vite + React project
**Acceptance criteria:**
- `webapp/` contains a working Vite + React 19 project
- `npm run dev` starts local dev server
- `npm run build` produces `webapp/dist/`
- Old files (`js/`, `index.html`, `css/`) moved to `webapp/legacy/` (kept for reference, not deployed)

**Steps:**
1. Run `npm create vite@latest . -- --template react` inside `webapp/`
2. Install dependencies: `npm install react-router-dom @tanstack/react-query react-bootstrap bootstrap react-swipeable @fortawesome/react-fontawesome @fortawesome/free-solid-svg-icons @fortawesome/free-brands-svg-icons`
3. Install dev deps: `npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom`
4. Create project structure (see below)
5. Move legacy files: `mkdir legacy && mv index.html js/ css/ manifest.json legacy/`
6. Copy `manifest.json` to `public/manifest.json`
7. Copy icons to `public/icons/`

**Project structure:**
```
webapp/
├── public/
│   ├── manifest.json
│   └── icons/
├── src/
│   ├── api/          # API client + endpoint functions
│   ├── components/   # Reusable UI components
│   │   ├── auth/     # Login, MFA, Profile, MagicLink
│   │   ├── video/    # VideoPlayer, VideoSwiper, VoteButtons
│   │   ├── comments/ # CommentPanel, CommentList, CommentInput
│   │   ├── modals/   # ShareModal, DeleteModal
│   │   └── layout/   # Header, Tabs, AppShell
│   ├── contexts/     # AuthContext, ThemeContext
│   ├── hooks/        # useVideos, useComments, useVote, useSwipe
│   ├── utils/        # Video model, category classifier, formatting
│   ├── styles/       # assessment.css (migrated), theme vars
│   ├── App.jsx
│   └── main.jsx
├── legacy/           # Old vanilla JS files (reference only)
├── package.json
├── vite.config.js
└── vitest.config.js
```

---

### Task 2: API client layer
**Acceptance criteria:**
- All 14 API endpoints implemented as async functions
- JWT + guestToken headers injected automatically
- Error shape normalized: `{ message, status }` on failure
- `REACT_APP_API_BASE_URL` (or `VITE_API_BASE_URL`) used for base URL

**Steps:**
1. Create `src/api/client.js` — base Fetch wrapper with auth headers
2. Create `src/api/auth.js` — join, me, verifyMagic, mfa/setup/verify/enable/disable
3. Create `src/api/videos.js` — list, privateList, share, delete
4. Create `src/api/comments.js` — list, post, delete
5. Create `src/api/guestToken.js` — guest token management

**Key implementation note:** Replace `$.ajaxSetup()` global headers with a `createHeaders()` util that reads from `localStorage` each call (mirrors current behavior).

---

### Task 3: Environment config
**Acceptance criteria:**
- `VITE_API_BASE_URL` sets API base URL
- `.env.local` for local dev (points to `http://localhost:8081/v1/funny-app`)
- Production uses `https://canh-labs.com/api/v1/funny-app`

**Steps:**
1. Create `webapp/.env.example` with `VITE_API_BASE_URL=...`
2. Create `webapp/.env.local` for local dev (gitignored)
3. Update `vite.config.js` — no proxy needed (CORS handled by Spring Boot)

---

## Phase 2: Auth Module (3 days)

### Task 4: AuthContext + useAuth hook
**Acceptance criteria:**
- JWT, user, guestToken stored in/read from localStorage
- `useAuth()` provides: `{ user, isLoggedIn, login, logout, setGuestToken }`
- Session validation on app mount (calls `GET /user/me`)
- All components read auth via `useAuth()` — no direct localStorage reads

**Steps:**
1. Create `src/contexts/AuthContext.jsx`
2. `AuthProvider` wraps app, initializes state from localStorage
3. `useEffect` on mount: validate JWT via `GET /user/me`; clear if invalid
4. Expose: `login(jwt, user)`, `logout()`, `setGuestToken(token)`, `user`, `isLoggedIn`
5. Create `src/hooks/useAuth.js` consuming context

---

### Task 5: LoginForm component
**Acceptance criteria:**
- Email input → POST /user/join
- Handles `INVITED_SEND` status (shows "check email" message)
- Handles `MFA_REQUIRED` status (opens MFA modal)
- Loading spinner during request
- Error message display

**Component:** `src/components/auth/LoginForm.jsx`

---

### Task 6: MagicLink handler
**Acceptance criteria:**
- On app load, check `?token=` URL param
- If present, call `GET /user/verify-magic?token=<token>`
- On success: store JWT + user, redirect to `/`
- On failure: show error message

**Component:** `src/components/auth/MagicLinkHandler.jsx` (rendered at route `/`)

---

### Task 7: MFA modal + Profile modal
**Acceptance criteria:**
- **MFA Login Modal**: OTP input → POST /user/mfa/verify → login on success
- **Profile Modal** tabs:
  - User Info tab: email, member since, last login (read-only)
  - MFA Setup tab: show QR code, verify code to enable, or disable with code
- All existing form validation behavior preserved

**Components:**
- `src/components/auth/MFALoginModal.jsx`
- `src/components/auth/ProfileModal.jsx`

---

## Phase 3: Video Core (4 days)

### Task 8: Video model + category classifier
**Acceptance criteria:**
- `Video` class with same `determineCategory()` logic (keyword matching: funny/regular)
- `useVideos(category)` hook: React Query query for `GET /video-stream/list`
- `usePrivateVideos()` hook: React Query query for `GET /private-videos` (auth required)
- Videos sorted by upvotes (matching current `sort` logic)

**Files:**
- `src/utils/videoModel.js` — Video class + category logic
- `src/hooks/useVideos.js` — React Query wrappers

---

### Task 9: VideoPlayer component
**Acceptance criteria:**
- HTML5 `<video>` with `autoPlay`, `playsInline`, `preload="auto"`
- Controls: play/pause toggle, mute toggle, progress bar (seekable)
- Loading spinner shown on `loadstart`/`waiting` events, hidden on `canplay`
- Center play icon shown when paused
- Expandable description overlay with "See more" toggle
- Video ends → callback to advance to next

**Component:** `src/components/video/VideoPlayer.jsx`
**Key:** Use `useRef` for video element — do NOT use React state for video playback controls (causes rerenders/flicker)

---

### Task 10: VideoSwiper component
**Acceptance criteria:**
- Displays current video (index-based)
- Left/right swipe navigation via `react-swipeable`
- Keyboard left/right arrow navigation
- Navigation feedback animation (flash left/right)
- Touch events don't conflict with video controls
- Auto-advance on video end

**Component:** `src/components/video/VideoSwiper.jsx`
**Key:** Maintain `currentIndex` in React state; video array from `useVideos()` hook

---

### Task 11: VoteButtons component
**Acceptance criteria:**
- Up vote / down vote buttons
- Visual state: active/inactive icon toggle (matching current `fa-thumbs-up` logic)
- Vote state persisted per video in component state (resets on unmount)
- `POST /video/like` and `POST /video/unlike` on click

**Component:** `src/components/video/VoteButtons.jsx`

---

### Task 12: VideoTabs + VideoFeed
**Acceptance criteria:**
- Three tabs: Popular, Funny, Private
- Private tab hidden unless `isLoggedIn`
- Clicking Private tab loads private videos (lazy load on tab click)
- Each tab has its own `VideoSwiper` with independent index state

**Components:**
- `src/components/layout/VideoTabs.jsx`
- `src/components/video/VideoFeed.jsx`

---

## Phase 4: Social Features (2 days)

### Task 13: CommentPanel component
**Acceptance criteria:**
- Slides in from right (CSS animation matching current `slideInRight`)
- Loads comments via `GET /videos/{id}/comments`
- Displays avatar (initials + hashCode color, same logic as current)
- Comment time formatting (relative, matching current `formatCommentTime`)
- Post comment via `POST /videos/{id}/comments`
- Delete own comment (DELETE with confirmation)
- Enter key sends comment
- Mobile keyboard avoidance (panel repositions when virtual keyboard appears)
- Comment badge count on video action button

**Component:** `src/components/comments/CommentPanel.jsx`

---

### Task 14: ShareModal component
**Acceptance criteria:**
- YouTube URL input, title, description, private checkbox
- POST /share-links on submit
- Loading spinner during submit
- Clears form on modal close
- Success: closes modal, new video appears in feed (React Query invalidation)

**Component:** `src/components/modals/ShareModal.jsx`

---

### Task 15: DeleteConfirmModal component
**Acceptance criteria:**
- "Are you sure?" confirmation
- DELETE /share-links/{id} on confirm
- On success: remove video from UI (React Query mutation)

**Component:** `src/components/modals/DeleteConfirmModal.jsx`

---

## Phase 5: Layout & Theme (1.5 days)

### Task 16: ThemeContext + useTheme hook
**Acceptance criteria:**
- Dark/light theme toggle
- Persists to localStorage
- Reads OS preference (`prefers-color-scheme`) as fallback
- Sets `data-theme` attribute on `<html>` (same as current)
- Moon/sun icon toggle

**Files:**
- `src/contexts/ThemeContext.jsx`
- `src/hooks/useTheme.js`

---

### Task 17: AppShell (Header + Layout)
**Acceptance criteria:**
- App title + icon
- Global message banner (`showMessage` equivalent)
- Email input + Login button (shown when not logged in)
- Share button, Profile button, Logout button (shown when logged in)
- Welcome message with email
- Theme toggle button (top-right)
- VideoTabs below header

**Component:** `src/components/layout/AppShell.jsx`

---

## Phase 6: CI/CD Update (1 day)

### Task 18: Update deploy-web.yml
**Acceptance criteria:**
- Workflow installs Node.js 20
- Runs `npm ci` in `webapp/`
- Runs `npm run build` in `webapp/`
- SCPs `webapp/dist/` to server (not `webapp/`)
- Server receives deployable static files in Nginx document root

**Changes to `.github/workflows/deploy-web.yml`:**
1. Add step: `actions/setup-node@v4` with Node.js 20
2. Add step: `npm ci` in `webapp/`
3. Add step: `npm run build` in `webapp/`
4. Update SCP source from `webapp/` to `webapp/dist/`

---

### Task 19: Nginx SPA routing
**Acceptance criteria:**
- `try_files $uri $uri/ /index.html` configured for React Router
- No 404 on page refresh with client-side routes

**Note:** Verify with ops team if Nginx config needs updating on server; may be out of scope for this PR.

---

## Phase 7: Testing & QA (2 days)

### Task 20: Unit tests for auth flows
**Acceptance criteria:**
- AuthContext: login, logout, session restoration
- MagicLink handler: token extraction + API call
- API client: header injection, error normalization

---

### Task 21: Unit tests for video components
**Acceptance criteria:**
- VideoPlayer: play/pause/mute controls
- VideoSwiper: left/right navigation, auto-advance
- VoteButtons: toggle state, API calls

---

### Task 22: Integration smoke tests
**Acceptance criteria:**
- App renders without errors
- Tab switching shows correct content
- Share modal opens/closes/clears

---

## Task Dependency Graph

```
T1 (Vite setup)
  ├── T2 (API client)
  │     └── T4 (AuthContext) ──── T5, T6, T7
  │           └── T8 (Video model) ──── T9, T10, T11, T12
  │                                          └── T13, T14, T15
  ├── T3 (Env config)
  └── T16 (ThemeContext)
          └── T17 (AppShell) ──── all components integrated

T12 + T13 + T14 + T15 + T16 + T17 → T18 (CI/CD)
T17 → T19 (Nginx)
T18 → T20, T21, T22 (Tests)
```

---

## Files Changed

| File | Change |
|------|--------|
| `webapp/package.json` | New — Vite + React project |
| `webapp/vite.config.js` | New — Vite configuration |
| `webapp/src/` | New — all React source files |
| `webapp/public/` | New — static assets |
| `webapp/legacy/` | New — moved vanilla JS files (reference) |
| `.github/workflows/deploy-web.yml` | Update — add Node build step |

## Files NOT Changed

| File | Reason |
|------|--------|
| `api/` (all backend) | No backend changes required |
| `helm/` | No Helm chart changes |
| `doc/adr/0004-frontend-structure.md` | ADR-0006 supersedes, do not delete |

---

## Definition of Done

- [ ] `npm run build` succeeds with no errors or warnings
- [ ] All 14 API calls function correctly against production API
- [ ] Feature parity verified: auth, video, comments, theme, modals
- [ ] `npm test` passes all unit tests
- [ ] CI pipeline deploys built React app via SCP
- [ ] Mobile swipe navigation tested on real device
- [ ] ADR-0006 updated to "Accepted"
- [ ] Tech strategy updated: JavaScript section updated to React + Vite
