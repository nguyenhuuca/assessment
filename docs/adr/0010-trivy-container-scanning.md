# ADR-0010: Add Trivy Dependency and Filesystem Scanning to CI

## Metadata

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2026-05-30 |
| **Deciders** | nguyenhuuca |
| **Related PRD** | N/A |
| **Domain Tags** | security, devops |
| **Supersedes** | N/A |
| **Superseded By** | N/A |

**Tech Strategy Alignment:**
- [x] Decision follows Golden Path in `.claude/rules/tech-strategy.md`

---

## Context

The CI pipeline covers build, test (coverage gate and SonarCloud), Docker build, and deploy. There is no automated vulnerability scanning of the container image or runtime dependencies before pushing to Docker Hub and deploying to production.

Two gaps were identified in the 2026-05-30 system health report: no container image scanning (MEDIUM severity) and no secrets scanning (MEDIUM severity). The JWT library at version 0.11.5 went undetected in CI for months and was only caught by manual review. Docker Hub and Kubernetes do not scan images automatically in this setup. The production image is based on `eclipse-temurin:24-jre-alpine`, which is minimal but still receives OS-level CVEs.

---

## Decision Drivers

- Vulnerable OS packages or transitive Java dependencies must be caught before reaching production
- Scanning must integrate with GitHub Actions without requiring an external paid service
- Pipeline overhead must remain acceptable (target: under 90 seconds per run)
- Findings must be surfaced in a developer-visible interface (GitHub Security tab)
- The gate policy must not block the release cadence on the first run before a baseline is established

---

## Considered Options

### Option 1: Trivy (Aqua Security) — chosen
Free, open-source scanner with a native GitHub Action; scans filesystem, container images, and secrets in one tool.

| Pros | Cons |
|------|------|
| Zero cost, OSS, maintained by Aqua Security | Requires the Docker image to be built before image scanning can run |
| Native `aquasecurity/trivy-action` with SARIF output to GitHub Security tab | Trivy DB is ~200MB per download (mitigated by caching) |
| Scans both filesystem and container image in one tool | CRITICAL gate may cause false-positive blocks on first run |
| Fast (~30–60s on a warm runner) | |

### Option 2: Snyk
Deep language-level scanning with fix suggestions.

| Pros | Cons |
|------|------|
| Detailed fix suggestions per vulnerability | Full feature set requires a paid plan |
| Strong Java and Maven support | External SaaS dependency; not self-contained in CI |

### Option 3: OWASP Dependency-Check
Maven plugin for Java dependency scanning.

| Pros | Cons |
|------|------|
| Java-focused, integrates as a Maven plugin | Slow (~5 minutes per run) |
| No external service required | Cannot scan container images |
| | XML-only report; no native GitHub Security tab integration |

### Option 4: Dependabot
Automated pull requests for outdated dependencies.

| Pros | Cons |
|------|------|
| No pipeline overhead; runs asynchronously | Reactive only; does not block deploys on vulnerabilities |
| Native GitHub integration | Cannot scan container images |
| | No active gate on severity |

### Option 5: Grype (Anchore)
Open-source scanner similar to Trivy with strong SBOM support.

| Pros | Cons |
|------|------|
| Good SBOM generation capability | Less GitHub Actions ecosystem adoption than Trivy |
| Similar feature set to Trivy | SARIF integration less mature |

---

## Decision Outcome
**Chosen Option:** Option 1 — Trivy
**Rationale:** Trivy is zero-cost, actively maintained by Aqua Security, and has the widest adoption in GitHub Actions environments. It scans both filesystem and container image in one tool, eliminating the need for a separate Maven plugin. Its SARIF output integrates natively with the GitHub Security tab. At 30–60 seconds on a warm runner it does not meaningfully slow the pipeline. Snyk was excluded due to cost. OWASP Dependency-Check was excluded for speed and lack of image scanning. Dependabot was excluded for being reactive with no gate. Grype was excluded due to lower ecosystem adoption.

The scan target is the filesystem (`api/` directory), catching CVEs in Maven dependencies declared in `pom.xml` and compiled JARs. Docker image scanning is deferred until Docker is used in the production deployment path.

Gate policy: fail on CRITICAL severity; HIGH severity is reported but non-blocking initially, to be tightened after a clean baseline is established. SARIF reports are uploaded to the GitHub Security tab on every run.

### Quantified Impact *(where applicable)*
| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| Pipeline duration | Baseline | +~60s | On a warm runner with Trivy DB cached |
| Trivy DB download | N/A | ~200MB | Mitigated by GitHub Actions cache |
| CVE visibility | Manual review only | Automated on every push | SARIF surfaced in GitHub Security tab |

---

## Consequences
**Positive:**
- Automated CVE detection runs before every production deploy
- SARIF upload surfaces findings in GitHub Security Code Scanning tab
- Catches both OS-level (Alpine) and Java library vulnerabilities in one job
- Establishes the foundation for secrets scanning (`--scanners secret` can be added later)

**Negative:**
- Pipeline gains one additional job (~60s on a warm runner)
- Trivy DB must be downloaded each run if the cache is cold (~200MB)

**Risks:**
- The CRITICAL gate may block the pipeline on the first run if existing dependencies carry unresolved critical CVEs; a one-time triage pass is required before the gate is activated
- HIGH severity findings are non-blocking initially; if the threshold is not tightened after the baseline is established, medium-term risk accumulates
- Image scanning is deferred; OS-level CVEs in the deployed container will not be caught until Docker is used in production

---

## Validation
- [x] Tech Strategy alignment confirmed
- [ ] Related plan document created: docs/plans/plan-trivy-scan.md

---

## Links
- System Health Report 2026-05-30 — source of MEDIUM priority gaps that triggered this ADR
- ADR-0002 — CI/CD pipeline architecture
- `docs/plans/plan-trivy-scan.md` — implementation plan
- `.github/workflows/funnyapp-ci.yml` — target file for implementation

---

## Changelog
| Date | Author | Change |
|------|--------|--------|
| 2026-05-30 | nguyenhuuca | Initial draft |
| 2026-05-31 | nguyenhuuca | Restructured to new ADR template |
