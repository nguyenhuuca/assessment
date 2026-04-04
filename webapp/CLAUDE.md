# CLAUDE.md — webapp (React Frontend)

This file provides guidance to Claude Code when working with the `webapp/` directory.

## Stack

| Component     | Choice                              |
|---------------|-------------------------------------|
| Framework     | React 19                            |
| Build Tool    | Vite 6                              |
| Routing       | react-router-dom v7                 |
| Data Fetching | @tanstack/react-query v5            |
| Swipe         | react-swipeable                     |
| Icons         | Material Symbols Outlined (CDN)     |
| Fonts         | Bebas Neue (brand), DM Sans (body)  |
| Testing       | Vitest + @testing-library/react     |
| HTTP Client   | Axios (via `src/api/client.js`)     |

> **No Tailwind, no styled-components.** UI is built with CSS custom properties defined in `src/styles/index.css`.

## Commands

```bash
npm run dev      # Dev server (Vite HMR)
npm run build    # Production build → dist/
npm run preview  # Preview production build
npm run test     # Run Vitest tests
npm run lint     # ESLint
```

## Project Structure

```
webapp/
├── index.html                     # Entry HTML; loads Google Fonts + Material Symbols CDN
├── vite.config.js
├── src/
│   ├── main.jsx                   # React root; QueryClientProvider, App
│   ├── App.jsx                    # Router + AppShell
│   ├── styles/
│   │   └── index.css              # Design system: CSS tokens, all component classes
│   ├── api/
│   │   ├── client.js              # Axios instance (baseURL from VITE_API_BASE_URL)
│   │   ├── auth.js                # join, me, mfa.* endpoints
│   │   ├── videos.js              # list, privateList, share, delete, like, unlike
│   │   ├── comments.js            # list, add comments
│   │   ├── guestToken.js          # guest token init/storage
│   │   └── index.js               # re-exports
│   ├── contexts/
│   │   ├── AuthContext.jsx        # JWT + user state, login/logout/updateUser
│   │   └── ThemeContext.jsx       # dark/light theme toggle
│   ├── hooks/
│   │   ├── useAuth.js             # consumes AuthContext
│   │   ├── useTheme.js            # consumes ThemeContext
│   │   └── useVideos.js           # React Query hooks: useVideos(category), usePrivateVideos()
│   ├── utils/
│   │   └── videoModel.js          # mapApiVideo(), buildStreamUrl(), determineCategory()
│   └── components/
│       ├── layout/
│       │   └── AppShell.jsx       # Fixed header, desktop side nav, mobile bottom nav, modals
│       ├── auth/
│       │   ├── LoginForm.jsx      # Email magic-link form (inline in header)
│       │   ├── MagicLinkHandler.jsx
│       │   ├── MFALoginModal.jsx
│       │   └── ProfileModal.jsx   # User info + MFA setup/disable tabs
│       ├── video/
│       │   ├── VideoFeed.jsx      # PublicFeed / PrivateFeed (React Query → VideoSwiper)
│       │   ├── VideoSwiper.jsx    # Index state, swipe/keyboard nav, action column, preloader
│       │   ├── VideoPlayer.jsx    # <video> element, progress bar, mute, metadata overlay
│       │   └── VoteButtons.jsx    # Like / Dislike with heartbeat animation
│       ├── comments/
│       │   ├── CommentPanel.jsx   # Slide-in comment panel
│       │   └── index.js
│       └── modals/
│           ├── ShareModal.jsx     # Share video form (custom styled, no Bootstrap)
│           └── DeleteConfirmModal.jsx
```

## Design System (`src/styles/index.css`)

All styling uses CSS custom properties. Never use Bootstrap classes for layout or color — only the custom classes defined here.

### Color Tokens
```css
--bg:            #0e0e0e   /* page background */
--bg-surface:    #131313   /* modal/card surface */
--bg-elevated:   #1a1919
--accent-cyan:   #00eefc   /* primary accent, active states, progress bar */
--primary:       #ff8d89   /* like/voted state */
--text:          #ffffff
--text-muted:    #adaaaa
--border:        rgba(255,255,255,0.05)
```

### Key CSS Classes
| Class | Purpose |
|-------|---------|
| `.action-btn` | 56px circular glass button (right action sidebar) |
| `.action-col` | Right-side action column in VideoSwiper |
| `.nav-arrows` | Prev/next nav button group |
| `.app-modal-backdrop` | Modal overlay (blur + dark) |
| `.app-modal` | Modal dialog box |
| `.app-btn.primary/secondary/danger/success` | Themed buttons |
| `.app-input` | Styled text/textarea input |
| `.app-label` | Form field label |
| `.app-alert.error/success` | Inline alert messages |
| `.app-tabs` / `.app-tab` | Tab navigation |
| `.mobile-nav` | Mobile bottom navigation bar |
| `.desktop-sidenav` | Desktop left sidebar (hidden on mobile) |
| `.progress-track` / `.progress-fill` | Cyan glow video progress bar |
| `.vid-spinner` | Video loading spinner |
| `.fab` | Floating action button |
| `.topnav-brand` | Header brand (hidden on mobile) |
| `.topnav-tabs` | Header tab nav (hidden on mobile) |

## Responsive Layout

- **Desktop (>768px)**: fixed side nav (88px), video in 9:16 card with action column beside it
- **Mobile (≤768px)**: full-screen video, action buttons overlay on right edge, bottom nav, no side nav

## API Integration

Base URL set via env var: `VITE_API_BASE_URL` (defaults to `https://canh-labs.com/api/v1/funny-app`).

Auth headers (`Authorization: Bearer <jwt>`, `X-Guest-Token`) are injected via Axios interceptor in `client.js`.

Video streams use `buildStreamUrl()` from `videoModel.js` — appends `?token=<jwt>&guestToken=<guest>` to URL because `<video src>` cannot send custom headers.

## State Management

- **Server state**: React Query (`useVideos`, `usePrivateVideos`) — cache invalidated on share/delete
- **Auth state**: `AuthContext` → localStorage (`jwt`, `user`)
- **Theme state**: `ThemeContext` → localStorage (`theme`)
- **UI state**: local `useState` in components

## Video Playback

- `VideoPlayer` uses `useRef` for `<video>` element — playback controlled imperatively, not via state
- Starts `muted=true` for autoplay browser policy compliance; user toggles with mute button
- Progress bar is a direct absolute child of player div (not inside scrim) to avoid `overflow: hidden` clipping
- `VideoSwiper` preloads next 2 videos with hidden `<video preload="auto">` elements

## Testing

Tests live next to source in `__tests__/` folders.

```bash
npm run test              # all tests
npm run test -- --watch   # watch mode
```

Mock `window.matchMedia` is set up in `src/test-setup.js`.

## Environment Variables

```bash
VITE_API_BASE_URL=https://canh-labs.com/api/v1/funny-app
```

## Build & Deploy

```bash
npm run build   # outputs to dist/
```

CI (GitHub Actions) builds with `VITE_API_BASE_URL` injected, then SCPs `dist/` to server.
