# PRD: Vietnamese + English Language Support

<!--
Product Requirements Document
Filename: docs/prd/PRD-i18n.md
Owner: nguyenhuuca
Handoff to: Architect (ADR-0020), Builder (/swarm-execute)
-->

## Overview

**Status:** Approved (2026-10-14)
**Author:** nguyenhuuca
**Date:** 2026-10-14
**Related ADR:** [ADR-0020 Internationalization](../adr/0020-i18n-vietnamese-english.md)
**Related Plan:** [plan-i18n](../plans/plan-i18n.md)

Every screen, API error and email is available in **Tiếng Việt** and **English**. The first visit follows the browser language. Users can switch at any time, and a logged-in user's choice follows them across devices.

---

## Problem Statement

The UI mixes the two languages today. Navigation is in English ("Home", "Library", "Settings") while actions are in Vietnamese ("Phát", "Xoá", "Lưu"). Other text is inconsistent too:
- Relative times are always English ("2m ago").
- The magic link email is English.
- The password-change email is Vietnamese.
- API errors come in either language.

Non-Vietnamese visitors cannot use most actions, and Vietnamese users get half-English screens.

---

## Goals & Success Metrics

| Goal | Metric | Target |
|------|--------|--------|
| Consistent language | Screens with mixed-language UI chrome | 0 |
| Complete translations | Keys present in both `vi` and `en` | 100% (enforced by CI parity test) |
| Correct default | First visit with a `vi-*` browser shows Vietnamese; any other shows English | 100% (test) |
| Persistent choice | A logged-in user's language is restored on a new device | Yes |
| Localized email | Magic link and password emails arrive in the user's language | 100% |

---

## Users & Stories

- **Guest (any language):** I see the site in my browser's language and can switch with one click from the header.
- **Logged-in user:** I pick my language once, in Settings or the header, and it is used on every device and in emails.
- **Vietnamese user:** every button, message, time ("2 phút trước") and email is in Vietnamese.
- **Admin:** the admin pages follow the same language setting.

---

## Requirements

### Functional
1. **Language switcher** (VI / EN) in the header, available to guests and users, and in Settings, logged in only.
2. **First-visit default:** a browser language starting with `vi` gives Vietnamese; any other gives English.
3. **Persistence:**
   - `localStorage` for everyone.
   - `user_settings.locale` for logged-in users, applied after login.
4. **Coverage:**
   - All user UI: feed, Explore, Library, auth, comments, notifications, Settings, modals and the mobile nav.
   - The Admin pages.
5. **Formatting:** relative time, compact view counts, dates and plurals follow the selected language.
6. **API errors:** user-visible errors come back in the request language (`Accept-Language`).
7. **Emails:** the magic link and the password set/change/remove notices go out in the user's language, using the request language for brand-new users.
8. **Not translated:** user content (video titles, comments, collection names) and the brand name "Funny Movies".
9. `<html lang>` reflects the current language.

### Non-functional
- No visible flash of the wrong language on load. Resources are bundled, and detection runs before the first render.
- The bundle grows by less than about 30 KB gzipped.
- The CI parity tests fail on any missing key, in the frontend JSON or the backend properties.

---

## Out of Scope
- Languages other than vi and en (the design allows adding them later).
- Translating user-generated content or video metadata.
- Right-to-left layouts.
- Locale-specific URLs (`/vi/...`) and SEO hreflang.

---

## Open Questions
- **OQ-1:** Should the header switcher be a globe icon with a menu, or a compact "VI | EN" toggle? **Resolved:** the "VI | EN" toggle beside the theme toggle.
- **OQ-2:** **Resolved:** the English name for the "Xem sau" default collection is "Watch later".

## Risks
See [ADR-0020 Consequences](../adr/0020-i18n-vietnamese-english.md#consequences): edge caching, large diff, missing keys.
