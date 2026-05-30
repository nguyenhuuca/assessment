# Implementation Plan: HLS Video Streaming Migration

**Status:** Ready for execution  
**Estimated effort:** 7–10 days  
**Dependencies:** ADR `artifacts/adr_hls_video_streaming.md` accepted  
**Handoff:** Use `/swarm-execute` or `/builder` with this plan  

---

## Summary

Migrate locally-stored video streaming from HTTP Range + MP4 → HLS (`.m3u8` + `.ts` segments).  
FFmpeg (already present for thumbnails) remuxes each `.full` file into 6-second segments at ingest time.  
Old Range endpoint stays live throughout — rollout is feature-flagged via `hlsReady` in `VideoDto`.  
YouTube videos are **not affected**.

**Phases:**
1. Database — Liquibase migration
2. Backend Core — transcode service + HLS controller
3. Backend Wiring — integrate into download pipeline + scheduler
4. Frontend — `hls.js` player + fallback logic
5. Migration Job — backfill existing videos
6. Cleanup — delete `.full` files, retire old cache

---

## Phase 1: Database Migration (0.5 days)

### Task 1: Add `hls_status` and `hls_path` to `video_sources`

**File:** `api/src/main/resources/db/changelog/` — create next-numbered migration file  
**Acceptance criteria:**
- `mvn verify` passes with new columns present
- `hls_status` defaults to `'PENDING'` for all existing rows
- `hls_path` is nullable

**Steps:**
1. Find the latest Liquibase file number in `db/changelog/`
2. Create `V{n}__add_hls_fields_to_video_sources.xml` (or `.sql` matching project convention):

```sql
ALTER TABLE video_sources
  ADD COLUMN hls_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  ADD COLUMN hls_path   VARCHAR(500);
```

3. Add it to the Liquibase master changelog (check `db.changelog-master.yaml` or `db.changelog-master.xml`)
4. Verify with `mvn liquibase:update -Dliquibase.url=...` locally

---

## Phase 2: Backend Core (3 days)

### Task 2: `HlsStatus` enum

**File:** `api/src/main/java/com/canhlabs/funnyapp/entity/HlsStatus.java`

```java
public enum HlsStatus {
    PENDING, PROCESSING, READY, ERROR
}
```

### Task 3: Update `VideoSource` entity

**File:** `api/src/main/java/com/canhlabs/funnyapp/entity/VideoSource.java`

Add two fields:
```java
@Enumerated(EnumType.STRING)
@Column(name = "hls_status")
private HlsStatus hlsStatus = HlsStatus.PENDING;

@Column(name = "hls_path")
private String hlsPath;
```

**Acceptance criteria:**
- Entity compiles and maps to new columns correctly
- Existing tests pass (no schema drift)

### Task 4: `HlsTranscodeService` interface

**File:** `api/src/main/java/com/canhlabs/funnyapp/service/HlsTranscodeService.java`

```java
public interface HlsTranscodeService {
    void transcode(String fileId);
    HlsStatus getStatus(String fileId);
    Path getPlaylistPath(String fileId);
    Path getSegmentPath(String fileId, String segmentName);
    boolean isSegmentValid(String fileId, String segmentName);
}
```

### Task 5: `HlsTranscodeServiceImpl`

**File:** `api/src/main/java/com/canhlabs/funnyapp/service/impl/HlsTranscodeServiceImpl.java`

**Acceptance criteria:**
- `transcode(fileId)` sets status PROCESSING → READY on success, ERROR on failure
- FFmpeg command uses `-c copy` (stream-copy, no re-encode)
- Output directory `video-cache/{fileId}/` is created before FFmpeg runs
- Uses `ProcessBuilder` with `inheritIO()` redirected to log
- Process runs on a Virtual Thread (non-blocking caller)
- Status is persisted to DB via `VideoSourceRepository`
- `getPlaylistPath` / `getSegmentPath` resolve from `AppConstant.CACHE_DIR`

**FFmpeg command constructed:**
```
ffmpeg -i {CACHE_DIR}/{fileId}.full
       -c:v copy -c:a copy
       -f hls
       -hls_time 6
       -hls_list_size 0
       -hls_flags independent_segments
       -hls_segment_filename {CACHE_DIR}/{fileId}/seg_%03d.ts
       {CACHE_DIR}/{fileId}/playlist.m3u8
```

**Key implementation notes:**
- Check `{fileId}.full` exists before starting; set ERROR if missing
- Create output dir: `Files.createDirectories(Path.of(CACHE_DIR, fileId))`
- After FFmpeg exits, verify `playlist.m3u8` exists before setting READY
- Use `@Async` or `Thread.ofVirtual().start(...)` — do not block caller

**Unit test: `HlsTranscodeServiceImplTest`**
- Mock `VideoSourceRepository`, mock `ProcessBuilder` (or use test double)
- Assert status transitions: PENDING → PROCESSING → READY
- Assert status transitions: PENDING → PROCESSING → ERROR (nonzero exit code)
- Assert correct FFmpeg args are built

### Task 6: `HlsStreamController`

**File:** `api/src/main/java/com/canhlabs/funnyapp/web/HlsStreamController.java`

**Endpoints:**

| Method | Path | Response |
|--------|------|----------|
| GET | `/api/v1/hls/{fileId}/playlist.m3u8` | 200 `application/vnd.apple.mpegurl` / 202 / 404 |
| GET | `/api/v1/hls/{fileId}/{segment}.ts` | 200 `video/mp2t` with immutable cache headers |
| GET | `/api/v1/hls/{fileId}/status` | 200 `{ "status": "READY" }` |

**Acceptance criteria:**
- Playlist endpoint returns 202 + `Retry-After: 5` header when status is PROCESSING
- Playlist endpoint returns 404 when status is PENDING or ERROR
- Segment endpoint returns `Cache-Control: public, max-age=31536000, immutable`
- Segment endpoint returns 404 for unknown segment names (path traversal safe — validate filename matches `seg_\d+\.ts`)
- Status endpoint always returns 200 with current status string
- Served via `FileSystemResource` (no Guava cache in this path)

**Security:** Reject segment names containing `..` or `/` — validate with regex `^seg_\d{3}\.ts$`.

**Unit test: `HlsStreamControllerTest`**
- Mock `HlsTranscodeService`
- Assert 200 response when READY + file exists
- Assert 202 + Retry-After when PROCESSING
- Assert 404 when PENDING/ERROR
- Assert path traversal attempt `../etc/passwd` returns 400

### Task 7: Update `VideoDto`

**File:** `api/src/main/java/com/canhlabs/funnyapp/dto/VideoDto.java`

Add two fields:
```java
private boolean hlsReady;
private String  hlsUrl;   // "/api/v1/hls/{fileId}/playlist.m3u8"
```

**Populate in `StreamVideoServiceImpl.getVideoById()` and `getVideosToStream()`:**
```java
dto.setHlsReady(source.getHlsStatus() == HlsStatus.READY);
if (dto.isHlsReady()) {
    dto.setHlsUrl("/api/v1/hls/" + source.getSourceId() + "/playlist.m3u8");
}
```

**Acceptance criteria:**
- Existing API consumers receive `hlsReady: false` for all current videos (no breaking change)
- Once transcoding completes, `hlsReady: true` and `hlsUrl` populated on next request

---

## Phase 3: Backend Wiring (1 day)

### Task 8: Wire transcoding into `VideoStorageServiceImpl`

**File:** `api/src/main/java/com/canhlabs/funnyapp/service/impl/VideoStorageServiceImpl.java`

**Change:** After a file is downloaded from Google Drive, fork transcoding as an async task.

```java
// After file write completes, still inside StructuredTaskScope:
scope.fork(() -> {
    hlsTranscodeService.transcode(fileId);
    return null;
});
```

**Acceptance criteria:**
- Download and transcoding both run in the same `StructuredTaskScope`
- A transcoding failure does NOT fail the download (catch exception inside the fork, log it)
- `VideoSource` is saved to DB with `hlsStatus = PENDING` before fork starts

### Task 9: Add feature flag in `application.yaml`

```yaml
hls:
  enabled: true
  segment-duration: 6     # seconds
  output-dir: video-cache
```

Bind via `@ConfigurationProperties("hls")` — a new `HlsProperties` config class.  
`HlsTranscodeServiceImpl` reads `hlsProperties.isEnabled()` before running FFmpeg.

### Task 10: Add retry job to `AppScheduler`

**File:** `api/src/main/java/com/canhlabs/funnyapp/jobs/AppScheduler.java`

```java
@Scheduled(cron = "0 0 2 * * *")   // 2 AM daily
void retryFailedHlsTranscoding() {
    // videoSourceRepository.findByHlsStatusIn(List.of(PENDING, ERROR))
    //   .forEach(v -> hlsTranscodeService.transcode(v.getSourceId()));
}
```

**Acceptance criteria:**
- Only fires when `hls.enabled = true`
- Does not re-process videos already in PROCESSING or READY state
- Logs how many videos were submitted for retry

---

## Phase 4: Frontend (2 days)

### Task 11: Add `hls.js` dependency

```bash
cd webapp
npm install hls.js
```

**Acceptance criteria:**
- `npm run build` succeeds with `hls.js` included
- Import is lazy (`React.lazy` / dynamic import) to avoid loading on pages without video

### Task 12: `HlsVideoPlayer` component

**File:** `webapp/src/components/video/HlsVideoPlayer.jsx`

```jsx
import { useEffect, useRef } from 'react';
import Hls from 'hls.js';

export function HlsVideoPlayer({ src, className }) {
  const videoRef = useRef(null);

  useEffect(() => {
    if (!src || !videoRef.current) return;

    if (Hls.isSupported()) {
      const hls = new Hls({ startLevel: -1 });
      hls.loadSource(src);
      hls.attachMedia(videoRef.current);
      hls.on(Hls.Events.ERROR, (_, data) => {
        if (data.fatal) hls.destroy();
      });
      return () => hls.destroy();
    } else if (videoRef.current.canPlayType('application/vnd.apple.mpegurl')) {
      // Safari: native HLS
      videoRef.current.src = src;
    }
  }, [src]);

  return <video ref={videoRef} controls className={className} />;
}
```

**Acceptance criteria:**
- Plays in Chrome/Firefox via hls.js
- Plays in Safari via native HLS
- Cleans up hls instance on unmount
- Renders nothing (or spinner) if `src` is null

### Task 13: Update video player integration

**Where:** Wherever `<video>` or the existing player is rendered for local videos.

**Logic:**
```jsx
function VideoPlayer({ video }) {
  const [status, setStatus] = useState(video.hlsReady ? 'READY' : 'PENDING');

  // Poll status if not yet ready
  useEffect(() => {
    if (status === 'READY') return;
    const interval = setInterval(async () => {
      const res = await fetch(`/api/v1/hls/${video.fileId}/status`);
      const data = await res.json();
      setStatus(data.status);
      if (data.status === 'READY' || data.status === 'ERROR') {
        clearInterval(interval);
      }
    }, 5000);
    return () => clearInterval(interval);
  }, [status, video.fileId]);

  if (status === 'READY') {
    return <HlsVideoPlayer src={video.hlsUrl} />;
  }
  if (status === 'PROCESSING') {
    return <div>Preparing video...</div>;
  }
  // Fallback: Range-based MP4 player for PENDING/ERROR
  return <video src={`/api/v1/stream/${video.fileId}`} controls />;
}
```

**Acceptance criteria:**
- Uses `HlsVideoPlayer` when `hlsReady = true`
- Shows processing indicator when status = PROCESSING
- Falls back to existing Range stream when PENDING or ERROR
- No flicker: initial render uses `video.hlsReady` from API response

### Task 14: Frontend tests

**File:** `webapp/src/components/video/HlsVideoPlayer.test.jsx`

- Test: renders video element
- Test: attaches hls.js when Hls.isSupported() returns true (mock hls.js)
- Test: uses native src when Hls.isSupported() returns false
- Test: cleans up on unmount (hls.destroy called)

---

## Phase 5: Backfill Migration Job (1 day)

### Task 15: One-shot admin endpoint to trigger backfill

**File:** `api/src/main/java/com/canhlabs/funnyapp/web/AdminVideoController.java` (or existing admin controller)

```
POST /api/v1/admin/hls/migrate
  → Submits all VideoSource with hlsStatus=PENDING where .full file exists
  → Returns { "submitted": 42 }
  → Requires admin role (existing security config)
```

**Acceptance criteria:**
- Endpoint is authenticated (admin only)
- Does not re-submit PROCESSING/READY/ERROR videos
- Returns count of submitted videos
- Each transcoding runs asynchronously — response is immediate

### Task 16: Backfill verification script (optional, for ops)

A simple log-query or actuator check to confirm all videos reach READY status:
```
GET /api/v1/admin/hls/stats
→ { "PENDING": 0, "PROCESSING": 2, "READY": 150, "ERROR": 1 }
```

---

## Phase 6: Cleanup (0.5 days — after all videos are READY)

### Task 17: Delete `.full` files after HLS verification

**When:** All `VideoSource` rows have `hlsStatus = READY`

**Change in `VideoStorageServiceImpl.deleteIfEligible()`:**
```java
// After HLS is confirmed READY, the .full file is safe to remove
if (source.getHlsStatus() == HlsStatus.READY) {
    Files.deleteIfExists(Path.of(CACHE_DIR, fileId + ".full"));
}
```

**Acceptance criteria:**
- Only deletes `.full` when `hlsStatus = READY` (not before)
- Logs deletion with fileId
- Does not throw if file already missing

### Task 18: Remove Guava chunk cache from HLS path (future cleanup)

- `VideoCacheImpl` and `ChunkIndexCacheImpl` are only used in `StreamVideoServiceImpl` (Range path)
- Once `VideoStreamController` is deprecated, remove cache wiring
- Leave cache classes in place during transition — mark with `@Deprecated` when Range endpoint is removed

---

## Testing Checklist

| Test | Type | Must Pass |
|------|------|-----------|
| `HlsTranscodeServiceImplTest` | Unit | Phase 2 |
| `HlsStreamControllerTest` | Unit | Phase 2 |
| Path traversal on segment name | Unit | Phase 2 |
| `VideoStorageServiceImpl` transcoding fork | Unit | Phase 3 |
| `HlsVideoPlayer` renders + cleanup | Unit | Phase 4 |
| Backfill endpoint returns 401 unauthenticated | Unit | Phase 5 |
| End-to-end: upload `.full` → transcode → play | Manual / Integration | Phase 5 |
| Existing Range endpoint still returns 206 | Integration | Phase 3 |
| `mvn verify` passes throughout | CI | All phases |

---

## Risk Mitigations

| Risk | Mitigation |
|------|------------|
| FFmpeg not in PATH on prod | Verify in `HlsTranscodeServiceImpl` on startup; log warning if missing; set `hls.enabled=false` |
| Transcoding fills disk | Add disk-space check before transcoding; alert via `AppScheduler` log |
| Segments served with wrong MIME type | Enforce `Content-Type: video/mp2t` explicitly in controller |
| Path traversal via segment name | Validate segment name with regex `^seg_\d{3}\.ts$`; reject anything else with 400 |
| hls.js bundle bloat | Lazy-import; verify `npm run build` output size stays under 2 MB total |
| DB migration fails on prod | Test Liquibase migration on staging before prod deploy |

---

## File Map

| New/Changed File | Phase |
|---|---|
| `db/changelog/V{n}__add_hls_fields_to_video_sources.xml` | 1 |
| `entity/HlsStatus.java` | 2 |
| `entity/VideoSource.java` (add 2 fields) | 2 |
| `service/HlsTranscodeService.java` | 2 |
| `service/impl/HlsTranscodeServiceImpl.java` | 2 |
| `web/HlsStreamController.java` | 2 |
| `dto/VideoDto.java` (add 2 fields) | 2 |
| `service/impl/StreamVideoServiceImpl.java` (populate hlsReady/hlsUrl) | 2 |
| `service/impl/VideoStorageServiceImpl.java` (fork transcode) | 3 |
| `config/HlsProperties.java` | 3 |
| `application.yaml` (hls section) | 3 |
| `jobs/AppScheduler.java` (retry job) | 3 |
| `webapp/src/components/video/HlsVideoPlayer.jsx` | 4 |
| `webapp/src/components/video/HlsVideoPlayer.test.jsx` | 4 |
| `webapp/src/components/video/VideoPlayer.jsx` (update) | 4 |
| `web/AdminVideoController.java` (backfill endpoint) | 5 |
| `service/impl/VideoStorageServiceImpl.java` (deleteIfEligible update) | 6 |
