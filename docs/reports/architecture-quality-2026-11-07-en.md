# Code Quality Report — re-run of the 2025-07 Sonargraph criteria

**Scope:** `api/src/main/java` (production code) at commit `c01a1be0`
**Date:** 2026-11-07
**Baseline:** [architecture-quality-2025-07-13-en.md](architecture-quality-2025-07-13-en.md) (Sonargraph, 2025-07-05)

Sonargraph is not installed, so every metric was recomputed with the tools listed under [Method](#method). The coupling metrics use the same Lakos formulas as Sonargraph. Applied to the 2025 figures they give back its NCCD 0.82 and propagation cost 3.60, so the coupling rows compare like for like. The rows that do not are marked ≈.

---

## 1. Code Analysis

| Metric | 2025-07 | 2026-11 | Comment |
|--------|---------|---------|---------|
| Issue Density | 0.00 | ≈ 0.94 issues / 100 ncloc in production code (SonarCloud: 160 open = 8 bugs + 152 smells, 0 vulnerabilities; another 176 sit in test code) | ⚠️ Mostly minor smells. Only 2 bugs are real, see §6. Not comparable with Sonargraph's rule set |
| Code Duplicates (≥100 tokens, PMD CPD) | 0 | **0** | ✅ No duplication. SonarCloud agrees: 0.0% |
| Redundant Code | 0% | 2 unused classes (`enums.CacheKey`, `utils.ContractBatch`, the latter only used by tests) | ✅ Deleted in `e9dd51d7` |
| Structural Debt (cycle debt) | 0 | **0** | ✅ No cycles left to cut (§4) |
| SonarCloud technical debt | — | 1,847 min (≈ 31 h), debt ratio 0.4%, maintainability rating **A** | ✅ |

---

## 2. Coupling and Cohesion

| Metric | 2025-07 | 2026-11 | Comment |
|--------|---------|---------|---------|
| ACD (Average Component Dependency) | 5.08 | **6.04** | ⚠️ Higher, mostly because the system is 2.5× larger. See NCCD |
| CCD (Cumulative Component Dependency) | 716 | **2,156** | 📈 Grows with size (357 vs 141 components) |
| NCCD (Normalized CCD) | 0.82 | **0.80** | ✅ Below 1 and slightly better: relative to its size, the system is no more tangled than in 2025 |
| Propagation Cost | 3.60% | **1.69%** | ✅ Halved: a change reaches a smaller share of the code |
| Component dependencies | — | 1,070 edges | — |
| Maintainability Level | 92.54 | n/a (Sonargraph-proprietary) | SonarCloud maintainability rating A is the nearest substitute |

Coupling hotspots: these classes depend, directly or transitively, on the most components.

| Class | Depends on (transitive) | Direct fan-out |
|-------|------------------------|----------------|
| `service.impl.SuggestionBulkServiceImpl` | 55 | — |
| `service.impl.UserServiceImpl` | 48 | 22 |
| `service.impl.VideoSuggestionServiceImpl` | 48 | 27 |
| `web.UserController` | 43 | 21 |
| `web.AdminController` | 41 | **31** (highest) |

Most-used classes (fan-in), which are stable and should change rarely: `exception.CustomException` (32), `dto.user.UserDetailDto` (26), `config.AppProperties` (23), `entity.User` (22), `utils.AppUtils` (20).

---

## 3. Complexity

| Metric | 2025-07 | 2026-11 | Comment |
|--------|---------|---------|---------|
| Average Complexity (McCabe, per method) | 1.65 | ≈ **2.2** (median 1) | ✅ Still very low. lizard counts slightly higher than Sonargraph |
| Complex Methods (CCN > 15) | 0 | **2** → 0 after `f9f00ede` | ✅ See below |
| Methods with CCN > 10 | — | 10 of 989 | — |
| Methods longer than 50 lines | — | 1 | — |

Methods over the threshold, with SonarCloud's cognitive complexity in brackets:

| CCN | Method |
|-----|--------|
| 19 | `service.impl.OAuthDriveUploader.upload` (cognitive 24) |
| 17 | `service.impl.UserSettingsServiceImpl.updateSettings` |

Just below the threshold, but SonarCloud flags their cognitive complexity: `client.YtDlpClientImpl` line 229 (cognitive 20), `client.FfmpegHlsClient` line 112 (17), and `service.impl.VideoCommentServiceImpl` line 49 (16).

---

## 4. Cyclic Dependencies

| Metric | 2025-07 | 2026-11 | Comment |
|--------|---------|---------|---------|
| Cyclic Packages | 0 | **0** | ✅ Fixed on 2026-11-07 (`c01a1be0`). Until then, 6 packages formed one cycle |
| Cyclic Components (classes) | 0 | **0** | ✅ No class-level cycle, from bytecode |
| Relative Entanglement | 0.00% | **0.00%** | ✅ |

The package cycle (`cache`, `client`, `filter`, `service`, `service.impl`, `service.notification`) was closed by two back-edges: `client → service.impl` (`HlsStorage`) and `service.notification → service.impl` (`snippetOf`). Both are now removed. `PackageCycleTest` fails the build if a new package cycle appears.

IntelliJ's DSM still shows red cells when it is run on the whole `api` directory, because test classes (e.g. `web.*PermissionTest` → `filter`) cross packages both ways. Run it with the **Production Files** scope to see production structure only.

---

## 5. System Size

| Metric | 2025-07 | 2026-11 | Change |
|--------|---------|---------|--------|
| Lines of Code (ncloc) | 6,735 | **≈ 17,000** (SonarCloud 17,014; lizard 16,824) | ×2.5 |
| Number of Components (top-level types) | 141 | **357** | ×2.5 |
| Java Packages | 20 | **33** | +13 |
| Total Source Files | 141 | **357** | ×2.5 |
| Methods | — | 989 (lizard) | — |

---

## 6. Findings Worth Acting On

| # | Finding | Where | Severity |
|---|---------|-------|----------|
| 1 | `InterruptedException` is caught and rethrown without restoring the interrupt flag (SonarCloud S2142) | `service.impl.VideoStorageServiceImpl` line 177, `utils.TaskUtils` line 73 | Medium |
| 2 | Two methods have CCN > 15 | §3 | Medium |
| 3 | `web.AdminController` has the highest fan-out (31): it serves many admin areas at once | `web.AdminController` | Medium |
| 4 | Dead code | `enums.CacheKey`, `utils.ContractBatch` | Low |
| 5 | SonarCloud reports coverage 0.0% although JaCoCo measures ≥ 90%: CI passes `api/target/...`, which the scanner resolves against `api/` (log: "No coverage report can be found") | CI `funnyapp-ci.yml` | Low (reporting only) |

SonarCloud bugs judged not actionable:
- S2184 in `AppConstant`: `20 * 1024 * 1024` cannot overflow `int`. Only the "5MB" comment is wrong.
- S3077 on the `volatile` fields in `JwtProvider`: the fields hold immutable objects, and the code comment documents why a double build is harmless.
- S2259 in `AdminController.bulkAccounts`: the endpoint is behind authentication, so the current user is never null.

---

## Follow-ups (2026-11-07)

| # | Action | Commit |
|---|--------|--------|
| 1 | Restore the interrupt flag before rethrowing, with regression tests that fail without the fix | `99eee906` |
| 2 | Split `OAuthDriveUploader.upload` (CCN 19 → 4) and `UserSettingsServiceImpl.updateSettings` (17 → 1); max CCN is now 11 | `f9f00ede` |
| 3 | `AdminController` fan-out: open, needs its own plan | — |
| 4 | Delete `CacheKey` and `ContractBatch` | `e9dd51d7` |
| 5 | Sonar JaCoCo path made module-relative | `05f51b1e` |

---

## Summary

- ✅ The structure stays clean while the code grew 2.5×: no package or class cycles, no duplication, NCCD 0.80 (was 0.82), and propagation cost halved (1.69% vs 3.60%).
- ⚠️ ACD rose from 5.08 to 6.04, which is expected at 357 components. The coupling is concentrated in a few classes: `AdminController`, `SuggestionBulkServiceImpl`, `UserServiceImpl` and `VideoSuggestionServiceImpl`.
- ⚠️ 2 methods exceed CCN 15, 2 classes are dead code, and 2 places swallow the interrupt flag. All are small fixes.
- 📌 Guards in place: `PackageCycleTest` (package cycles), CI coverage gate ≥ 90%, and the aic-cli metrics report (report-only).

---

## Method

| Metric group | Tool | How |
|--------------|------|-----|
| ACD, CCD, NCCD, propagation cost, class cycles | `jdeps -verbose:class` (JDK 24) on `target/classes` | Nested classes are folded into their top-level type (one component per source file, like Sonargraph). CCD = Σ components reachable from each component (itself included). ACD = CCD/n. NCCD = CCD / ((n+1)·log2(n+1) − n). Propagation cost = CCD / n². Cycles via Tarjan SCC |
| Package cycles | jdeps graph collapsed to packages, plus `PackageCycleTest` | — |
| Complexity, size | lizard 1.25.0 | Per-method CCN, NLOC |
| Duplicates | PMD CPD 3.26.0 (`maven-pmd-plugin:cpd`) | `minimumTokens=100`. A run at 50 tokens finds 14 small blocks (the largest: 16 lines inside `ChatGptServiceImpl`, 15 lines shared by `FfmpegHlsClient` and `YtDlpClientImpl` process handling) |
| Issues, debt, bugs | SonarCloud project `nguyenhuuca_assessment`, analysis of 2026-11-07 | Public web API |
| Redundant code | jdeps fan-in = 0 and no Spring/JPA entry annotation, confirmed by grep | — |

Annotation-only references, such as `@Constraint(validatedBy = …)`, are invisible to jdeps. They were checked by grep before any class was reported as unused.
