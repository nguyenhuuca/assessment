# ADR-0010: Add Trivy Dependency & Filesystem Scanning to CI

## Status
Accepted

## Date
2026-05-30

## Context

The Funny Movies CI pipeline currently covers build, test (coverage gate + SonarCloud), Docker build, and deploy. There is **no automated vulnerability scanning** of the container image or runtime dependencies before pushing to Docker Hub and deploying to production.

**Current gaps identified in the 2026-05-30 system health report:**
- No container image scanning in CI (MEDIUM severity)
- No secrets scanning in CI (MEDIUM severity)
- The JWT library `0.11.5` went undetected in CI for months — only caught by manual review

**Risk:**
- A vulnerable OS package or transitive dependency can be shipped to production without any warning
- Docker Hub and Kubernetes do not scan images automatically in this setup
- The `nguyenhuuca/funny-app` image is based on `eclipse-temurin:24-jre-alpine` — Alpine is minimal but still ships packages that receive CVEs

**Alternatives considered:**

| Tool | Pros | Cons |
|------|------|------|
| **Trivy** (Aqua Security) | Free, fast, OSS, GitHub Action available, scans image + filesystem + secrets | Requires Docker image to be built first |
| Snyk | Deep language-level scanning, fix suggestions | Paid for full features, external service |
| OWASP Dependency-Check | Maven plugin, Java-focused | Slow (~5 min), no image scanning, XML report only |
| Dependabot | Auto-PRs for outdated deps | Reactive only, no image scanning, no gate |
| Grype (Anchore) | Similar to Trivy, good SBOM support | Less ecosystem adoption |

## Decision

Add **Trivy** as a new `scan` job in the GitHub Actions CI pipeline, running **after the Docker image is built and pushed**.

**Scan target:**  
**Filesystem** (`api/` directory) — catches CVEs in Maven dependencies declared in `pom.xml` and compiled JARs in `target/`. Docker image scanning is deferred until Docker is used in production.

**Gate policy:**
- Fail the pipeline on any **CRITICAL** severity finding
- **HIGH** severity findings: reported but non-blocking (backlog for now, tighten after baseline is established)
- Upload SARIF report to GitHub Security tab for visibility

**Rationale for Trivy over alternatives:**
- Zero cost, maintained by Aqua Security, wide adoption
- `aquasecurity/trivy-action` integrates natively with GitHub Actions and Security tab (SARIF)
- Scans both image and filesystem in one tool — no need for a separate Maven plugin
- Fast enough (~30–60s) to not slow the pipeline meaningfully

## Consequences

### Positive
- Automated CVE detection before every production deploy
- SARIF upload surfaces findings in GitHub Security → Code Scanning tab
- Catches both OS-level (Alpine) and Java library vulnerabilities
- Sets foundation for secrets scanning (Trivy supports `--scanners secret`)

### Negative
- Pipeline gains one more job (~60s on a warm runner)
- CRITICAL gate may cause false-positive blocks on first run if existing image has unresolved CVEs — need a one-time triage before enabling the gate
- Trivy DB must be downloaded each run (~200MB); mitigated by caching

### Trade-offs accepted
- HIGH severity is non-blocking initially to avoid disrupting the release cadence; the threshold will be tightened after the first clean baseline is established
- Image scan requires the Docker job to complete first, so `scan` runs sequentially after `docker` (no parallelism gain here)

## Related

- System Health Report 2026-05-30 — identified this as MEDIUM priority open gap
- ADR-0002 — CI/CD pipeline architecture
- `.github/workflows/funnyapp-ci.yml` — target file for implementation
- `docs/plans/plan-trivy-scan.md` — implementation plan
- Note: Docker image scanning to be added when Docker is used in production deployment
