# Sequence Diagrams: Password Login

<!--
One file per feature that has a PRD: docs/sequence/sequence-<feature>.md (slug = PRD slug).
- Mermaid `sequenceDiagram` only (rendered by mkdocs via pymdownx.superfences); no PNG/PlantUML.
- Draw what the code does (or will do, per ADR/spec). Cite the class/method in each participant or note.
- One diagram per main flow: the happy path first, then the important alternative or failure paths
  (auth failure, validation error, async/event, scheduled job). 2-6 diagrams is typical.
- Participants: Browser (React page/hook) → Controller → Service → Repository/DB, plus external
  systems (YouTube, GitHub, SMTP, SSE) and async listeners/jobs. Use `autonumber`.
- Keep in sync: when the as-built flow changes, update the diagram in the same commit.
-->

- **PRD:** [PRD-password-login](../prd/PRD-password-login.md)
- **ADR:** [0018-password-login-option](../adr/0018-password-login-option.md)
- **Spec:** — (plan: [plan-password-login](../plans/plan-password-login.md))
- **Status:** As-built (2026-10-21)

Notes (code vs PRD):

- "Forgot password" in `PasswordLoginModal.jsx` makes no API call. `LoginForm.handleForgot` only closes the modal, copies the typed email into the magic-link form and shows the hint "Press Log in to receive a sign-in link by email". The user then submits the normal magic-link form (`POST /user/join`).
- All base paths below are relative to `/v1/funny-app`.

## 1. Password sign-in (happy path, with and without MFA)

A visitor opens the password dialog from the header `LoginForm`, submits email and password and gets a JWT. If the account has MFA enabled, `UserServiceImpl.completeLogin` returns `MFA_REQUIRED` plus a session token instead, and the SPA opens `MFALoginModal` to finish with `POST /user/mfa/verify`.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (LoginForm / PasswordLoginModal / MFALoginModal)
    participant API as UserController
    participant PL as PasswordLoginServiceImpl
    participant LA as LoginAttemptServiceImpl
    participant US as UserServiceImpl
    participant MS as MFASessionStore
    participant DB as PostgreSQL

    U->>FE: open login form
    FE->>API: GET /v1/funny-app/user/auth-options
    API-->>FE: 200 AuthOptionsDto (passwordLoginAvailable)
    Note over FE: password button shown only when passwordLoginAvailable is true
    U->>FE: enter email and password, submit
    FE->>API: POST /v1/funny-app/user/login (RateLimited 10 per IP)
    API->>PL: login(PasswordLoginRequest)
    PL->>PL: requireEnabled() (app password-login-enabled flag)
    PL->>DB: userRepo.findAllByUserName(email)
    DB-->>PL: User
    PL->>PL: usable check (hash set, not DEACTIVATED, not locked, password at most 72 bytes)
    PL->>PL: bCrypt.matches(password, user.password)
    opt failed_login_count or locked_until was set
        PL->>LA: recordSuccess(userId) (REQUIRES_NEW)
        LA->>DB: userRepo.clearLoginFailures(userId)
    end
    PL->>US: completeLogin(user)
    alt user.mfaEnabled is false
        US->>US: issueToken(user) (JWT with cv claim = credentialsVersion)
        US-->>PL: UserInfoDto (jwt)
        PL-->>API: UserInfoDto
        API-->>FE: 200 ResultObjectInfo of UserInfoDto
        FE->>FE: LoginForm.handleAuthResult then AuthContext.login(jwt, user)
        FE-->>U: signed in
    else user.mfaEnabled is true
        US->>MS: storeSession(sessionToken, userName)
        US-->>PL: UserInfoDto (action MFA_REQUIRED, sessionToken, no jwt)
        PL-->>API: UserInfoDto
        API-->>FE: 200 ResultObjectInfo of UserInfoDto (MFA_REQUIRED)
        FE->>FE: LoginForm.handleAuthResult stores user, calls onMfaRequired (AppShell opens MFALoginModal)
        U->>FE: enter 6-digit OTP
        FE->>API: POST /v1/funny-app/user/mfa/verify (otp, username, sessionToken)
        API->>US: verifyMfa(MfaRequest)
        US->>MS: getUserId(sessionToken)
        MS-->>US: userName
        US->>DB: userRepo.findAllByUserName(userName)
        US->>US: totp.verify(otp, mfaSecret)
        US-->>API: UserInfoDto (jwt)
        API-->>FE: 200 ResultObjectInfo of UserInfoDto
        FE->>FE: AuthContext.login(jwt, user)
        FE-->>U: signed in
    end
```

## 2. Password sign-in failure (generic 401, lockout, rate limit)

Every rejection (unknown email, no password set, wrong password, deactivated, locked, over-long password) returns the same `401 INVALID_CREDENTIALS`, so callers cannot enumerate accounts. A wrong password increments the failure counter in its own transaction. At 5 failures the account is locked for 15 minutes (magic link still works). The IP rate limit answers 429.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (PasswordLoginModal.jsx)
    participant API as UserController
    participant PL as PasswordLoginServiceImpl
    participant LA as LoginAttemptServiceImpl
    participant DB as PostgreSQL

    U->>FE: submit email and password
    FE->>API: POST /v1/funny-app/user/login
    alt more than 10 requests per minute from the IP
        API-->>FE: 429 (RateLimited aspect)
        FE-->>U: "rateLimited" message
    else within the limit
        API->>PL: login(PasswordLoginRequest)
        PL->>DB: userRepo.findAllByUserName(email)
        DB-->>PL: User or null
        alt unknown email, no hash, DEACTIVATED, locked or password not checkable
            PL->>PL: burnTime(password) (BCrypt against dummyHash, equal timing)
            PL-->>API: CustomException 401 INVALID_CREDENTIALS
        else password does not match
            PL->>LA: recordFailure(userId) (REQUIRES_NEW)
            LA->>DB: userRepo.incrementFailedLoginCount(userId)
            LA->>DB: userRepo.findAllById(userId)
            opt failedLoginCount reaches MAX_FAILURES (5)
                LA->>DB: userRepo.lockAccount(userId, now plus 15 min)
            end
            PL-->>API: CustomException 401 INVALID_CREDENTIALS
        end
        API-->>FE: 401 ResultErrorInfo (INVALID_CREDENTIALS)
        FE-->>U: "invalidCredentials" (same text for every cause)
    end
```

## 3. Set or change password (authenticated, MFA aware)

A signed-in user sets a first password (no current password needed) or changes an existing one (current password required) in Settings, `SignInPanel.jsx`. MFA users must also send a 6-digit OTP. Success bumps `credentials_version`, which revokes every other session, and returns a fresh JWT for the current one. Remove password (`DELETE /user/password`) follows the same path with `removePassword`, always requires the current password and sends `PASSWORD_REMOVED`.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (SignInPanel.jsx)
    participant API as UserController
    participant PL as PasswordLoginServiceImpl
    participant LA as LoginAttemptServiceImpl
    participant CV as CredentialsVersionCache
    participant US as UserServiceImpl
    participant MAIL as MailService
    participant DB as PostgreSQL

    U->>FE: fill current (if set), new, confirm, OTP (if mfaEnabled)
    FE->>FE: client checks (required, confirm match, OTP length 6)
    FE->>API: PUT /v1/funny-app/user/password (JWT, RateLimited 5)
    API->>PL: setPassword(SetPasswordRequest)
    PL->>PL: requireEnabled() and currentUser() (AppUtils.getCurrentUser, userRepo.findAllById)
    PL->>PL: PasswordPolicy.validate(newPassword, email)
    opt user already has a password
        PL->>PL: verifyCurrentPassword(user, currentPassword)
        Note over PL,LA: wrong current password calls recordFailure and counts toward lockout
    end
    opt user.mfaEnabled
        PL->>PL: verifyOtpIfMfa() via totp.verify(otp, mfaSecret)
    end
    PL->>PL: bCrypt.encode(newPassword), set passwordSetAt
    PL->>DB: userRepo.save(user) with credentialsVersion plus 1
    PL->>CV: evict(userId)
    PL->>US: issueToken(user) (new cv claim)
    PL->>MAIL: sendSimpleMail(security notice) (failure only logged)
    PL-->>API: UserInfoDto (jwt, action PASSWORD_SET or PASSWORD_CHANGED)
    API-->>FE: 200 ResultObjectInfo of UserInfoDto
    FE->>FE: AuthContext.login(newJwt, user) and patch ['settings'] query
    FE-->>U: "passwordUpdated"
    alt policy violation
        PL-->>API: CustomException 400 PASSWORD_TOO_SHORT, PASSWORD_TOO_LONG, PASSWORD_CONTAINS_EMAIL or PASSWORD_TOO_COMMON
    else wrong or uncheckable current password, or account locked
        PL-->>API: CustomException 400 CURRENT_PASSWORD_INVALID
    else MFA user with missing or wrong OTP
        PL-->>API: CustomException 400 OTP_INVALID
    end
    API-->>FE: 400 ResultErrorInfo (code)
    FE-->>U: mapPasswordError() shows translated errCodes text
```

## 4. Session revocation after a credential change

Tokens carry a `cv` claim. `JWTAuthenticationFilter` compares it with `CredentialsVersionCache.currentVersion(userId)` on every request, so a token issued before a set, change or remove is rejected with 401 `TOKEN_REVOKED`. The SPA then clears local storage and signs the user out.

```mermaid
sequenceDiagram
    autonumber
    participant FE2 as Browser (other device, old JWT)
    participant F as JWTAuthenticationFilter
    participant JP as JwtProvider
    participant CV as CredentialsVersionCache
    participant DB as PostgreSQL
    participant AC as Browser (client.js and AuthContext)

    FE2->>F: any protected request with old JWT (cv = n)
    F->>JP: verifyToken(token)
    JP-->>F: UserDetailDto (credentialsVersion = n)
    F->>CV: currentVersion(userId)
    alt cached value present
        CV-->>F: n plus 1
    else cache miss or expired (instances converge within about a minute)
        CV->>DB: read credentials_version
        DB-->>CV: n plus 1
        CV-->>F: n plus 1
    end
    F-->>FE2: 401 TOKEN_REVOKED
    FE2->>AC: client.js removes jwt and user, dispatches AUTH_REVOKED_EVENT
    AC-->>FE2: signed out, login form shown
```
