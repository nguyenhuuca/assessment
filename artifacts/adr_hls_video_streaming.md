# ADR: Switch to HLS for Local Video Streaming

**Status:** Proposed  
**Date:** 2026-05-30  
**Author:** nguyenhuuca  

---

## Context

The current streaming implementation (`VideoStreamController`) serves locally-stored MP4 files (`.full` extension, downloaded from Google Drive) via HTTP Range requests (RFC 7233). Clients receive 256 KB on the first request and 512 KB on subsequent requests.

**Pain points with the current approach:**
- Range-based MP4 streaming is not CDN-cacheable (every byte range is a unique request)
- Requires custom seek math and a Guava byte[] chunk cache in application memory
- No standard adaptive bitrate (ABR) path — quality is fixed
- Player compatibility depends on the browser correctly implementing Range requests
- Seeking is at byte granularity but the application imposes its own chunk boundaries, causing mismatches

**YouTube videos are out of scope** — they are embedded via `embedLink` and served by YouTube's own infrastructure. HLS migration applies only to locally-stored videos (`VideoSource` entities with `sourceType = "google_drive"`).

---

## Decision

Switch local video streaming from HTTP Range + MP4 to **HLS (HTTP Live Streaming)** using FFmpeg to segment videos into `.ts` chunks and `.m3u8` playlists during the existing Google Drive sync pipeline.

- **Segmentation:** FFmpeg stream-copy (no re-encode), 6-second segments
- **Timing:** Offline — run during `VideoStorageServiceImpl.downloadFileFromFolder()`
- **Serving:** Direct `FileSystemResource` from disk (no application-level byte cache)
- **Frontend:** `hls.js` for Chrome/Firefox; native HLS for Safari
- **Rollout:** Feature-flagged, backward-compatible — old Range endpoint kept until all videos are transcoded

---

## Architecture

### Storage Layout

```
video-cache/
├── {fileId}.full              ← original MP4 (kept until HLS verified, then deleted)
└── {fileId}/
    ├── playlist.m3u8          ← media playlist
    ├── seg_000.ts
    ├── seg_001.ts
    └── ...
```

### New Backend Components

#### 1. `HlsTranscodeService` (interface + impl)

```java
// service/HlsTranscodeService.java
public interface HlsTranscodeService {
    void transcode(String fileId);             // async, updates VideoSource status
    HlsStatus getStatus(String fileId);
    Path getPlaylistPath(String fileId);
    Path getSegmentPath(String fileId, String segmentName);
}
```

**Implementation notes:**
- Runs `ProcessBuilder` with FFmpeg command below
- Wraps execution in a Virtual Thread (`Thread.ofVirtual().start(...)`)
- On start: set `VideoSource.hlsStatus = PROCESSING`
- On success: set `hlsStatus = READY`, populate `hlsPath`
- On failure: set `hlsStatus = ERROR`, log stderr

**FFmpeg command:**
```bash
ffmpeg -i video-cache/{fileId}.full \
  -c:v copy -c:a copy \
  -f hls \
  -hls_time 6 \
  -hls_list_size 0 \
  -hls_flags independent_segments \
  -hls_segment_filename "video-cache/{fileId}/seg_%03d.ts" \
  video-cache/{fileId}/playlist.m3u8
```

`-c copy` remuxes without re-encoding — near-instant processing, no quality loss.

#### 2. `HlsStreamController` (new controller)

```
GET /api/v1/hls/{fileId}/playlist.m3u8
  → 200 + Content-Type: application/vnd.apple.mpegurl  (if READY)
  → 202 Accepted + Retry-After: 5                       (if PROCESSING)
  → 404 Not Found                                        (if PENDING/ERROR/unknown)

GET /api/v1/hls/{fileId}/{segment}.ts
  → 200 + Content-Type: video/mp2t
  → Cache-Control: public, max-age=31536000 (segments are immutable)
  → Served via FileSystemResource (no Guava cache)

GET /api/v1/hls/{fileId}/status
  → 200 + { "status": "READY" | "PROCESSING" | "PENDING" | "ERROR" }
```

Segment files are served via Spring's `FileSystemResource` — the OS page cache handles hot-segment caching transparently, eliminating the application-level Guava byte[] cache for this flow.

#### 3. `VideoSource` entity changes

```java
@Enumerated(EnumType.STRING)
@Column(name = "hls_status")
private HlsStatus hlsStatus = HlsStatus.PENDING;

@Column(name = "hls_path")
private String hlsPath;  // relative path, e.g., "{fileId}/playlist.m3u8"

public enum HlsStatus { PENDING, PROCESSING, READY, ERROR }
```

#### 4. Liquibase migration

```sql
-- db/changelog/V{n}__add_hls_status_to_video_sources.sql
ALTER TABLE video_sources
  ADD COLUMN hls_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  ADD COLUMN hls_path   VARCHAR(500);
```

#### 5. `VideoDto` changes

```java
private boolean hlsReady;
private String  hlsUrl;    // "/api/v1/hls/{fileId}/playlist.m3u8" when ready
```

#### 6. `VideoStorageServiceImpl` changes

After a file is downloaded, fork transcoding as an async subtask inside the existing `StructuredTaskScope`:

```java
// After file download in downloadFileFromFolder()
scope.fork(() -> {
    hlsTranscodeService.transcode(fileId);
    return null;
});
```

#### 7. `AppScheduler` — retry job

```java
@Scheduled(cron = "0 0 2 * * *")     // 2 AM daily
void retryFailedHlsTranscoding() {
    // Find all VideoSource with hlsStatus IN (PENDING, ERROR)
    // Re-submit to hlsTranscodeService.transcode()
}
```

### Frontend Changes

**Dependency:**
```bash
npm i hls.js
```

**New component: `HlsVideoPlayer.jsx`**

```jsx
import Hls from 'hls.js';

export function HlsVideoPlayer({ video }) {
  const videoRef = useRef(null);

  useEffect(() => {
    if (!video.hlsReady) return;
    const src = video.hlsUrl;

    if (Hls.isSupported()) {
      const hls = new Hls();
      hls.loadSource(src);
      hls.attachMedia(videoRef.current);
      return () => hls.destroy();
    } else if (videoRef.current.canPlayType('application/vnd.apple.mpegurl')) {
      // Safari: native HLS
      videoRef.current.src = src;
    }
  }, [video.hlsReady, video.hlsUrl]);

  return <video ref={videoRef} controls />;
}
```

**Fallback logic in parent component:**

```jsx
// If hlsReady: use HlsVideoPlayer
// If !hlsReady && video.fileId: use legacy Range player (existing <video> with range URL)
// Poll /hls/{fileId}/status every 5s until READY
```

---

## Trade-off Matrix

| Criterion               | Range/MP4 (current)              | HLS (proposed)                         |
|-------------------------|----------------------------------|----------------------------------------|
| Browser compatibility   | Range requests widely supported  | hls.js universal; Safari native        |
| Seek performance        | Byte-precise but custom chunking | Segment-level (±3 s), standard         |
| CDN cacheability        | Poor — varied byte ranges        | Excellent — immutable segments         |
| Server memory           | Guava byte[] cache needed        | OS page cache sufficient               |
| Server CPU on request   | None                             | None (FFmpeg runs at ingest time)      |
| Ingest CPU overhead     | None                             | FFmpeg `-c copy` < 1s per GB           |
| Storage overhead        | 1×                               | ~1.01× (segments ≈ source size)        |
| ABR readiness           | Not possible                     | Add renditions in future               |
| DRM readiness           | Not built-in                     | AES-128 segment encryption available   |
| Code complexity         | Custom Range parser + cache      | Standard Spring Resource serving       |

---

## Migration Plan (Zero Downtime)

### Phase 1 — Backend (no visible frontend change)
1. Liquibase migration: add `hls_status`, `hls_path` to `video_sources`
2. Implement `HlsTranscodeService` + wire into download pipeline
3. Implement `HlsStreamController`
4. Add nightly retry job
5. Update `VideoDto` with `hlsReady`, `hlsUrl`
6. One-shot migration endpoint (admin-only) or scheduled job to transcode existing `.full` files

### Phase 2 — Frontend
1. Add `hls.js`
2. Implement `HlsVideoPlayer` component
3. Switch video player to HLS when `hlsReady = true`
4. Poll status endpoint for in-progress transcoding

### Phase 3 — Cleanup
1. After all videos report `hlsReady = true`, delete `.full` files via `deleteIfEligible()`
2. Remove Guava `VideoCacheImpl` from request path (keep for backward compat period)
3. Deprecate `VideoStreamController` (sunset in a future release)

### Feature Flag

```yaml
# application.yaml
hls:
  enabled: true
  segment-duration: 6       # seconds per .ts segment
  output-dir: video-cache   # base directory
```

---

## Consequences

**Positive:**
- Standard HLS playback across all browsers (no custom player code)
- Eliminates application-level byte[] chunk cache — reduces heap pressure
- Segments are immutable → trivially CDN-cacheable in future
- Foundation for ABR and AES-128 DRM

**Negative / Accepted:**
- Additional FFmpeg invocation per video at ingest time (mitigated: stream-copy is fast)
- Slightly more complex storage layout (directory per video vs single file)
- Frontend gains `hls.js` dependency (~300 KB gzipped, lazy-loaded)

**Neutral:**
- `StatsCache` and `VideoAccessService` remain useful for analytics regardless of streaming protocol
- YouTube videos unaffected

---

## Alternatives Rejected

| Alternative | Reason Rejected |
|---|---|
| On-demand segmentation (first request) | Unacceptable first-play latency |
| Multi-bitrate ABR ladder (v1) | Extra FFmpeg passes add ingest time; can be added as Phase 2 |
| MPEG-DASH instead of HLS | HLS has broader native support; hls.js is simpler than dash.js |
| Keep Range/MP4 with chunked transfer | Does not address CDN cacheability or cache memory pressure |

---

## References

- [RFC 8216 — HTTP Live Streaming](https://datatracker.ietf.org/doc/html/rfc8216)
- [hls.js documentation](https://github.com/video-dev/hls.js)
- [FFmpeg HLS muxer](https://ffmpeg.org/ffmpeg-formats.html#hls-1)
- Current implementation: `web/VideoStreamController.java`, `service/impl/StreamVideoServiceImpl.java`
- Related ADR: `doc/adr/` (existing architecture decisions)
