# `ci/` — vendored CI tooling

## `aic-cli.jar`

The headless **Abstractness & Instability Calculator** CLI, used by `funnyapp-ci.yml` to run a
package-metrics / architecture-conformance check on `api/` after the build.

- Source: <https://github.com/nguyenhuuca/abstractness-instability-calculator> (`core` module, shaded jar).
- Runs on the CI's JDK (Java 22+ bytecode; forward-compatible).
- Reads the compiled classes under `api/target/classes`, so it runs **after** the build step.

> ⚠️ This jar is **vendored temporarily**. Prefer replacing it with a download from a GitHub Release
> (or building it from source in CI) once the calculator publishes releases, to avoid a binary in git
> and version drift.

### What the CI runs

```bash
java -jar ci/aic-cli.jar --scan=api --output=api/target/aic-metrics.json
```

No flags — the whole check policy lives in **`api/aic-check.yaml`**, which the CLI auto-discovers.
The step is currently **report-only** (`continue-on-error: true`): it prints a summary, uploads
`aic-metrics.json` as the `aic-metrics` artifact, and never fails the build.

## Policy — `api/aic-check.yaml`

That file (at the `api/` module root, **not** in `src/main/resources`, so it isn't bundled into the
app jar) defines the gates and the architecture check. Currently effective:

| Check | State |
|-------|-------|
| `max-package-distance` | disabled |
| `forbidden-zones` | disabled |
| `max-average-distance` | disabled |
| `no-cycles` | **enabled** |
| `max-complexity` | **enabled**, threshold 15 |
| `banned-apis` | **enabled** (System.exit, java.util.Date, java.sql outside the repository layer) |
| architecture | **enabled**, template `layered` |
| `dead-code` | **enabled** (report-only) |

Edit that file to change thresholds, enable/disable gates, or switch the architecture template/spec.
Precedence is **code defaults < `aic-check.yaml` < CLI flags**.

### Make the check blocking

Remove `continue-on-error: true` from the "Architecture & metrics check" step in
`funnyapp-ci.yml`; then any gate or architecture violation (exit code `1`) fails the build.

### Ownership

`.github/CODEOWNERS` requires **@nguyenhuuca** to approve changes to `api/aic-check.yaml` (once branch
protection's "Require review from Code Owners" is enabled on `main`).

## Update the jar

```bash
# in the calculator repo
mvn -pl core -am clean package -DskipTests
cp core/target/aic-cli.jar /path/to/assessment/ci/aic-cli.jar
```

## View results

After a run: **Actions → the workflow run → Artifacts → `aic-metrics`** (the full JSON envelope), or
read the "Show architecture & metrics summary" step log.
