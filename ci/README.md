# `ci/` — vendored CI tooling

## `aic-cli.jar`

The headless **Abstractness & Instability Calculator** CLI, used by `funnyapp-ci.yml` to run a
package-metrics / architecture-conformance check on `api/` after the build.

- Source: <https://github.com/nguyenhuuca/abstractness-instability-calculator> (`core` module, shaded jar).
- Runs on the CI's JDK (Java 22+ bytecode; forward-compatible).
- The CI step is **report-only** (`continue-on-error`) for now — it prints a summary and uploads
  `aic-metrics.json` as an artifact, but does not fail the build.

> ⚠️ This jar is **vendored temporarily**. Prefer replacing it with a download from a GitHub Release
> (or building it from source in CI) once the calculator publishes releases, to avoid a binary in git
> and version drift.

### Update it

```bash
# in the calculator repo
mvn -pl core -am clean package -DskipTests
cp core/target/aic-cli.jar /path/to/assessment/ci/aic-cli.jar
```

### What the CI runs

```bash
java -jar ci/aic-cli.jar --scan=api --output=api/target/aic-metrics.json
```

The check policy (gates + architecture) lives in **`api/aic-check.yaml`** — the CLI discovers it
automatically, so no flags are needed. Edit that file to change thresholds, enable/disable gates, or
switch the architecture template.
