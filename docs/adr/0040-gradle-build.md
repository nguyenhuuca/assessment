# ADR-0040: Build the backend with Gradle instead of Maven

## Metadata

**Status:** Proposed · **Date:** 2026-11-23 · **Deciders:** owner · **Tags:** build, ci, tooling  
**Related PRD:** N/A · **Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** ⚠️ Deviation: `tech-strategy.md` names "Maven 3.6+" as the build tool. The owner asked for this migration (2026-11-23). When this ADR is Accepted, `tech-strategy.md` changes in the same commit that removes `pom.xml` (plan task GR-4).

Plan: [plan-gradle-migration](../plans/plan-gradle-migration.md)

---

## Context

`api/` is built by Maven (`api/pom.xml`, 378 lines, parent `spring-boot-starter-parent` 4.1.2, Java 24 with `--enable-preview`). No ADR has recorded the build tool. The choice is written down only in `tech-strategy.md`.

Maven is used in more places than the pom:

- **CI** (`.github/workflows/funnyapp-ci.yml`): `mvn package` with `-Dapp.build.version=prod-<sha>`, `mvn verify sonar`, the `~/.m2` cache, about 15 `api/target/...` paths (JAR, JaCoCo XML/HTML, surefire reports, `aic-metrics.json`, the downloaded JAR artifact for the Docker/Trivy job). `autopilot.yml` uses `cache: maven` and allows `Bash(mvn:*)`.
- **Scripts:** `api/startLocal.sh`, `api/unittest.sh`, `scripts/qa-twice.sh` (`-Dtest=`, `target/surefire-reports`), `scripts/claude-autopilot-merge.sh` (`mvn verify` gate).
- **Image and VM:** `api/Dockerfile` copies `target/funny-app-1.0.0.jar`. The VM runs `funny-app-1.0.0.jar` through `startServer.sh` and a deploy script that lives on the VM (outside the repo).
- **Agent rules and docs:** `tech-strategy.md`, `CLAUDE.md`, `AGENTS.md`, `api/CLAUDE.md`, `README.md`, the autopilot skill and the spec template (plus mirror copies under `docs/claude/`).

What the build does now:

- **Resource filtering:** `application.yaml` has `version: ${APP_VERSION:@app.build.version@}`. The Boot parent fills in `@...@` tokens, and prod reads its version from that value.
- **Coverage:** the JaCoCo `check` goal in the pom has a 7% minimum, so it never fails. The real gate is a CI step that reads `api/.coverage-threshold` (`.90`) and compares it with LINE coverage in the JaCoCo report. The report and the `check` goal use **different exclude lists**.

## Decision Drivers

- Requested by the owner. Possible benefits (unconfirmed): incremental and cached builds, a single `build.gradle.kts` instead of pom XML, and coverage rules written in code.
- **No behaviour change:** the same dependencies, the same JAR name, the same `app.version` in prod, the same 90% LINE gate.
- **Trunk-based:** every step lands on `main` without breaking CI or deploy.
- Gradle must support Spring Boot 4.1. The Boot 4.1 Gradle plugin needs Gradle 8.14+ or 9.x (checked with Context7, Spring Boot v4.1.0 docs).

## Considered Options

### Option 1: Stay on Maven

| Pros | Cons |
|------|------|
| No work, no risk | Does not meet the owner's request |
| Every doc and script already matches | Coverage gate stays split between the pom (7%, never fails) and a bash step in CI |

### Option 2: Gradle with Groovy DSL (`build.gradle`)

| Pros | Cons |
|------|------|
| Spring docs show Groovy samples first | Typos are found only at runtime; weaker IDE completion |
| Short syntax | |

### Option 3: Gradle 9.x with Kotlin DSL (`build.gradle.kts`) and a committed wrapper (chosen)

| Pros | Cons |
|------|------|
| Type-checked build script with IDE completion; the Gradle default since 8.2 | First build is slower (compiles the script) |
| The wrapper pins the Gradle version, so CI, local runs and agents use the same version | `gradle-wrapper.jar` is committed (binary). The wrapper checksum must be verified |
| Spring Boot plugin + `io.spring.dependency-management` keep the same Boot BOM as the parent pom | Two build files exist until GR-4 |

## Decision

**Option 3.** Build `api/` with Gradle 9.x (latest stable, pinned by `gradlew`) and the Kotlin DSL:

1. **Same dependencies:** the Spring Boot Gradle plugin 4.1.2 and `io.spring.dependency-management` (same BOM). Versions the pom pins explicitly stay pinned. `lombok` is `compileOnly` + `annotationProcessor`. `junit-platform-launcher` is `testRuntimeOnly` (Gradle 9 needs it).
2. **Preview:** a Java toolchain 24, with `--enable-preview` on `JavaCompile`, `Test` and `JavaExec`/`bootRun`.
3. **Same artifact:** `bootJar` writes `build/libs/funny-app-1.0.0.jar`, and the plain `jar` task is disabled. This keeps the JAR name the VM and its out-of-repo deploy script expect. Only the directory changes (`target/` → `build/libs/`).
4. **Same version stamping:** `processResources` replaces `@app.build.version@` in `application*.yaml` (`ReplaceTokens`, `@` delimiters, so Spring `${...}` placeholders are left alone). The value comes from `-Papp.build.version=...` and defaults to `dev`.
5. **One coverage gate:** `jacocoTestCoverageVerification` reads `api/.coverage-threshold` (LINE, BUNDLE) and uses the **same exclude list as the report** that CI measures today. `./gradlew check` enforces it. The CI bash/xmllint step is removed. `.coverage-threshold` stays the only source of truth.
6. **Sonar:** the `org.sonarqube` plugin replaces `sonar-maven-plugin`, pointed at `build/reports/jacoco/test/jacocoTestReport.xml`.

## Consequences

**Positive**
- The coverage gate lives in one place and fails locally (`./gradlew check`), not only in CI.
- Gradle's build cache and up-to-date checks make repeated local and CI builds faster than `mvn clean package` (not measured yet; GR-1 records the timings).

**Negative / risks**
- **A wrong exclude list moves coverage.** GR-1 must show the same LINE % as Maven on the same commit (±0.1 percentage point).
- **Missing resource filtering** would make prod report `@app.build.version@` as its version. A test reads the packaged `application.yaml` and checks the value.
- **The aic-cli scan** reads `api/target/classes` (path set in `--scan=api`). GR-3 checks whether it can read `build/classes/java/main`; if it cannot, it stays report-only and is noted.
- **Docs drift:** about 190 Maven references exist. Only living docs and rules change (GR-4). ADRs, plans and reports are history and keep their Maven wording.
- **Rollback:** until GR-4, `pom.xml` still exists, so reverting the CI commit (GR-3) brings back the Maven build. After GR-4, rollback means reverting GR-4 as well.

**Neutral**
- `.funny-app.version`, the Helm tag bump, `entrypoint.sh` and `startServer.sh` do not change.
