# Sequence Diagrams: React Frontend Migration (SPA runtime)

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

- **PRD:** [PRD-react-migration](../prd/PRD-react-migration.md)
- **ADR:** [0008-react-migration](../adr/0008-react-migration.md)
- **Spec:** —
- **Status:** As-built (2026-10-21)

> **PRD vs code.**
> - The PRD status is `Planning` but the SPA is live in `webapp/` (ADR-0008: Accepted, implemented).
> - PRD says React Router v6 for routing. The code uses `react-router-dom` v7 but defines no `<Route>` elements. `BrowserRouter` only provides `useNavigate` to `MagicLinkHandler`, and screens are switched by an `activeNav` state in `AppShell`.
> - PRD says react-bootstrap and Font Awesome. The UI uses custom CSS tokens and Material Symbols.
> - PRD lists `/private-videos`, the private tab and `/share-links` POST. The private tab was removed and share links are being retired. Only `DELETE /share-links/{id}` remains in `videosApi`.
> - `webapp/CLAUDE.md` mentions Axios, but `src/api/client.js` uses `fetch`. The `Authorization` header carries the raw JWT with no `Bearer ` prefix.
> - There is no global React Query 401 handler. 401 handling lives in `client.js` (TOKEN_REVOKED only) and `AuthContext` (the `/user/me` check at boot).

All API calls go to `VITE_API_BASE_URL`, default `https://canh-labs.com/api/v1/funny-app`.

## 1. App boot and JWT restore

On load, `main.jsx` renders `App`, which nests `BrowserRouter`, `QueryClientProvider` (retry 1, no refetch on focus), `ThemeProvider`, `AuthProvider`, `AppShell`. `AuthProvider` reads `jwt`, `user` and `guestToken` from localStorage and, when a JWT exists, validates it with `GET /user/me`.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant M as Browser (main.jsx, App.jsx)
    participant AP as AuthProvider (AuthContext.jsx)
    participant LS as localStorage
    participant C as api/client.js
    participant API as UserController

    U->>M: load SPA
    M->>AP: mount providers then AppShell
    AP->>LS: readStorage jwt, user, guestToken
    alt no jwt
        AP->>AP: loading = false, guest mode
    else jwt present
        AP->>AP: loading = true, user from cache
        AP->>C: authApi.me()
        C->>API: GET /user/me (Authorization jwt, X-Guest-Token)
        alt 200
            API-->>C: ResultObjectInfo of UserDetailDto
            AP->>LS: store user with role and isAdmin
            AP->>AP: loading = false
        else 401 or 403 (expired or invalid token)
            API-->>C: error
            C-->>AP: throw message, status
            AP->>LS: remove jwt and user
            AP->>AP: jwt = null, user = null, loading = false
        else network or 5xx
            AP->>AP: keep stored session, loading = false
        end
    end
    AP-->>U: AppShell renders (home feed for guests and users)
```

## 2. Magic link sign-in (?token= in the URL)

`MagicLinkHandler` (mounted in `AppShell`) runs once, strips the token from the address bar and exchanges it for a JWT. Accounts with MFA get an intermediate `MFA_REQUIRED` answer.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant H as MagicLinkHandler.jsx
    participant AP as AuthProvider
    participant C as api/client.js
    participant API as UserController
    participant SH as AppShell

    U->>H: open email link with ?token=T
    H->>H: history.replaceState (token removed from URL)
    H->>C: authApi.verifyMagic(T)
    C->>API: GET /user/verify-magic?token=T
    alt valid token and no MFA
        API-->>C: 200 ResultObjectInfo (jwt, user)
        H->>AP: login(jwt, user)
        AP->>AP: localStorage jwt and user, isLoggedIn = true
        H->>H: navigate to root (replace)
    else MFA enabled
        API-->>C: 200 action MFA_REQUIRED
        H->>SH: onMfaRequired(data) opens MFALoginModal
        SH->>API: POST /user/mfa/verify (otp, username, sessionToken)
        API-->>SH: jwt and user then login()
    else invalid or expired
        API-->>C: error
        H->>SH: onError(message) shows toast for 5 seconds
    end
```

## 3. Screen guard (no router routes)

There are no protected routes. `AppShell` picks the screen from `activeNav` and renders admin screens only when `user.isAdmin` is true. The server is the real gate: `AdminController` is `@PreAuthorize("hasRole('ADMIN')")` and `JWTAuthenticationFilter` rejects bad tokens with 401.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant SH as AppShell.jsx
    participant AU as useAuth (AuthContext)
    participant AV as AdminView.jsx
    participant C as api/client.js
    participant F as JWTAuthenticationFilter
    participant API as AdminController

    U->>SH: click Admin nav or notification link
    SH->>AU: read user.isAdmin, isLoggedIn
    SH->>SH: goNav('admin') sets activeNav
    alt activeNav is admin and user.isAdmin
        SH->>AV: render AdminView
        AV->>C: GET /admin/videos (JWT)
        C->>F: request
        F->>F: verifyToken and credentials version check
        F->>API: pass with ROLE from token
        API-->>AV: 200 page of AdminVideoDto
    else activeNav is admin and not admin
        SH->>SH: falls through to ComingSoon page (no API call)
    else settings and not logged in
        SH->>SH: render ComingSoon page=settings
    end
    opt forged client state or demoted admin
        C->>F: GET /admin/videos with non-admin JWT
        F->>API: pass
        API-->>C: 403 from PreAuthorize
    end
```

## 4. React Query data fetch with 401 handling

A hook such as `useVideos` or `useMyCollections` calls `api.get`. `client.js` adds `Authorization`, `X-Guest-Token` and `Accept-Language`. Only a `TOKEN_REVOKED` 401 triggers a global logout through the `auth:revoked` window event. Any other failure is thrown as `{ message, status, code }` to React Query, which retries once (`retry: 1`) and then exposes `error` to the component.

```mermaid
sequenceDiagram
    autonumber
    participant CP as Component (VideoFeed.jsx, LibraryView.jsx)
    participant Q as React Query (useVideos, useMyCollections)
    participant C as api/client.js request()
    participant F as JWTAuthenticationFilter
    participant API as Controller
    participant AP as AuthProvider
    participant SH as AppShell

    CP->>Q: useQuery(['videos', category])
    Q->>C: videosApi.list() GET /video-stream/list
    C->>API: fetch with headers (public path, no JWT check)
    API-->>C: 200 ResultListInfo of VideoDto
    C-->>Q: data
    Q-->>CP: videos (staleTime 5 min, mapApiVideo, sorted by upvotes)

    CP->>Q: useQuery(['collections','mine'])
    Q->>C: api.get('/collections')
    C->>F: GET /collections (Authorization jwt)
    alt token valid
        F->>API: continue
        API-->>C: 200 ResultListInfo
        C-->>Q: data
    else token revoked (password changed elsewhere)
        F-->>C: 401 error.message TOKEN_REVOKED
        C->>C: remove jwt and user from localStorage
        C-)AP: window event auth:revoked
        AP->>AP: jwt = null, user = null, sessionNotice set
        AP-->>SH: sessionNotice
        SH->>SH: toast sessionRevoked for 5 seconds
        C-->>Q: throw message, status 401
        Q->>Q: retry once then error state
    else other 401 (expired token)
        F-->>C: 401 ResultErrorInfo
        C-->>Q: throw message, status 401
        Q-->>CP: error shown inline, session cleared at next boot via /user/me
    end
```
