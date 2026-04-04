# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Vanilla JavaScript SPA for the Funny Movies video streaming frontend. No build step — files are served directly to the browser. Dependencies loaded via CDN (Bootstrap 5.1.3, jQuery 3.5.1, Font Awesome 5.7.0).

## Running Locally

No build system. Serve files with any static file server:

```bash
# Python
python -m http.server 8080

# Or open index.html directly in browser
```

The app hits `https://canh-labs.com/api/v1/funny-app` by default. To point at a local API, edit `appConst.baseUrl` in `js/common.js`.

## File Structure

```
webapp/
├── index.html          # Single HTML entry point; defines all modals, tabs, and UI
├── js/
│   ├── common.js       # API base URL, STATUS constants, showMessage()
│   ├── auth.js         # Auth, MFA, magic link, profile
│   └── assessment.js   # Video model, templates, actions, VideoService
├── css/
│   └── assessment.css  # All styles; CSS variable-based theme system
└── manifest.json       # PWA config
```

## Architecture

**Module pattern** using object literals — no ES modules, no bundler.

### `js/common.js`
- `appConst.baseUrl` — API root URL (switch to localhost for local dev)
- `appConst.offlineMode` — feature flag for offline testing
- `window.STATUS` — frozen enum (`MFA_REQUIRED`, `INVITED_SEND`)
- `showMessage(msg, type)` — global error/success banner

### `js/auth.js`
- `Auth.initAjaxHeaders()` — sets JWT + guest token on all AJAX requests
- `Auth.UserStorage` — localStorage read/write for user state
- `Auth.LoginManager` — passwordless join/login flow
- `Auth.MFA` — TOTP setup, verification, enable/disable
- `Auth.MagicLink` — consumes token from URL query param on page load
- `Auth.initState()` — called on load to restore session

### `js/assessment.js`
Four major objects:
- **`Video` (class)** — model with `determineCategory()` auto-classifying videos as `funny` or `regular` based on title keywords
- **`VideoTemplate`** — generates video player HTML from a template string with `{{placeholder}}` substitution
- **`VideoActions`** — all user interactions: play/pause, like/dislike, share, delete, comment panel, swipe/keyboard navigation
- **`VideoService`** — AJAX calls to the backend API; manages loading spinners and error handling

Entry point: `VideoService.loadData()` called from `index.html` `<script>` tag on page load.

## State Management

All persistent state lives in `localStorage`:
- JWT token and user object (`Auth.UserStorage`)
- Theme preference (`ThemeManager`)

No global state store — modules communicate via DOM events and direct function calls.

## Theming

CSS custom properties in `assessment.css` under `:root` (dark, default) and `[data-theme="light"]`. `ThemeManager.toggle()` flips `data-theme` on `<body>` and persists to localStorage.

## API Integration

All requests use jQuery `$.ajax`. Auth headers (`Authorization: Bearer <jwt>`, `X-Guest-Token`) are set globally via `Auth.initAjaxHeaders()`.

Backend endpoints used:
- `POST /user/join` — register/login
- `GET /user/me` — session validation
- `GET /video/list?category=` — fetch videos by tab
- `POST /video/share` — add new video
- `POST /video/like` / `POST /video/unlike`
- `DELETE /video/{id}`
- `GET|POST /user/mfa/*` — MFA setup and verification
