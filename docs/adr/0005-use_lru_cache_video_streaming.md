# ADR-0005: Use LRU Cache for Video Streaming

## Metadata

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2025-07-14 |
| **Deciders** | nguyenhuuca |
| **Related PRD** | N/A |
| **Domain Tags** | data, infrastructure |
| **Supersedes** | N/A |
| **Superseded By** | N/A |

**Tech Strategy Alignment:**
- [x] Decision follows Golden Path in `.claude/rules/tech-strategy.md`

---

## Context

The video streaming service handles large files by serving them in small chunks. An in-memory caching layer is required to reduce disk and network latency. The cache must evict unused items efficiently under memory pressure, prioritize chunks likely to be re-accessed soon, and support concurrent access safely.

The primary design decision is which eviction policy to apply. The two most common strategies considered are LRU (Least Recently Used) and LFU (Least Frequently Used).

---

## Decision Drivers

- Eviction policy must suit temporal locality patterns typical in video streaming
- Implementation complexity must remain low for the current team size
- Library support must exist in the existing Java ecosystem (Guava)
- Memory and computational overhead must be minimal

---

## Considered Options

### Option 1: LRU (Least Recently Used)
Evicts the item that was accessed least recently.

| Pros | Cons |
|------|------|
| Simple to implement; supported by Guava out of the box | Not frequency-aware; may evict chunks that are hot but not recent |
| Aligns with temporal locality patterns in streaming | Suboptimal for long-lived streams with repeated access to older content |
| Low memory and computational overhead | |

### Option 2: LFU (Least Frequently Used)
Evicts the item accessed least frequently over its lifetime.

| Pros | Cons |
|------|------|
| Captures frequency-based locality; better for repeated access to the same content | Higher memory and implementation complexity |
| Better cache hit rate under stable, long-lived access patterns | Requires frequency counters; more tuning needed |

### Option 3: FIFO (First In, First Out)
Evicts the oldest item regardless of access pattern.

| Pros | Cons |
|------|------|
| Trivial to implement | Poor cache hit rate for streaming workloads; ignores access recency and frequency |

### Option 4: Hybrid (e.g., W-TinyLFU)
Combines frequency and recency signals for near-optimal eviction.

| Pros | Cons |
|------|------|
| Potentially optimal hit ratio across diverse workloads | Requires significant tuning and added implementation complexity |

---

## Decision Outcome
**Chosen Option:** Option 1 — LRU (Least Recently Used)
**Rationale:** LRU is directly supported by Guava, requires no additional tuning, and aligns well with the temporal locality patterns observed in video streaming where recently accessed chunks are most likely to be re-requested. LFU offers frequency-based advantages but adds complexity that is not justified at the current scale. FIFO and Hybrid options were ruled out for poor hit rate and high tuning cost respectively.

### Quantified Impact *(where applicable)*
N/A — no concrete before/after metrics available at decision time.

---

## Consequences
**Positive:**
- Cache automatically evicts least recently accessed chunks when the memory limit is reached
- Performance remains stable under typical streaming usage patterns
- Fast implementation path using Guava with low operational complexity

**Negative:**
- Frequently accessed but not recently accessed chunks may be evicted, reducing hit rate under certain access patterns

**Risks:**
- If production workloads exhibit cache churn or degraded hit rates due to frequent eviction of hot chunks, a switch to LFU (Caffeine) or a hybrid strategy such as W-TinyLFU may be required; this should be driven by chunk access telemetry

---

## Validation
- [x] Tech Strategy alignment confirmed
- [ ] Related plan document created: N/A

---

## Links
- Guava Cache documentation: https://github.com/google/guava/wiki/CachesExplained
- Caffeine (LFU alternative): https://github.com/ben-manes/caffeine

---

## Changelog
| Date | Author | Change |
|------|--------|--------|
| 2025-07-14 | nguyenhuuca | Initial draft |
| 2026-05-31 | nguyenhuuca | Restructured to new ADR template |
