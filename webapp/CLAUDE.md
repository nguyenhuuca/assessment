# CLAUDE.md — webapp (React Frontend)

This file provides guidance to Claude Code when working with the `webapp/` directory.

Stack: `package.json` and `.claude/rules/tech-strategy.md`. **No Tailwind, no styled-components.** UI is built with CSS custom properties defined in `src/styles/index.css`.

## Design System (`src/styles/index.css`)

All styling uses CSS custom properties. Never use Bootstrap classes for layout or color — only the custom classes defined here.

## Responsive Layout

- **Desktop (>768px)**: fixed side nav (88px), video in 9:16 card with action column beside it
- **Mobile (≤768px)**: full-screen video, action buttons overlay on right edge, bottom nav, no side nav

## API Integration

Base URL set via env var: `VITE_API_BASE_URL` (defaults to `https://canh-labs.com/api/v1/funny-app`).

Auth headers (`Authorization: Bearer <jwt>`, `X-Guest-Token`) are added by the fetch wrapper in `client.js`.

Video streams use `buildStreamUrl()` from `videoModel.js`. Never put the JWT or guest token in a URL — the stream endpoint is public and does not read them, and query strings are written to nginx/Cloudflare logs (token leak).

## State Management

- **Server state**: React Query (`useVideos`) — cache invalidated on share/delete
- **Auth state**: `AuthContext` → localStorage (`jwt`, `user`)
- **Theme state**: `ThemeContext` → localStorage (`theme`)
- **UI state**: local `useState` in components

## Video Playback

- `VideoPlayer` uses `useRef` for `<video>` element — playback controlled imperatively, not via state
- Starts `muted=true` for autoplay browser policy compliance; user toggles with mute button
- Progress bar is a direct absolute child of player div (not inside scrim) to avoid `overflow: hidden` clipping
- `VideoSwiper` preloads next 2 videos with hidden `<video preload="auto">` elements

## Testing

Tests live next to source in `__tests__/` folders. Mock `window.matchMedia` is set up in `src/test-setup.js`.

## Environment Variables

```bash
VITE_API_BASE_URL=https://canh-labs.com/api/v1/funny-app
```

## Build & Deploy

CI (GitHub Actions) builds with `VITE_API_BASE_URL` injected, then SCPs `dist/` to server.
