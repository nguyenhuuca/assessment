# Plan: Trivy Vulnerability Scan on JAR & Dependencies

**ADR:** ADR-0010  
**Date:** 2026-05-30  
**Status:** Ready to implement  
**Effort:** ~30 minutes  

---

## Context

Production deployment uses **JAR over SSH** — Docker image is built but not yet used in production. The scan target is therefore the **filesystem** (`api/` directory): Maven dependencies declared in `pom.xml` and resolved JARs in `target/`.

Trivy `fs` mode detects CVEs in Java libraries by scanning `pom.xml` and the compiled JAR — no Docker required.

---

## Current Pipeline

```
build ──→ test ──┬──→ deploy (SSH)
                 └──→ docker (not in prod)
```

## Target Pipeline

```
build ──→ test ──┬──→ scan (fs) ──→ deploy (SSH)
                 └──→ docker
```

`scan` runs **after `test`** (needs the compiled JAR from `build`) and **blocks `deploy`** on CRITICAL findings.

---

## Change: Add `scan` job to `funnyapp-ci.yml`

Add this job and update `deploy` to depend on it:

```yaml
  scan:
    name: 🔍 Trivy Vulnerability Scan
    runs-on: ubuntu-latest
    needs: [ build ]

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Download built JAR
        uses: actions/download-artifact@v4
        with:
          name: built-jar
          path: api/target/

      - name: Cache Trivy DB
        uses: actions/cache@v4
        with:
          path: ~/.cache/trivy
          key: ${{ runner.os }}-trivy-db
          restore-keys: ${{ runner.os }}-trivy-db

      - name: Scan filesystem for CVEs (CRITICAL gate)
        uses: aquasecurity/trivy-action@master
        with:
          scan-type: fs
          scan-ref: api/
          format: table
          exit-code: '1'
          severity: CRITICAL
          ignore-unfixed: true

      - name: Scan filesystem for CVEs (SARIF upload)
        uses: aquasecurity/trivy-action@master
        if: always()
        with:
          scan-type: fs
          scan-ref: api/
          format: sarif
          output: trivy-results.sarif
          severity: CRITICAL,HIGH
          ignore-unfixed: true

      - name: Upload results to GitHub Security tab
        uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: trivy-results.sarif
          category: trivy-fs
```

Update `deploy` job to wait on `scan`:

```yaml
  deploy:
    name: 🚀 Deploy to Server
    runs-on: ubuntu-latest
    needs: [ test, scan ]   # <-- add scan here
```

---

## Behaviour

| Scenario | Result |
|----------|--------|
| No CRITICAL CVEs | ✅ scan passes → deploy proceeds |
| CRITICAL CVE with fix available | ❌ scan fails → deploy blocked |
| CRITICAL CVE with no fix | ✅ ignored (`ignore-unfixed`) → deploy proceeds |
| HIGH CVE | ✅ visible in GitHub Security tab, does not block deploy |

---

## First-Run Checklist

- [ ] Add `scan` job to `funnyapp-ci.yml`
- [ ] Add `scan` to `deploy.needs`
- [ ] Push → check GitHub Actions for scan results
- [ ] Open GitHub → Security → Code Scanning to review findings
- [ ] If false positives: create `api/.trivyignore` with CVE IDs to suppress

## `.trivyignore` (if needed)

```
# CVE-YYYY-XXXXX  reason: not exploitable in this context
```

## Rollback

Remove the `scan` job and revert `deploy.needs` to `[ test ]`. No application code touched.
