# Sequence Diagrams: Spring Boot 4.1 Upgrade

- **PRD:** [PRD-spring-boot-4-upgrade](../prd/PRD-spring-boot-4-upgrade.md)
- **ADR:** [0033-spring-boot-4-upgrade](../adr/0033-spring-boot-4-upgrade.md)
- **Spec:** [spec-spring-boot-4-upgrade](../specs/spec-spring-boot-4-upgrade.md)
- **Status:** Draft

The upgrade adds no new flow. These diagrams show the existing flows whose internals change: which component now does the work (Jackson 3, `jjwt-orgjson`, Boot 4 modules), and where the spec's rules and edge cases apply.

## 1. Startup on the prod VM (Boot 4.1)

The JVM starts with the existing `startServer.sh` flags. Liquibase must find nothing to apply, and the JWT/JSON classes are warmed up before Tomcat accepts traffic (spec FR-2, FR-5, Rule 6, EC-9).

```mermaid
sequenceDiagram
    autonumber
    participant SH as startServer.sh (JVM flags)
    participant BOOT as Spring Boot 4.1 (main thread)
    participant LQ as Liquibase (Boot 4 module)
    participant DB as PostgreSQL
    participant JWT as JwtProvider.warmUp()
    participant TC as Tomcat
    participant ACT as /actuator/health

    SH->>BOOT: java --enable-preview -jar funny-app.jar
    BOOT->>LQ: auto-configure (spring.liquibase.*)
    LQ->>DB: read DATABASECHANGELOG
    DB-->>LQ: all changesets applied by 3.5 build
    LQ-->>BOOT: 0 pending changesets (Rule 6)
    BOOT->>JWT: @PostConstruct warmUp()
    JWT->>JWT: Jwts.builder()...compact() (org.json writer init)
    JWT->>JWT: parser.parseSignedClaims() (org.json reader init)
    Note over JWT: class init on main thread, not on virtual threads (EC-9)
    BOOT->>TC: start connector :8081
    TC-->>BOOT: accepting requests
    ACT-->>SH: UP
```

## 2. Authenticated request with a token issued before the upgrade

A user who logged in on 3.5 calls an API after the deploy. The JSON claims are now parsed by org.json instead of Jackson 2 (spec Rule 5, EC-1, EC-2).

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (webapp)
    participant F as JWT filter (Security 7 chain)
    participant JP as JwtProvider.verifyToken()
    participant OJ as jjwt-orgjson reader
    participant C as Controller (/v1/funny-app/...)
    participant MVC as Spring MVC JsonMapper (Jackson 3)

    U->>FE: open app (token from 3.5 in storage)
    FE->>F: GET /v1/funny-app/... Authorization: Bearer <3.5 token>
    F->>JP: verifyToken(token)
    JP->>OJ: parse header + claims JSON
    OJ-->>JP: claims {id, email, role, permissions, cv?}
    JP->>JP: signature check with JWT_SECRET (unchanged)
    JP->>JP: convertValue: id→Long, permissions→int, cv→int or 0
    JP-->>F: UserDetailDto (same as on 3.5)
    F->>C: request with SecurityContext
    C-->>MVC: ResultObjectInfo<T>
    MVC-->>FE: 200 JSON (same fields/values, order may differ)
    FE-->>U: page renders as before
```

## 3. Expired or tampered token

The failure path keeps the same sub-codes and body format (spec EC-3, FR-4).

```mermaid
sequenceDiagram
    autonumber
    participant FE as Browser (webapp)
    participant F as JWT filter
    participant JP as JwtProvider.verifyToken()
    participant UE as UnauthorizedException.toJson()

    FE->>F: request with expired or tampered token
    F->>JP: verifyToken(token)
    alt expired
        JP-->>F: throw UnauthorizedException("TOKEN_EXPIRED", 602)
    else bad signature / malformed
        JP-->>F: throw UnauthorizedException("TOKEN_INVALID", 601)
    end
    F->>UE: toJson() with shared Jackson 3 mapper config
    UE-->>F: {"status":"FAILED","error":{... "timestamp":"dd-MM-yyyy hh:mm:ss"}}
    F-->>FE: 401 + same body as 3.5
```

## 4. Request body deserialization rules

How the Jackson 3 mapper configured in FR-4 handles a client request body (spec Rules 3–4, EC-4, EC-5, EC-6).

```mermaid
sequenceDiagram
    autonumber
    participant FE as Browser (webapp)
    participant MVC as Spring MVC JsonMapper (Jackson 3, FR-4 config)
    participant C as Controller
    participant CA as @ControllerAdvice

    FE->>MVC: POST/PATCH body JSON
    alt null for primitive field
        MVC->>C: field = 0 / false (FAIL_ON_NULL_FOR_PRIMITIVES=false)
        C-->>FE: 200 as on 3.5
    else unknown property
        MVC->>C: property ignored (FAIL_ON_UNKNOWN_PROPERTIES=false)
        C-->>FE: 200 as on 3.5
    else trailing tokens after root value
        MVC-->>CA: HttpMessageNotReadableException (Jackson 3 default)
        CA-->>FE: 400 ResultErrorInfo (accepted change, Rule 4)
    end
```

## 5. Parsing an external payload with a code-built mapper

The classes that built their own `ObjectMapper` now use the shared Jackson 3 settings, so a provider adding fields does not break parsing (spec FR-4, EC-5). This example uses yt-dlp; YouTube API, GitHub webhook and ChatGPT follow the same pattern.

```mermaid
sequenceDiagram
    autonumber
    participant W as VideoImportWorker
    participant Y as YtDlpClientImpl
    participant P as yt-dlp process
    participant M as JsonMapper (shared FR-4 settings)

    W->>Y: fetchMetadata(url)
    Y->>P: yt-dlp --dump-json url
    P-->>Y: JSON (may contain new fields)
    Y->>M: readTree / readValue
    M-->>Y: JsonNode (unknown fields ignored)
    alt malformed output
        M-->>Y: JacksonException (unchecked, was JsonProcessingException)
        Y-->>W: same fallback as 3.5 (job marked FAILED with message)
    end
    Y-->>W: metadata
```

## 6. Deploy and rollback

The upgrade ships as one artifact. Rollback means redeploying the previous 3.5 artifact; no database step is needed (spec NFR Availability, Rule 6).

```mermaid
sequenceDiagram
    autonumber
    actor O as Owner
    participant GH as GitHub Actions (funnyapp-ci.yml)
    participant VM as Prod VM / Helm
    participant APP as funny-app 4.1
    participant DB as PostgreSQL

    O->>GH: push main (after approval)
    GH->>GH: mvn verify (JsonContractTest, coverage ≥ 90%)
    GH->>VM: deploy JAR / image tag 4.1
    VM->>APP: start (diagram 1)
    APP->>DB: Liquibase: 0 pending
    O->>APP: prod smoke test (login, MFA, stream, import, admin, SSE)
    alt smoke test fails or app hangs
        O->>VM: redeploy previous 3.5 JAR / image tag
        VM->>DB: no migration to revert
        VM-->>O: 3.5 back within 10 min
    else all pass
        O-->>O: watch 7 days for incidents
    end
```
