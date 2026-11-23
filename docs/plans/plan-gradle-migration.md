# Plan: Migrate the backend build from Maven to Gradle

| Field | Value |
|-------|-------|
| Status | 📋 Draft (waiting for the owner to accept ADR-0040) |
| Date | 2026-11-23 |
| Related | [ADR-0040](../adr/0040-gradle-build.md) (build tool), [ADR-0033](../adr/0033-spring-boot-4-upgrade.md) (Boot 4 BOM) |
| Size | Small/Medium tooling change: no PRD, no sequence diagram (no runtime flow changes) |

## Objective

Replace `api/pom.xml` with Gradle (Kotlin DSL + wrapper) without changing behaviour. The build must keep the same dependencies, the same `funny-app-1.0.0.jar`, the same `app.version` in prod and the same 90% LINE gate. CI, scripts, agent rules and living docs switch to Gradle. Historical ADRs, plans and reports stay as they are.

## Out of scope

- Upgrading dependencies or Gradle plugins beyond what the pom already uses (the Two Hats Rule applies).
- Multi-module split, build cache server, Develocity.
- `webapp/` (Vite) and the remote deploy script on the VM. The JAR name stays the same, so neither needs a change.

## Mapping pom → Gradle

| pom | Gradle (`api/build.gradle.kts`) |
|-----|------------------|
| parent `spring-boot-starter-parent:4.1.2` | `id("org.springframework.boot") version "4.1.2"` + `io.spring.dependency-management` |
| `java.version=24`, `--enable-preview` (compiler, surefire `argLine`) | `java.toolchain.languageVersion = 24`; `--enable-preview` on `JavaCompile.options.compilerArgs`, `Test.jvmArgs`, `JavaExec.jvmArgs` (`bootRun`) |
| lombok in `annotationProcessorPaths` | `compileOnly` + `annotationProcessor("org.projectlombok:lombok")` (+ the test equivalents if tests use Lombok) |
| `spring-boot-configuration-processor` (optional) | `annotationProcessor` |
| `provided` / `runtime` / `test` scope | `compileOnly` / `runtimeOnly` / `testImplementation` |
| Pinned versions (json, commons-lang3, commons-validator, guava, jjwt 0.13.0, springdoc 3.2.0, commons-codec, zxing, google drive/auth, otel annotations, ff-java-server-sdk) | Same versions, kept in one `val` block or `gradle/libs.versions.toml` |
| — | `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` (Gradle 9) |
| `finalName=funny-app-${project.version}`, version `1.0.0` | `version = "1.0.0"`; `bootJar { archiveFileName = "funny-app-1.0.0.jar" }`; `jar { enabled = false }` |
| Boot parent filters `@app.build.version@` | `processResources { filesMatching("application*.y*ml") { filter<ReplaceTokens>("tokens" to mapOf("app.build.version" to buildVersion)) } }`; `buildVersion = findProperty("app.build.version") ?: "dev"` |
| jacoco `prepare-agent` + `report` (excludes: config, dto, exception, enums, entity, repo, FunnyApp, AppConstant*, WebSecurityConfig*, CacheBean) | `jacoco` plugin; `jacocoTestReport` (XML + HTML) with **this** exclude list |
| jacoco `check` 0.07 (LINE+BRANCH, different excludes) | `jacocoTestCoverageVerification`: LINE ≥ value read from `.coverage-threshold`, same excludes as the report; `check` depends on it |
| `-DskipTests`, `-DskipCoverage` | `-x test`, `-x jacocoTestCoverageVerification` |
| `sonar-maven-plugin` | `id("org.sonarqube")` + `sonar { properties { ... } }` |
| checkstyle (`skip=true`), surefire-report | Dropped (checkstyle was off; Gradle has test reports built in) |

## Tasks

Each task merges to `main` on its own. GR-1 adds Gradle next to Maven, so CI keeps using Maven until GR-3.

| ID | Task | Files | Blocked by | Acceptance criteria |
|----|------|-------|-----------|---------------------|
| GR-0 | Owner reviews and accepts ADR-0040 (Kotlin DSL, Gradle 9.x, single coverage gate) | `docs/adr/0040-gradle-build.md` | — | ADR status Accepted, or changes requested |
| GR-1 | Add Gradle next to the pom: wrapper (Gradle 9.x, `distributionSha256Sum` set), `settings.gradle.kts`, `build.gradle.kts` following the mapping above; `.gitignore` adds `build/`, `.gradle/`; regression test `BuildVersionFilteringTest` (packaged `application.yaml` has no `@app.build.version@`) | `api/gradlew*`, `api/gradle/wrapper/*`, `api/*.gradle.kts`, `.gitignore`, `api/src/test/...` | GR-0 | Parity on the same commit, recorded in the commit body: (1) the resolved runtime classpath matches `mvn dependency:list` (diff empty, or every difference explained); (2) same test count, 0 failures; (3) LINE coverage equal to Maven's ±0.1 pp, and the gate fails when the threshold is set to `.99`; (4) `build/libs/funny-app-1.0.0.jar` boots locally with `--enable-preview`, `/actuator/health` UP, `/actuator/info` or the version endpoint shows `-Papp.build.version=test-x`; (5) `mvn verify` still green |
| GR-2 | Local scripts use Gradle: `startLocal.sh` (`./gradlew clean bootJar`, `build/libs/...`), `unittest.sh` (`./gradlew test`), `qa-twice.sh` (`--tests` per class, `build/test-results/test`), `claude-autopilot-merge.sh` (`./gradlew -p api check`) | `api/*.sh`, `scripts/*.sh` | GR-1 | Each script run once locally and passes; `qa-twice.sh` copies the reports from the new path |
| GR-3 | CI + image use Gradle: `funnyapp-ci.yml` (`setup-java` + `gradle/actions/setup-gradle`, `./gradlew -p api clean bootJar -x test -Papp.build.version=prod-${GITHUB_SHA::7}`, `./gradlew -p api check sonar`, every `api/target/...` → `api/build/...`, remove the `~/.m2` cache and the xmllint coverage step), `autopilot.yml` (`cache`, allowlist `Bash(./gradlew:*)`), `Dockerfile` (`COPY build/libs/funny-app-1.0.0.jar`), aic-cli scan path | `.github/workflows/*.yml`, `api/Dockerfile`, `ci/README.md` | GR-1 | Owner approves the push; CI green on `main`; Sonar shows coverage; prod deploy OK: `/actuator/health` UP and the app reports version `prod-<sha>` for this commit; aic step either reads `build/classes` or is noted as broken (report-only) |
| GR-4 | Remove Maven: delete `api/pom.xml` and the `.mvn` lines in `.gitignore`; update the rules and living docs: `tech-strategy.md` (Build Tool, Required config, CI rows, `mvn verify`) + mirror `docs/claude/rules/`, `CLAUDE.md`, `AGENTS.md`, `api/CLAUDE.md`, `README.md`, autopilot `SKILL.md` + `report.template.md` (+ mirror), `templates/artifacts/spec.template.md` (+ mirror), `docs/guides/claude-guide-{en,vi}.md`, `docs/architecture/diagrams/ci-pipeline.md`, `docs/puml/funny-app-ssh-deploy.puml`, `docs/claude/index.md`; mark ADR-0040 as Accepted; update tracking + mkdocs | the files listed | GR-2, GR-3 + one green prod deploy on Gradle | `grep -rn "mvn \|pom.xml" --exclude-dir={adr,plans,reports,specs,prd,sequence,node_modules}` returns no hits in rules or living docs; mkdocs build OK; CI green after the push |

Dependency graph: `GR-0 → GR-1 → {GR-2, GR-3} → GR-4` (GR-2 and GR-3 can run in parallel).

## Risks and checks

- **The coverage gate moves**: today the CI number comes from the report's exclude list, and the Gradle gate uses that same list, so it should match. GR-1 must show the numbers side by side.
- **`app.version` in prod**: covered by the GR-1 regression test and the GR-3 prod check.
- **Remote deploy script on the VM** (`/opt/CICD/deploy-script/`) and `scripts/migration/02-presync.sh` use `funny-app-1.0.0.jar`. The name stays the same, so neither needs a change. If GR-3 shows the CI scp step uses a path under `api/target/`, fix it in the workflow only.
- **Gradle daemon on CI**: `setup-gradle` handles caching. Do not commit `gradle.properties` with `org.gradle.jvmargs` larger than the runner can handle.
- **Wrapper jar is a binary in a public repo**: generate it with `gradle wrapper --gradle-distribution-sha256-sum ...` and check it with `gradle/actions/wrapper-validation` in CI (GR-3).
