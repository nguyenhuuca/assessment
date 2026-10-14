# ADR-0020: Internationalization (Vietnamese + English)

## Metadata

**Status:** Accepted (2026-10-14) · **Date:** 2026-10-14 · **Deciders:** nguyenhuuca · **Tags:** i18n, frontend, api-contract, email
**Related PRD:** [PRD-i18n](../prd/PRD-i18n.md) · **Related Plan:** [plan-i18n](../plans/plan-i18n.md) · **Supersedes:** N/A

**Tech Strategy:** ⚠️ Adds new frontend dependencies (`i18next`, `react-i18next`, `i18next-browser-languagedetector`), which are not in the Golden Path. If this ADR is accepted, `.claude/rules/tech-strategy.md` must be updated, as task FE-1 does.

---

## Context

The UI mixes the two languages. Navigation chrome is in English ("Home", "Library", "Settings"), while content labels are in Vietnamese ("Phát", "Xoá", "Xem sau").

Survey of the codebase on 2026-10-14:

**Frontend**
- About 40 non-test `.jsx` files contain roughly 230–280 hardcoded strings.
- There is no i18n library. `index.html` hardcodes `lang="en"`.
- Formatting is hardcoded too:
  - `commentUtils.js` relative time is always English ("2m ago").
  - `ExploreView` uses `Intl.NumberFormat('en')`.
  - `"{n} video"` has no plural handling.
- Notification titles are rendered on the frontend (`utils/notificationText.js`, in Vietnamese). The DB stores only a JSON payload, so no data migration is needed.
- 21 test files assert on literal UI strings (about 60 assertions).

**Backend**
- No `MessageSource` and no `LocaleResolver`; `Accept-Language` is ignored.
- `CustomException` messages are free text, mostly English and some Vietnamese. `client.js` shows `error.message` directly.
- Emails:
  - The magic link email (`templates/email/invite.html` and its subject) is English only.
  - The password-change email is built in code in Vietnamese (`PasswordLoginServiceImpl` ~L234–248).
- The default collection name `"Xem sau"` is stored as data in `video_collections.name`.
- `user_settings` exists (ADR-0016) but has no locale column.

The owner chose (2026-10-14):
- `react-i18next`.
- Default language taken from the browser.
- Scope covers user UI, API error messages, emails and the Admin pages.

---

## Decision Drivers

- Two languages now. Adding a third later must not need a refactor.
- Guests can switch language too. Logged-in users keep their choice across devices.
- Emails go out without a browser (for example the async password-change notice), so the backend must know the user's language without a request.
- No new infrastructure. Translations ship inside the bundle and the JAR.
- Tests must not break every time a translation is reworded.

---

## Decisions

### D1. Frontend: i18next + react-i18next, bundled resources

- Add `i18next`, `react-i18next` and `i18next-browser-languagedetector`.
- Resources are static JSON imports in `webapp/src/i18n/locales/{vi,en}/<namespace>.json`. Namespaces are `common`, `feed`, `library`, `auth`, `comments`, `notifications`, `settings`, `admin` and `errors`.
- There is no HTTP backend and no lazy loading. Both languages together are about 300 keys, a few KB gzipped.
- Plurals use i18next suffixes (`_one` / `_other`). Vietnamese uses `_other` only, per CLDR.
- Interpolation uses `{{name}}`. React already escapes output, so `escapeValue: false`.
- Dates, numbers and relative time use `Intl.*` with the current language:
  - `Intl.RelativeTimeFormat` replaces the hand-written "2m ago".
  - `Intl.NumberFormat(lng, { notation: 'compact' })`.

**Rejected alternative: an in-house `useT()` hook.** It has no dependency, but the owner preferred the standard library for plurals, detection and future languages.

### D2. Language resolution order (frontend)

1. The user's saved choice, from `localStorage['lang']`, which the switcher writes.
2. For logged-in users, `user_settings.locale` from the server, when set. It is applied after login and overrides (1).
3. The browser language: `navigator.language` starting with `vi` gives `vi`, anything else gives `en`.
4. Fallback is `en`.

`supportedLngs: ['vi', 'en']` and `nonExplicitSupportedLngs: true`, so `vi-VN` maps to `vi`. On every change the app sets `document.documentElement.lang`.

### D3. API contract: `Accept-Language`

- `api/client.js` sends `Accept-Language: <current lng>` on every request.
- The backend uses `AcceptHeaderLocaleResolver` with supported locales `[vi, en]` and default `en`.
- CORS already allows all headers, and `Accept-Language` is a CORS-safelisted header anyway.

### D4. Backend messages: `MessageSource` + message keys

- Add `messages.properties` (English, the default) and `messages_vi.properties` in `src/main/resources/i18n/`, both UTF-8.
- `CustomException` gains an optional `messageKey` and `args`.
  - `RestExceptionHandler` resolves the key with `LocaleContextHolder.getLocale()`.
  - Without a key, the existing `message` is returned unchanged, so migration can be incremental.
- Response shape: `ResultErrorInfo.message` is now localized. A new stable `code` field holds the message key, for clients and tests.
- Only messages users can see are migrated:
  - Validation, collection and comment errors.
  - Auth and password errors (`PasswordPolicy` and `PasswordLoginServiceImpl` codes already exist).
  - Rate limiting.
- Internal and system errors stay in English.

### D5. Persisted preference: `user_settings.locale`

- Migration: `ALTER TABLE user_settings ADD COLUMN locale VARCHAR(5) NULL`. NULL means "not chosen, follow the browser".
- `GET/PATCH /user/settings` exposes `locale`. The allowed values are `vi`, `en` and null.
- The frontend PATCHes when a logged-in user switches language, and applies the server value after login.

### D6. Emails use the recipient's locale, never the current thread

- The email locale is `user_settings.locale`, then the request locale (at magic-link sign-up, before any settings exist), then `en`.
- The locale is passed **explicitly** into `MailService`. The code never reads `LocaleContextHolder` there, because async and virtual threads do not carry it.
- Templates are split per locale: `templates/email/invite_vi.html` and `invite_en.html`. The template body has too much layout to stay readable as message keys. Subjects and the password-change body move to `MessageSource`.

### D7. Stored names stay data and are translated on display

- The default collection keeps `name = 'Xem sau'` in the DB, for uniqueness and because it cannot be renamed.
- The frontend shows `t('library.watchLater')` when `isDefault` is true.
- User-generated content (titles, comments, collection names) is never translated.

### D8. Test strategy

- `src/test-setup.js` initializes i18n synchronously with `lng: 'vi'` and the real resources. Most existing Vietnamese assertions keep working. Assertions on English chrome ("Home") are updated to the Vietnamese value or switched to role/key-based queries.
- CI guards:
  - A **key-parity test**: `vi` and `en` have identical key sets per namespace, and no value is empty.
  - The same check for `messages.properties` against `messages_vi.properties` in JUnit.
- Each switched component gets at least one test that renders in `en`.

---

## Consequences

**Positive**
- Every user-facing string lives in one place per language.
- Adding a language means adding JSON and properties files only.
- Errors and emails match the user's language.

**Negative / costs**
- Three new frontend dependencies, about 20 KB gzipped, plus an update to tech-strategy.md.
- Touches about 40 components and 21 test files, so the diff is large. It is phased by area to keep PRs reviewable.
- `ResultErrorInfo` gains a field. This is additive, so existing clients are unaffected.

**Risks**
- **Cloudflare caching.** If any API error or GET response is edge-cached, the language would leak between users. Mitigation: send `Vary: Accept-Language` on localized responses, and verify that `/api` JSON is not cached at the edge (QA-1).
- **Missing keys at runtime.** Mitigated by the parity tests. Set `saveMissing: false` and `returnNull: false`, and fall back to `en`.
- **Admin-only strings** are lower value. They are translated last (FE-5) and can be deferred without blocking the rest.

---

## Compliance

- Spring `MessageSource` and `LocaleResolver` are part of Spring Boot, so the backend stays on the Golden Path.
- One Liquibase migration (D5); no `ddl-auto`.
- No secrets and no new infrastructure.
