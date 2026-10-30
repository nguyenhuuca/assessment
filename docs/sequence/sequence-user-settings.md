# Sequence Diagrams: User Settings

- **PRD:** [PRD-user-settings](../prd/PRD-user-settings.md)
- **ADR:** [0016-user-settings-design](../adr/0016-user-settings-design.md)
- **Spec:** [spec-user-settings](../specs/spec-user-settings.md)
- **Status:** As-built (2026-10-21)

All endpoints are under `/v1/funny-app` and need a JWT. `UserSettingsController` is class-level `@AuditLog`, so `otp` and `confirmation` are masked in audit logs.

## 1. Open Settings page (GET settings, lazy create)

The user opens `/settings`. `SettingsPage.jsx` loads settings through `useSettings()`. The first call creates a defaults row.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (SettingsPage.jsx)
    participant HK as useSettings.js
    participant API as UserSettingsController
    participant S as UserSettingsServiceImpl
    participant SR as UserSettingsRepository
    participant UR as UserRepo
    participant DB as PostgreSQL

    U->>FE: opens Settings
    FE->>HK: useSettings()
    HK->>API: GET /v1/funny-app/user/settings
    API->>S: getSettings()
    S->>SR: findById(userId)
    alt no row yet
        S->>SR: save(defaults: notify true, quality AUTO, incognito false, private false)
        SR->>DB: INSERT user_settings
        S->>S: incognitoCache.evict(userId) (createDefaultSettings)
    else row exists
        SR-->>S: UserSettings
    end
    S->>UR: findAllById(userId)
    UR-->>S: User (password, mfaEnabled)
    S->>S: buildDto(email, user, settings, flags passwordLoginEnabled and subscriptionStatusEnabled)
    S-->>API: UserSettingsDto
    API-->>HK: 200 ResultObjectInfo with UserSettingsDto
    HK-->>FE: renders Identity, Notifications, Playback, Language, Privacy, Danger zone
```

## 2. Change a setting (partial, atomic PATCH)

Each toggle or quality button sends only its own field. The service validates every provided field before writing anything, so one bad field rejects the whole request.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (SettingsPage.jsx)
    participant HK as useUpdateSettings
    participant API as UserSettingsController
    participant S as UserSettingsServiceImpl
    participant SR as UserSettingsRepository
    participant IC as IncognitoCache
    participant UR as UserRepo
    participant DB as PostgreSQL

    U->>FE: toggles a switch or picks quality
    FE->>HK: updateSettings.mutate with one field (controls disabled while pending)
    HK->>API: PATCH /v1/funny-app/user/settings (rate limit 20)
    API->>S: updateSettings(UpdateUserSettingsRequest)
    alt no field provided
        S-->>API: CustomException 400 error.settings.emptyUpdate
        API-->>HK: 400 ResultErrorInfo
    else defaultQuality not AUTO, 1080P or 4K
        S-->>API: CustomException 400 error.settings.invalidQuality
        API-->>HK: 400 ResultErrorInfo
    else locale not vi or en
        S-->>API: CustomException 400 error.settings.invalidLocale
        API-->>HK: 400 ResultErrorInfo
    else all valid
        S->>SR: findById(userId) or create defaults
        S->>S: apply only the provided fields
        S->>SR: save(settings)
        SR->>DB: UPDATE user_settings (updated_at bumped)
        S->>IC: evict(userId)
        S->>UR: findAllById(userId) (reload user to build the DTO)
        S->>S: buildDto: email, passwordEnabled, passwordLoginAvailable, mfaEnabled, mfaAvailable,<br/>settings fields, locale, accountStatus, updatedAt
        S-->>API: UserSettingsDto
        API-->>HK: 200 ResultObjectInfo with UserSettingsDto
        HK->>HK: setQueryData settings
        HK-->>FE: UI reflects new value
    end
    opt PATCH failed
        HK-->>FE: onError, patchError alert shown (settings value stays as before)
    end
```

## 3. Delete account (step-up verification)

The Danger zone opens `DeleteAccountModal.jsx`. An MFA user types a 6 digit OTP, other users type their email. The server soft-deletes and anonymizes, then the client logs out.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (DeleteAccountModal.jsx)
    participant API as UserSettingsController
    participant S as UserSettingsServiceImpl
    participant T as Totp
    participant UR as UserRepo
    participant WR as WatchHistoryRepository
    participant IC as IncognitoCache
    participant DB as PostgreSQL

    U->>FE: clicks Delete my account
    FE->>FE: client check (OTP has 6 digits, or confirmation equals own email)
    FE->>API: DELETE /v1/funny-app/user/account body otp or confirmation (rate limit 3)
    API->>S: deleteAccount(DeleteAccountRequest)
    S->>UR: findAllById(userId)
    alt already DEACTIVATED
        S-->>API: CustomException 409 error.account.alreadyDeactivated
        API-->>FE: 409 ResultErrorInfo
    else MFA enabled
        S->>T: verify(otp, mfaSecret)
        alt OTP wrong
            S-->>API: CustomException 403 error.otp.incorrect
            API-->>FE: 403 ResultErrorInfo
        end
    else MFA not enabled and confirmation differs from user name
        S-->>API: CustomException 400 error.account.confirmationMismatch
        API-->>FE: 400 ResultErrorInfo
    end
    Note over S: verification passed
    S->>UR: save(status DEACTIVATED, deleted_at now, user_name deleted_user_id, password null, mfa cleared)
    UR->>DB: UPDATE users
    S->>WR: deleteAll(userId)
    WR->>DB: DELETE FROM watch_history
    S->>IC: evict(userId)
    S-->>API: void
    API-->>FE: 204 No Content
    FE->>FE: logout() then redirect to /
    opt error
        FE-->>U: error alert in modal
    end
```

## 4. Deactivated account cannot sign in

After deletion the account is rejected on the magic-link and MFA paths. `UserServiceImpl.requireActive` throws 403. The password login path also refuses deactivated users.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (MagicLinkHandler.jsx)
    participant API as UserController
    participant S as UserServiceImpl
    participant UR as UserRepo

    U->>FE: opens magic link
    FE->>API: GET /v1/funny-app/user/verify-magic?token=...
    API->>S: verify magic token
    S->>UR: findAllByUserName(email)
    UR-->>S: User with status DEACTIVATED
    S->>S: requireActive(user)
    S-->>API: CustomException 403 error.account.deactivated
    API-->>FE: 403 ResultErrorInfo, no JWT issued
    Note over FE,API: the same guard runs in verifyMfa (POST /user/mfa/verify)
```

## Notes: PRD/spec vs code

- The spec lists `POST /user/join` as guarded. As built, `/user/join` only sends a magic link via `inviteService.inviteUser` and does not check status. The guard is in `UserServiceImpl` (`verify-magic` and `verifyMfa`) and in `PasswordLoginServiceImpl` (`/user/login`).
- The spec says editable fields are five. As built, `locale` (vi or en) is a sixth editable field (ADR-0020), and `UpdateUserSettingsRequest.isLocaleProvided()` lets the client send an explicit null.
- The spec says toggles may revert on error. As built there is no optimistic update: the UI waits for the 200 and shows `patchError` on failure.
- `defaultQuality` is stored and returned, but no player code in `webapp/src` reads it yet (only `SettingsPage.jsx`). Incognito is consumed by watch history (see sequence-watch-history-v2).
- `profilePrivate` is stored only, no other feature reads it in the code searched.
- The spec says the session or token is invalidated server-side on delete. As built, `deleteAccount` does not touch tokens and does not bump `credentialsVersion`. `JWTAuthenticationFilter` (lines 71-73) only compares the token's `cv` claim with `CredentialsVersionCache.currentVersion` and does no user status check, so an old JWT keeps working until it expires. The client calls `logout()` and redirects, which only discards the token locally.
