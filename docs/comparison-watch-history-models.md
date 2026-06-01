# Model Comparison — Watch History (4-Tier Artifact Set)

**Purpose:** Compare output quality of two models on the *same* feature (Watch History), across the full pipeline PRD → ADR → Spec → Plan.
**Feature:** Watch History for the Funny Movies video app.
**Date:** 2026-06-01
**Author:** nguyenhuuca

| Tier | Baseline (sibling model) | Opus 4.8 |
|------|--------------------------|----------|
| PRD | `docs/prd/PRD-watch-history.md` | `docs/prd/PRD-watch-history-opus.md` |
| ADR | `docs/adr/0013-watch-history-design.md` | `docs/adr/0014-watch-history-design-opus.md` |
| Spec | `docs/specs/spec-watch-history.md` | `docs/specs/spec-watch-history-opus.md` |
| Plan | `docs/plans/plan-watch-history.md` | `docs/plans/plan-watch-history-opus.md` |

> Both sets describe the identical feature with the same template at each tier. Differences below are content quality, not template differences. Same clarifying-question flow was followed for the Opus branch.

---

## Executive Summary

The two model outputs agree on the **core design** — fire-and-forget recording, upsert-on-replay, 500-entry cap, client-side badge via an IDs endpoint, `ON DELETE SET NULL` with an immutable `source_video_id`. That agreement is itself a signal the design is sound.

The Opus branch is **stronger on three things that compound across tiers**:

1. **Codebase grounding.** It caught two facts the baseline got wrong or missed:
   - **PK type:** baseline chose `UUID`; the actual entities `User.id` and `VideoSource.id` are both `BIGINT`/`IDENTITY`. Opus aligned to `BIGINT`.
   - **API base path:** baseline ADR assumed `/api/v1/...`; the verified constant is `AppConstant.API.BASE_URL = /v1/funny-app`. Opus flagged and corrected this.
2. **Concurrency rigor.** The baseline's "find-then-insert + delete-one-oldest" is non-atomic — its own ADR-0013 admits eviction "is not atomic with INSERT without a transaction." Opus made the ≤500 cap a hard invariant via native `ON CONFLICT` upsert + a single bulk-evict query in one transaction, and added explicit concurrency tests.
3. **Decidability.** Opus tiers carry primary-vs-secondary metric ranking, "leaning" recommendations on open questions, and an explicit IDOR (CWE-639) framing for ownership.

The baseline's advantage is **brevity** — it is ~15–20% shorter at each tier and easier for a non-technical stakeholder to skim. For an engineering audience the Opus depth is worth the length; for an exec one-pager the baseline reads faster.

**Overall:** Opus higher quality on correctness, grounding, and implementability; baseline marginally better on conciseness.

---

## Tier 1 — PRD

| Criterion | Baseline | Opus | Winner |
|-----------|----------|------|--------|
| Problem evidence | Asserted ("100% intent lost") | Same + names `ComingSoon.jsx`/`AppShell.jsx`, makes evidence instrumentable | Opus |
| Success metrics | 3 metrics, flat | Primary (re-watch ≥20%) vs secondary, with rationale | Opus |
| Functional reqs | 7 FR | 9 FR (cap/eviction and placeholder-replacement promoted to FRs) | Opus |
| NFR | 4 | 5 (adds atomic-eviction-under-concurrency) | Opus |
| User stories | 4, adequate | 5, acceptance covers empty state / bump-to-top / relative time | Opus |
| Readability | Tighter | Heavier | Baseline |

**Note:** PRD data-model sketch — baseline `UUID`, Opus `BIGINT` + index `(user_id, watched_at DESC)`.

---

## Tier 2 — ADR

| Criterion | Baseline (0013) | Opus (0014) | Winner |
|-----------|-----------------|-------------|--------|
| Decisions covered | 3 (trigger, cap, re-watch) | 4 (+ explicit ownership decision) | Opus |
| Trade-off matrices | Yes, good | Yes, plus convention-alignment reasoning | Opus |
| PK decision | UUID (unjustified vs codebase) | BIGINT, justified against `User`/`VideoSource` | Opus |
| Eviction atomicity | Admits non-atomic without tx | Transactional, resolves the gap | Opus |
| Security framing | "JWT-enforced" | IDOR / CWE-639 closed by construction | Opus |
| Base path | Assumed `/api/v1` (wrong) | Flagged as "verify, not assume" | Opus |
| Shared good insight | YouTube videos bypass server → frontend capture is the only uniform path | Same conclusion, independently reached | Tie |

Both ADRs correctly eliminate server-side capture because YouTube videos stream directly and would be missed — a non-obvious, high-value insight present in both.

---

## Tier 3 — Spec

| Criterion | Baseline | Opus | Winner |
|-----------|----------|------|--------|
| Base path | `/v1/funny-app` (correct) | `/v1/funny-app` + notes ADR-0013's wrong assumption | Opus |
| Response `id` type | `"uuid"` | `BIGINT` (matches corrected PK) | Opus |
| Error table | Lists 401/500 | Adds `429 RATE_LIMITED`, `400 INVALID_REQUEST` | Opus |
| Edge cases | 7 (EC-1..7) | 9 (+ EC-8 concurrent-at-cap, EC-9 same-video) | Opus |
| Ownership | Scoped by user_id | Explicit "no client `userId`", CWE-639 | Opus |
| Caching section | 2 caches | 3 caches | Opus (minor) |
| `VideoAccessStats` distinction | Slightly clearer | Present | Baseline (minor) |

Both resolved the same two clarifying questions the same way (rate limit 30/min; unknown videoId → 200 silent null). Self-consistency is high in both.

---

## Tier 4 — Plan

| Criterion | Baseline | Opus | Winner |
|-----------|----------|------|--------|
| PK / repo generic | `UUID` / `JpaRepository<…,UUID>` | `Long` / `…,Long>` | Opus |
| Upsert mechanism | Java find-then-insert (races EC-9) | Native `ON CONFLICT DO UPDATE` (atomic) | Opus |
| Eviction | Delete one oldest (non-atomic note) | Bulk `DELETE … NOT IN (… LIMIT 500)`, self-healing, same tx | Opus |
| Concurrency tests | Not explicit | Phase 7.3 dedicated EC-8/EC-9 tests | Opus |
| N+1 handling | Mentioned in Risks | `LEFT JOIN FETCH` in repo query | Opus |
| Service layering | Impl only | Interface + impl (project pattern) | Opus |
| Migration ordering | `202605310002` | `202606010001` (correct successor to latest `202605010001`) | Opus |
| Phase granularity | 11 phases, detailed | 11 phases, detailed | Tie |

---

## Cross-Cutting Observations

**Where the models agreed (design is robust):**
- Fire-and-forget POST on play as the only source-agnostic capture point (covers YouTube).
- Upsert on `(user_id, source_video_id)`; re-watch bumps to top.
- 500-entry cap with silent auto-eviction (vs hard reject) — correct for an automatic feature.
- Client-side `Set.has()` badge fed by one `/ids` fetch — zero per-card calls.
- `ON DELETE SET NULL` + immutable `source_video_id`; UI renders "Video unavailable".

**Where Opus added defensible value:**
- Fixed a real schema inconsistency (UUID → BIGINT) verified against `User.java`/`VideoSource.java`.
- Corrected a real, wrong API path assumption (verified against `AppConstant.java`).
- Closed a genuine concurrency hole (non-atomic eviction) end-to-end PRD→Plan, with tests.
- Reframed privacy as an IDOR class fix, not just "JWT auth".

**Shared residual risk (both, by user decision):** unknown `videoId` is recorded silently with `video_id = null` while `source_video_id NOT NULL` — bogus client ids can create junk rows; the only guard is the 30/min rate limit. Worth monitoring null-`video_id` growth post-launch.

---

## Scorecard

| Tier | Baseline | Opus |
|------|----------|------|
| PRD | ★★★★☆ | ★★★★★ |
| ADR | ★★★★☆ | ★★★★★ |
| Spec | ★★★★☆ | ★★★★★ |
| Plan | ★★★☆☆ | ★★★★★ |

**Verdict:** Opus 4.8 produces meaningfully higher-quality artifacts on this feature — the gap is widest at the Plan tier, where codebase grounding and concurrency correctness matter most. The baseline remains a solid, more concise alternative whose main weaknesses (UUID PK, wrong base path, non-atomic eviction) are all *correctness* issues an implementer would hit, not stylistic ones.

> Caveat: single feature, single run per model. Treat as directional evidence, not a benchmark. The Opus branch also benefited from being produced second (it could diverge deliberately where it found the baseline's gaps); a fully blind comparison would run both without sight of the other.

---

## Links

- PRD: [baseline](./prd/PRD-watch-history.md) · [opus](./prd/PRD-watch-history-opus.md)
- ADR: [0013 baseline](./adr/0013-watch-history-design.md) · [0014 opus](./adr/0014-watch-history-design-opus.md)
- Spec: [baseline](./specs/spec-watch-history.md) · [opus](./specs/spec-watch-history-opus.md)
- Plan: [baseline](./plans/plan-watch-history.md) · [opus](./plans/plan-watch-history-opus.md)
