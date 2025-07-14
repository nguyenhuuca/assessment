# Use LRU Cache for Video Streaming

**Status:** Accepted  
**Date:** 2025-07-14  
**Decision Makers:** [canh]  
**Context:** [Cache Layer in Video Streaming System]

## Context

The video streaming service handles large files by serving them in small chunks. To reduce disk and network latency and improve performance, we implement an in-memory caching layer. This layer needs to:

- Evict unused items efficiently under memory pressure  
- Prioritize chunks that are likely to be re-accessed soon  
- Support concurrent access safely

Currently, we must choose an eviction policy for the cache. The two most common strategies are:

- **Least Recently Used (LRU):** Evicts the least recently accessed item.
- **Least Frequently Used (LFU):** Evicts the item accessed least frequently over time.

## Decision

We decided to **use an LRU (Least Recently Used) cache** as the eviction strategy for video chunk caching.

### Rationale:

- LRU is simple to implement and widely supported by caching libraries (e.g., Guava, Caffeine).
- LRU aligns well with temporal locality patterns seen in streaming, where recently accessed chunks are more likely to be accessed again soon.
- LRU requires less memory and computational overhead compared to LFU.
- Benchmarking shows that LRU performs adequately under current access patterns.

### Trade-offs:

- LRU may evict "hot" chunks that are accessed frequently but not recently (unlike LFU).
- LFU may perform better in long-lived streams or workloads with heavy re-access of older content.

## Consequences

- The cache will automatically evict least recently accessed chunks when it exceeds its memory limit.
- Performance will likely remain stable under typical streaming usage.
- We gain fast implementation and low operational complexity.

## Alternatives Considered

| Option       | Pros                                   | Cons                                         |
|--------------|----------------------------------------|----------------------------------------------|
| LRU (chosen) | Simple, low overhead, temporal locality | Not frequency-aware                          |
| LFU          | Captures frequency-based locality       | More memory and complexity                   |
| FIFO         | Easy to implement                       | Poor cache hit rate for streaming workloads  |
| Hybrid       | Potentially optimal hit ratio           | Requires tuning and added complexity         |

## Future Considerations

We will monitor the system under production workloads. If we detect cache churn or degradation in hit rate due to frequent eviction of hot chunks, we may:

- Switch to **LFU** (e.g., Caffeine's LFU mode)
- Implement a hybrid strategy such as **W-TinyLFU**
- Analyze chunk access telemetry to guide caching strategy


