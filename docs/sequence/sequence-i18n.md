# Sequence Diagrams: Vietnamese + English language support (i18n)

- **PRD:** [PRD-i18n](../prd/PRD-i18n.md)
- **ADR:** [0020-i18n-vietnamese-english](../adr/0020-i18n-vietnamese-english.md)
- **Spec:** —
- **Status:** As-built (2026-10-21)

All endpoints are under `/v1/funny-app`. Supported languages are `vi` and `en` (`SUPPORTED_LANGUAGES` in `webapp/src/i18n/index.js`, `I18nConfig.SUPPORTED` in the API).

## 1. First visit and language switch (browser only)

`i18n/index.js` bundles every `locales/lng/namespace.json` and detects the language in the order localStorage key `lang`, then `navigator`. Unsupported languages fall back to `en`. `LanguageSwitcher.jsx` calls `setLanguage(code)`, which stores the choice in localStorage (detector cache) and updates `<html lang>`.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant M as Browser (main.jsx)
    participant I as i18n/index.js (i18next)
    participant LS as localStorage
    participant LSW as LanguageSwitcher.jsx
    participant DOM as document.documentElement

    U->>M: opens the site
    M->>I: import './i18n' as a side effect (main.jsx line 3, before App) - init with bundled resources, initImmediate false
    I->>LS: read key lang
    alt lang stored and supported
        LS-->>I: vi or en
    else nothing stored
        I->>I: navigator.language (vi-* maps to vi, other maps to fallback en)
    end
    I->>DOM: syncHtmlLang sets lang attribute
    I-->>M: ready before first render
    U->>LSW: clicks VI or EN
    LSW->>I: setLanguage(code) = changeLanguage
    I->>LS: cache lang
    I->>DOM: languageChanged event, syncHtmlLang
    I-->>LSW: React components re-render via react-i18next
```

## 2. Every API call carries the language and errors come back localized

`api/client.js` adds `Accept-Language` from `i18n.language` to every request. On the server, `AcceptHeaderLocaleResolver` (restricted to vi and en, default en) sets the locale and `RestExceptionHandler` resolves the message key from `messages.properties` or `messages_vi.properties`. The key is also returned as `error.code`.

```mermaid
sequenceDiagram
    autonumber
    participant FE as Browser (api/client.js)
    participant LR as AcceptHeaderLocaleResolver (I18nConfig)
    participant C as Any controller or service
    participant H as RestExceptionHandler
    participant MS as MessageSource (i18n/messages)

    FE->>LR: any request with header Accept-Language vi or en
    LR->>LR: locale = vi or en, otherwise default en
    LR->>C: handler runs
    C-->>H: CustomException.of(status, subCode, messageKey)
    H->>MS: getMessage(key, args, fallback, LocaleContextHolder.getLocale())
    MS-->>H: localized text (vi or en)
    H-->>FE: ResultErrorInfo with message and code = key (for example 403 error.account.deactivated)
    FE->>FE: throws message, status, code - UI shows the localized message
```

## 3. Logged-in user: locale persisted and applied across devices

`useLocaleSync()` runs in `AppShell.jsx`. Once per login the stored server locale wins over the local one. If the server has none, the current UI language is saved. Any later change is sent once as `PATCH /user/settings` with `locale`. `knownServerLocale` prevents a PATCH loop.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (useLocaleSync.js)
    participant I as i18next
    participant API as UserSettingsController
    participant S as UserSettingsServiceImpl
    participant DB as PostgreSQL

    FE->>API: GET /v1/funny-app/user/settings (useSettings, enabled when logged in)
    API-->>FE: 200 ResultObjectInfo with UserSettingsDto (locale may be null)
    alt server locale is vi or en
        FE->>FE: knownServerLocale = locale
        FE->>I: changeLanguage(locale) if different from UI language (no PATCH back)
    else no stored locale
        FE->>FE: knownServerLocale = current UI language
        FE->>API: PATCH /v1/funny-app/user/settings with locale (rate limit 20)
        API->>S: updateSettings(request)
        S->>S: validate locale in vi or en
        S->>DB: UPDATE user_settings SET locale
        API-->>FE: 200 UserSettingsDto
    end

    U->>I: later switches language in header or Settings
    I-->>FE: languageChanged event
    alt new language differs from knownServerLocale
        FE->>API: PATCH /v1/funny-app/user/settings with locale
        alt locale not vi or en (API-only: unreachable from the UI, useLocaleSync only sends vi or en)
            S-->>API: CustomException 400 error.settings.invalidLocale
            API-->>FE: 400 ResultErrorInfo
        else valid
            API-->>FE: 200 UserSettingsDto
        end
    end
    Note over FE: on logout the refs reset so the next login applies the server locale again
```

## 4. Localized emails (magic link, security notice, digest)

Emails are sent without a browser, so the locale is resolved on the calling thread by `EmailLocaleResolver` and passed on as a plain `Locale` (ADR-0020 D6): stored `user_settings.locale`, else the request locale (only inside a web request), else English. The magic link send is `@Async`, the notification digest runs from a job.

```mermaid
sequenceDiagram
    autonumber
    actor U as User or Scheduler
    participant API as UserController
    participant INV as InviteServiceImpl
    participant EL as EmailLocaleResolver
    participant SR as UserSettingsRepository
    participant MAIL as MailServiceImpl
    participant SMTP as SMTP (Gmail)

    U->>API: POST /v1/funny-app/user/join (email, header Accept-Language)
    API->>INV: inviteUser(email, null)
    INV->>EL: forEmail(email)
    EL->>SR: findById(userId) - stored locale
    alt stored locale vi or en
        SR-->>EL: locale
    else new address or no locale
        EL->>EL: forRequest() = LocaleContextHolder locale, English outside a request
    end
    EL-->>INV: Locale
    INV->>MAIL: sendInvitation(to, username, magicLink, locale) - async
    MAIL->>MAIL: subject from MessageSource mail.invite.subject, templateFor(locale) invite html vi or en
    MAIL->>SMTP: send HTML mail
    API-->>U: 200 ResultObjectInfo action INVITED_SEND

    Note over INV,SMTP: Same pattern elsewhere
    Note over INV,SMTP: PasswordLoginServiceImpl.sendSecurityEmail uses forUser(userId) and mail.security.* keys
    Note over INV,SMTP: NotificationDigestService.sendDigest uses forUser(userId) in the job thread (no request, so stored locale or English)
```

## Notes: PRD vs code

- PRD requirement 1 says the Settings switcher is logged in only. As built, `SettingsPage.jsx` embeds the same `LanguageSwitcher` as the header, and the Settings route itself needs login, so this matches.
- PRD says the first visit with a `vi-*` browser shows Vietnamese and anything else English. As built this comes from i18next `supportedLngs` + `nonExplicitSupportedLngs` + `fallbackLng: en`, not from custom code.
- `Accept-Language` is sent as the raw `i18n.language` (it can be `vi-VN`), and the server resolver matches on language only.
- Not every backend error is localized: `handleHttpMediaTypeNotSupported`, `handleEntityNotFound` and `handleIllegalArgumentException` (uses `ex.getMessage()`) in `RestExceptionHandler` use plain text, and the `HttpClientErrorException` overload of `handleAuthenticationException` passes the upstream body through. The `AuthenticationException` overload is localized (`KEY_BAD_CREDENTIALS`). The JWT filter writes its 401 straight from `UnauthorizedException.toJson()` (codes such as `TOKEN_REVOKED`), so those bodies are not localized either. Only keyed `CustomException` and the validation, missing-parameter and rate-limit handlers go through `MessageSource`.
- The password set, change and remove notices are plain-text mails localized by `messages*.properties` keys (not HTML templates). The invite mail is the only HTML template (`templateFor`).
