# ADR-0006: Unified Video List API for UI Display

**Status:** Proposed
**Date:** 2026-02-06
**Architect:** Principal Architect (Claude Sonnet 4.5)
**Stakeholders:** Frontend Team, Backend Team, Product

---

## Context

The **Funny Movies** application currently has fragmented video listing endpoints:
- `/api/top-videos` - YouTube videos suggested by ChatGPT
- `/api/stream/list` - Streamable videos from storage
- `/api/share-links` - User-shared videos
- `/api/private-videos` - Private user videos

**Current Limitations:**
1. ❌ **No pagination** - Will fail with >100 videos in memory
2. ❌ **No sorting** - Can't order by popularity, date, or relevance
3. ❌ **No filtering** - Can't filter by source, user, or category
4. ❌ **No search** - Can't search video titles or descriptions
5. ❌ **Fragmented UX** - Frontend must call multiple endpoints and merge results
6. ❌ **Poor performance** - No caching strategy for list queries
7. ❌ **Not scalable** - Service-side sorting breaks at scale

**UI Requirements:**
- Display unified video feed with thumbnails and titles
- Support infinite scroll or pagination
- Filter by video source (YouTube, Shared, Private)
- Search by title/description
- Sort by date, popularity, or relevance
- Show view counts and likes (optional)
- Responsive on mobile and desktop

---

## Decision

We will implement a **unified, paginated, filterable video list API** that consolidates all video sources into a single endpoint with comprehensive query capabilities.

### API Specification

#### Endpoint
```
GET /v1/funny-app/videos
```

#### Query Parameters

| Parameter | Type | Default | Required | Description |
|-----------|------|---------|----------|-------------|
| `page` | int | 0 | No | Page number (0-indexed) |
| `size` | int | 20 | No | Page size (max: 100) |
| `sort` | string | "createdAt,desc" | No | Sort field and direction |
| `source` | enum | "all" | No | Filter by source: `youtube`, `shared`, `private`, `all` |
| `userId` | long | null | No | Filter by user who shared (for shared/private videos) |
| `query` | string | null | No | Search in title and description (full-text) |
| `includeStats` | boolean | false | No | Include view counts and likes (lazy loaded) |

**Valid Sort Values:**
- `createdAt,asc` | `createdAt,desc` (default)
- `title,asc` | `title,desc`
- `views,desc` (requires `includeStats=true`)
- `likes,desc` (requires `includeStats=true`)

#### Request Examples

**Example 1: Get first page of all videos**
```http
GET /v1/funny-app/videos?page=0&size=20
```

**Example 2: Search for cat videos**
```http
GET /v1/funny-app/videos?query=cat&page=0&size=10
```

**Example 3: Get YouTube videos sorted by popularity**
```http
GET /v1/funny-app/videos?source=youtube&sort=views,desc&includeStats=true
```

**Example 4: Get current user's private videos**
```http
GET /v1/funny-app/videos?source=private&userId={currentUserId}
```

#### Response Structure

**Success Response:** `PaginatedResultListInfo<VideoListItemDto>`

```json
{
  "status": "SUCCESS",
  "data": [
    {
      "id": 123,
      "videoId": "dQw4w9WgXcQ",
      "title": "Amazing Cat Compilation 2026",
      "description": "The funniest cat videos of the year",
      "thumbnailUrl": "https://image.canh-labs.com/thumbnails/dQw4w9WgXcQ.jpg",
      "duration": 360,
      "source": "youtube",
      "userShared": "john@example.com",
      "embedLink": "https://www.youtube.com/embed/dQw4w9WgXcQ",
      "urlLink": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      "createdAt": "2026-02-06T10:30:00Z",
      "stats": {
        "views": 1250,
        "likes": 89,
        "dislikes": 3
      }
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 157,
    "totalPages": 8,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

**Error Response:** `ResultErrorInfo`
```json
{
  "status": "ERROR",
  "message": "Invalid page size. Maximum allowed is 100.",
  "code": "VALIDATION_ERROR"
}
```

---

## Data Transfer Objects (DTOs)

### VideoListItemDto
```java
package com.canhlabs.funnyapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoListItemDto {
    /**
     * Internal database ID
     */
    private Long id;

    /**
     * YouTube video ID or internal video identifier
     */
    private String videoId;

    /**
     * Video title (indexed for search)
     */
    private String title;

    /**
     * Video description (indexed for search)
     */
    private String description;

    /**
     * Thumbnail image URL
     */
    private String thumbnailUrl;

    /**
     * Video duration in seconds
     */
    private Integer duration;

    /**
     * Video source: youtube, shared, private
     */
    private VideoSource source;

    /**
     * Email of user who shared (null for YouTube videos)
     */
    private String userShared;

    /**
     * Embeddable link for iframe
     */
    private String embedLink;

    /**
     * Direct URL to video
     */
    private String urlLink;

    /**
     * Timestamp when video was added to system
     */
    private Instant createdAt;

    /**
     * Optional stats (only populated if includeStats=true)
     */
    private VideoStatsDto stats;
}
```

### VideoStatsDto
```java
package com.canhlabs.funnyapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoStatsDto {
    /**
     * Total view count
     */
    private Long views;

    /**
     * Total like count
     */
    private Long likes;

    /**
     * Total dislike count (may be 0 for YouTube)
     */
    private Long dislikes;
}
```

### PaginatedResultListInfo<T>
```java
package com.canhlabs.funnyapp.dto.webapi;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class PaginatedResultListInfo<T> extends ResultInfo {
    /**
     * List of data items for current page
     */
    private List<T> data;

    /**
     * Pagination metadata
     */
    private PaginationInfo pagination;
}
```

### PaginationInfo
```java
package com.canhlabs.funnyapp.dto.webapi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationInfo {
    /**
     * Current page number (0-indexed)
     */
    private int page;

    /**
     * Page size
     */
    private int size;

    /**
     * Total number of elements across all pages
     */
    private long totalElements;

    /**
     * Total number of pages
     */
    private int totalPages;

    /**
     * Whether there is a next page
     */
    private boolean hasNext;

    /**
     * Whether there is a previous page
     */
    private boolean hasPrevious;
}
```

### VideoSource Enum
```java
package com.canhlabs.funnyapp.enums;

public enum VideoSource {
    YOUTUBE("youtube"),
    SHARED("shared"),
    PRIVATE("private"),
    ALL("all");

    private final String value;

    VideoSource(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static VideoSource fromString(String value) {
        for (VideoSource source : VideoSource.values()) {
            if (source.value.equalsIgnoreCase(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Invalid video source: " + value);
    }
}
```

---

## Architecture Design

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                             │
│  (React/Vue) - Infinite scroll, filters, search             │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP GET /videos?...
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              VideoListController                             │
│  - Validate query parameters                                 │
│  - Rate limiting (@RateLimited)                              │
│  - OpenTelemetry tracing (@WithSpan)                         │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              VideoListService                                │
│  - Aggregate from multiple sources                           │
│  - Apply filters and sorting                                 │
│  - Paginate results                                          │
│  - Load stats conditionally                                  │
│  - Cache query results (Guava/Redis)                         │
└──────────────────────┬──────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ YouTubeVideo │ │  ShareLink   │ │VideoAccess   │
│   Repo       │ │    Repo      │ │  Stats Repo  │
└──────────────┘ └──────────────┘ └──────────────┘
        │              │              │
        └──────────────┼──────────────┘
                       ▼
              ┌─────────────────┐
              │   PostgreSQL    │
              │   Database      │
              └─────────────────┘
```

### Sequence Diagram

```
User → UI: Request videos page 1
UI → Controller: GET /videos?page=0&size=20&source=all
Controller → Service: getVideos(page=0, size=20, source=all)
Service → Cache: get("videos:all:0:20")
Cache → Service: MISS

Service → YouTubeVideoRepo: findAll(pageable)
YouTubeVideoRepo → Service: List<YouTubeVideo>

Service → ShareLinkRepo: findAll(pageable)
ShareLinkRepo → Service: List<ShareLink>

Service → Service: mergeAndSort(youtubeVideos, sharedVideos)
Service → Service: paginate(merged, page=0, size=20)

Service → Cache: put("videos:all:0:20", result, ttl=5min)
Service → Controller: PaginatedResultListInfo<VideoListItemDto>
Controller → UI: JSON response with pagination
UI → User: Display video grid
```

---

## Implementation Strategy

### Phase 1: Core Functionality (Sprint 1, 1 week)

**Deliverables:**
1. Create new DTOs: `VideoListItemDto`, `VideoStatsDto`, `PaginatedResultListInfo`, `PaginationInfo`
2. Create `VideoListService` interface and implementation
3. Implement basic aggregation from `YoutubeVideoRepo` and `ShareLinkRepo`
4. Add `VideoListController` with `/videos` endpoint
5. Support pagination with `Pageable` (Spring Data)
6. Support basic filtering by `source`
7. Unit tests for service layer (>80% coverage)

**Repository Methods:**
```java
// YoutubeVideoRepo
Page<YouTubeVideo> findBySource(String source, Pageable pageable);
Page<YouTubeVideo> findAll(Pageable pageable);

// ShareLinkRepo
Page<ShareLink> findByUserId(Long userId, Pageable pageable);
Page<ShareLink> findAll(Pageable pageable);
```

**Service Implementation Pattern:**
```java
@Service
public class VideoListServiceImpl implements VideoListService {

    private final YoutubeVideoRepo youtubeRepo;
    private final ShareLinkRepo shareLinkRepo;
    private final VideoAccessStatsRepo statsRepo;
    private final AppCache cache;

    @Override
    public PaginatedResultListInfo<VideoListItemDto> getVideos(
        int page, int size, String sort, VideoSource source,
        Long userId, String query, boolean includeStats
    ) {
        // 1. Build cache key
        String cacheKey = buildCacheKey(page, size, sort, source, userId, query);

        // 2. Check cache
        PaginatedResultListInfo<VideoListItemDto> cached = cache.get(cacheKey);
        if (cached != null) return cached;

        // 3. Query database based on source
        List<VideoListItemDto> videos = fetchFromSources(source, userId, page, size, sort);

        // 4. Apply search filter if query present
        if (query != null) {
            videos = filterByQuery(videos, query);
        }

        // 5. Load stats if requested
        if (includeStats) {
            enrichWithStats(videos);
        }

        // 6. Build pagination metadata
        PaginationInfo pagination = buildPaginationInfo(page, size, videos.size());

        // 7. Build result
        PaginatedResultListInfo<VideoListItemDto> result = PaginatedResultListInfo.<VideoListItemDto>builder()
            .status(ResultStatus.SUCCESS)
            .data(videos)
            .pagination(pagination)
            .build();

        // 8. Cache result (TTL: 5 minutes)
        cache.put(cacheKey, result, Duration.ofMinutes(5));

        return result;
    }
}
```

---

### Phase 2: Advanced Features (Sprint 2, 1 week)

**Deliverables:**
1. Implement full-text search on title and description (PostgreSQL `to_tsvector`)
2. Add sorting by views and likes (requires stats join)
3. Add user filtering for private videos
4. Implement cache invalidation on video create/update/delete
5. Add rate limiting to prevent abuse (`@RateLimited(permit = 30)`)
6. Integration tests with TestContainers
7. Performance tests with JMeter (target: <500ms p95 latency)

**Database Optimization:**
```sql
-- Add full-text search index
CREATE INDEX idx_youtube_video_search
ON youtube_video
USING GIN(to_tsvector('english', title || ' ' || COALESCE(description, '')));

CREATE INDEX idx_share_link_search
ON share_links
USING GIN(to_tsvector('english', title || ' ' || COALESCE(description, '')));

-- Add indexes for common queries
CREATE INDEX idx_youtube_video_source_created
ON youtube_video(source, created_at DESC);

CREATE INDEX idx_share_link_user_created
ON share_links(user_id, created_at DESC);
```

**Full-Text Search Query:**
```java
@Query("SELECT v FROM YouTubeVideo v WHERE to_tsvector('english', v.title || ' ' || COALESCE(v.desc, '')) @@ to_tsquery('english', :query)")
Page<YouTubeVideo> searchVideos(@Param("query") String query, Pageable pageable);
```

---

### Phase 3: Scale Optimization (Sprint 3+, 1 week)

**Deliverables:**
1. Migrate cache to Redis (per ADR-0003)
2. Implement cursor-based pagination for infinite scroll
3. Add Elasticsearch for advanced search (if >100k videos)
4. Optimize with UNION queries for aggregation
5. Add CDN caching for thumbnails
6. Load testing and capacity planning

**Redis Cache Implementation:**
```java
@Service
public class RedisVideoListCache implements VideoListCache {

    private final RedisTemplate<String, PaginatedResultListInfo<VideoListItemDto>> redisTemplate;

    @Override
    public void put(String key, PaginatedResultListInfo<VideoListItemDto> value) {
        redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(5));
    }

    @Override
    public PaginatedResultListInfo<VideoListItemDto> get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void invalidate(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
```

---

## Backward Compatibility

Existing endpoints will be **maintained as thin wrappers** to avoid breaking changes:

```java
@RestController
@RequestMapping(AppConstant.API.BASE_URL)
public class YoutubeController {

    private final VideoListService videoListService;

    /**
     * @deprecated Use /videos?source=youtube instead
     */
    @Deprecated
    @GetMapping("/top-videos")
    public ResponseEntity<ResultListInfo<VideoDto>> getTopVideos() {
        // Delegate to new unified endpoint
        PaginatedResultListInfo<VideoListItemDto> result =
            videoListService.getVideos(0, 100, "createdAt,desc", VideoSource.YOUTUBE, null, null, false);

        // Convert to old DTO format
        List<VideoDto> legacyFormat = result.getData().stream()
            .map(this::convertToLegacyDto)
            .toList();

        return ResponseEntity.ok(ResultListInfo.<VideoDto>builder()
            .status(ResultStatus.SUCCESS)
            .data(legacyFormat)
            .build());
    }
}
```

**Migration Timeline:**
- **Sprint 1-2:** New endpoint available, old endpoints remain
- **Sprint 3:** Add deprecation warnings to old endpoints
- **Sprint 6:** Remove deprecated endpoints (with notice)

---

## Testing Strategy

### Unit Tests (>80% Coverage Required)

```java
@Test
void testGetVideos_WithPagination() {
    // Given
    Pageable pageable = PageRequest.of(0, 20);
    List<YouTubeVideo> mockVideos = createMockVideos(20);
    when(youtubeRepo.findAll(pageable)).thenReturn(new PageImpl<>(mockVideos));

    // When
    PaginatedResultListInfo<VideoListItemDto> result =
        videoListService.getVideos(0, 20, "createdAt,desc", VideoSource.YOUTUBE, null, null, false);

    // Then
    assertThat(result.getData()).hasSize(20);
    assertThat(result.getPagination().getPage()).isEqualTo(0);
    assertThat(result.getPagination().getTotalPages()).isGreaterThan(0);
}

@Test
void testGetVideos_WithSearch() {
    // Given
    String query = "cat funny";
    List<YouTubeVideo> mockVideos = createMockVideosWithTitle("Funny Cat Compilation");
    when(youtubeRepo.searchVideos(query, pageable)).thenReturn(new PageImpl<>(mockVideos));

    // When
    PaginatedResultListInfo<VideoListItemDto> result =
        videoListService.getVideos(0, 20, "createdAt,desc", VideoSource.ALL, null, query, false);

    // Then
    assertThat(result.getData()).allMatch(v -> v.getTitle().toLowerCase().contains("cat"));
}

@Test
void testGetVideos_CacheHit() {
    // Given
    String cacheKey = "videos:youtube:0:20:createdAt,desc";
    PaginatedResultListInfo<VideoListItemDto> cachedResult = createMockResult();
    when(cache.get(cacheKey)).thenReturn(cachedResult);

    // When
    PaginatedResultListInfo<VideoListItemDto> result =
        videoListService.getVideos(0, 20, "createdAt,desc", VideoSource.YOUTUBE, null, null, false);

    // Then
    verify(youtubeRepo, never()).findAll(any(Pageable.class));
    assertThat(result).isEqualTo(cachedResult);
}
```

### Integration Tests (TestContainers)

```java
@SpringBootTest
@Testcontainers
class VideoListIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Autowired
    private VideoListService videoListService;

    @Autowired
    private YoutubeVideoRepo youtubeRepo;

    @Test
    void testGetVideos_WithRealDatabase() {
        // Given
        youtubeRepo.saveAll(createTestVideos(100));

        // When
        PaginatedResultListInfo<VideoListItemDto> page1 =
            videoListService.getVideos(0, 20, "createdAt,desc", VideoSource.ALL, null, null, false);

        PaginatedResultListInfo<VideoListItemDto> page2 =
            videoListService.getVideos(1, 20, "createdAt,desc", VideoSource.ALL, null, null, false);

        // Then
        assertThat(page1.getData()).hasSize(20);
        assertThat(page2.getData()).hasSize(20);
        assertThat(page1.getData()).doesNotContainAnyElementsOf(page2.getData());
    }
}
```

### Performance Tests (JMeter)

**Target SLAs:**
- p50 latency: <200ms
- p95 latency: <500ms
- p99 latency: <1000ms
- Throughput: >100 req/sec (single pod)

**Test Scenarios:**
1. **Cold cache:** First request (cache miss)
2. **Warm cache:** Subsequent requests (cache hit)
3. **Deep pagination:** Page 100 with offset 2000
4. **Large dataset:** 10k videos in database
5. **Concurrent users:** 50 simultaneous requests

---

## Monitoring & Observability

### Metrics to Track (Prometheus)

```java
@Component
public class VideoListMetrics {

    private final Counter videoListRequests = Counter.builder("video_list_requests_total")
        .tag("source", "all")
        .description("Total video list requests")
        .register(Metrics.globalRegistry);

    private final Timer videoListLatency = Timer.builder("video_list_latency")
        .description("Video list query latency")
        .register(Metrics.globalRegistry);

    private final Counter cacheHits = Counter.builder("video_list_cache_hits_total")
        .description("Cache hits for video list")
        .register(Metrics.globalRegistry);

    private final Counter cacheMisses = Counter.builder("video_list_cache_misses_total")
        .description("Cache misses for video list")
        .register(Metrics.globalRegistry);
}
```

### Alerts (Grafana)

1. **High Latency:** p95 latency >500ms for 5 minutes
2. **Low Cache Hit Rate:** <70% cache hits over 10 minutes
3. **High Error Rate:** >1% error rate for 5 minutes
4. **Database Connection Pool:** >80% pool utilization

### Logging (Structured)

```java
@WithSpan
@AuditLog("Video list query")
public PaginatedResultListInfo<VideoListItemDto> getVideos(...) {
    log.info("Video list query: page={}, size={}, source={}, query={}, cacheHit={}",
        page, size, source, query, cacheHit);

    // ... implementation

    log.info("Video list response: items={}, totalElements={}, duration={}ms",
        result.getData().size(), result.getPagination().getTotalElements(), duration);
}
```

---

## Security Considerations

### Authentication & Authorization

```java
@RestController
@RequestMapping(AppConstant.API.BASE_URL)
public class VideoListController {

    @GetMapping("/videos")
    @WithSpan
    @RateLimited(permit = 30) // 30 requests per minute
    public ResponseEntity<PaginatedResultListInfo<VideoListItemDto>> getVideos(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        @RequestParam(defaultValue = "all") String source,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "false") boolean includeStats,
        Authentication authentication
    ) {
        // Validate page size
        Contract.require(size <= 100, "Page size must not exceed 100");

        // Private videos require authentication
        if ("private".equals(source)) {
            Contract.require(authentication != null && authentication.isAuthenticated(),
                "Authentication required for private videos");

            // Ensure user can only see their own private videos
            UserDetails user = (UserDetails) authentication.getPrincipal();
            userId = userService.findByEmail(user.getUsername()).getId();
        }

        // Call service
        PaginatedResultListInfo<VideoListItemDto> result =
            videoListService.getVideos(page, size, sort, VideoSource.fromString(source), userId, query, includeStats);

        return ResponseEntity.ok(result);
    }
}
```

### Security Checks

1. ✅ **Rate Limiting:** 30 requests per minute per user
2. ✅ **Input Validation:** Max page size, valid sort fields, valid source enum
3. ✅ **Authorization:** Private videos only visible to owner
4. ✅ **SQL Injection:** Parameterized queries (JPA protects)
5. ✅ **XSS Prevention:** Output encoding (Spring Boot default)
6. ✅ **CORS:** Configured for frontend domain only

---

## Consequences

### Positive

1. ✅ **Unified UX:** Single endpoint for all video sources reduces frontend complexity
2. ✅ **Scalable:** Pagination prevents memory exhaustion with large datasets
3. ✅ **Performant:** Caching reduces database load by ~80-90%
4. ✅ **Flexible:** Query parameters support diverse UI requirements
5. ✅ **Searchable:** Full-text search enables discoverability
6. ✅ **Observable:** Metrics and tracing enable performance monitoring
7. ✅ **Maintainable:** DRY principle reduces code duplication
8. ✅ **Extensible:** Easy to add new video sources or filters

### Negative

1. ⚠️ **Complexity:** Service-level aggregation more complex than direct DB queries
2. ⚠️ **Migration Effort:** Frontend must update to new API (mitigated by wrappers)
3. ⚠️ **Cache Invalidation:** Requires careful coordination on write operations
4. ⚠️ **Memory Usage:** Service-level sorting can consume memory (mitigated by page size limit)

### Neutral

1. 🔵 **Backward Compatibility:** Old endpoints maintained but deprecated
2. 🔵 **Learning Curve:** Team must learn new pagination patterns
3. 🔵 **Testing Overhead:** More test cases required for combinations

---

## Trade-offs Summary

| Decision | Alternative | Rationale |
|----------|-------------|-----------|
| **Offset Pagination** | Cursor-based | Simpler implementation, supports page numbers. Migrate to cursor if scaling issues arise. |
| **Service-Level Aggregation** | UNION query | More flexible, type-safe. Database query optimization deferred until needed. |
| **Guava Cache** | Redis | Faster development, aligns with current architecture. Redis migration planned (ADR-0003). |
| **Full-Text Search (PostgreSQL)** | Elasticsearch | PostgreSQL sufficient for <100k videos. Elasticsearch overkill for current scale. |
| **Lazy Stats Loading** | Eager loading | Improves performance for most queries. Stats are optional and expensive to compute. |

---

## Rollout Plan

### Week 1: Development
- Day 1-2: Create DTOs and enums
- Day 3-4: Implement `VideoListService` with basic aggregation
- Day 5: Add `VideoListController` and tests

### Week 2: Testing & Refinement
- Day 1-2: Integration tests and performance benchmarks
- Day 3: Add full-text search
- Day 4: Implement caching with invalidation
- Day 5: Code review and documentation

### Week 3: Deployment
- Day 1: Deploy to staging environment
- Day 2-3: Frontend integration testing
- Day 4: Performance testing under load
- Day 5: Production deployment (feature flag enabled)

### Week 4+: Monitoring & Optimization
- Monitor metrics and alerts
- Gather user feedback
- Optimize slow queries
- Plan Redis migration

---

## Success Metrics

**KPIs to Track:**
1. **API Latency:** p95 <500ms (target: <300ms)
2. **Cache Hit Rate:** >80% (target: >90%)
3. **Error Rate:** <0.5% (target: <0.1%)
4. **User Engagement:** Video view rate increases by 20%
5. **Frontend Performance:** Time to first video render <1s

**Acceptance Criteria:**
- ✅ All unit tests pass (>80% coverage)
- ✅ Integration tests pass with real database
- ✅ Performance tests meet SLAs
- ✅ Security review approved
- ✅ Documentation complete (API docs, ADR, runbook)
- ✅ Frontend successfully integrated

---

## Related ADRs

- **ADR-0001:** Use PostgreSQL relational database
- **ADR-0002:** Layered Spring Boot architecture with Virtual Threads
- **ADR-0003:** Guava Cache (future Redis migration)
- **ADR-0005:** LRU cache for video streaming

---

## References

- Spring Data Pagination: https://spring.io/guides/gs/accessing-data-jpa/
- PostgreSQL Full-Text Search: https://www.postgresql.org/docs/current/textsearch.html
- RESTful API Design: https://restfulapi.net/
- Cursor vs Offset Pagination: https://slack.engineering/evolving-api-pagination-at-slack/

---

## Appendix: API Contract Examples

### Example 1: Homepage Video Feed

**Request:**
```http
GET /v1/funny-app/videos?page=0&size=20&sort=createdAt,desc&source=all
Authorization: Bearer {jwt-token}
```

**Response:**
```json
{
  "status": "SUCCESS",
  "data": [
    {
      "id": 101,
      "videoId": "abc123",
      "title": "Funny Dogs Compilation 2026",
      "description": "The best dog videos",
      "thumbnailUrl": "https://image.canh-labs.com/thumbnails/abc123.jpg",
      "duration": 420,
      "source": "youtube",
      "userShared": null,
      "embedLink": "https://www.youtube.com/embed/abc123",
      "urlLink": "https://www.youtube.com/watch?v=abc123",
      "createdAt": "2026-02-06T12:00:00Z",
      "stats": null
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 157,
    "totalPages": 8,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

### Example 2: Search with Stats

**Request:**
```http
GET /v1/funny-app/videos?query=cat&includeStats=true&sort=views,desc
Authorization: Bearer {jwt-token}
```

**Response:**
```json
{
  "status": "SUCCESS",
  "data": [
    {
      "id": 102,
      "videoId": "xyz789",
      "title": "Ultimate Cat Fails",
      "description": "Hilarious cat compilation",
      "thumbnailUrl": "https://image.canh-labs.com/thumbnails/xyz789.jpg",
      "duration": 360,
      "source": "youtube",
      "userShared": null,
      "embedLink": "https://www.youtube.com/embed/xyz789",
      "urlLink": "https://www.youtube.com/watch?v=xyz789",
      "createdAt": "2026-02-05T10:30:00Z",
      "stats": {
        "views": 5000,
        "likes": 250,
        "dislikes": 10
      }
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 15,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

### Example 3: User's Private Videos

**Request:**
```http
GET /v1/funny-app/videos?source=private&page=0&size=10
Authorization: Bearer {jwt-token}
```

**Response:**
```json
{
  "status": "SUCCESS",
  "data": [
    {
      "id": 201,
      "videoId": "private-123",
      "title": "My Birthday Party",
      "description": "Family celebration",
      "thumbnailUrl": "https://image.canh-labs.com/thumbnails/private-123.jpg",
      "duration": 1200,
      "source": "private",
      "userShared": "john@example.com",
      "embedLink": null,
      "urlLink": "https://funnyapp.canh-labs.com/stream/private-123",
      "createdAt": "2026-01-15T18:00:00Z",
      "stats": {
        "views": 12,
        "likes": 5,
        "dislikes": 0
      }
    }
  ],
  "pagination": {
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

---

**End of ADR-0006**
