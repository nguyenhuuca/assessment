# ADR-0006: HLS Video Streaming for Local Videos

## Metadata

**Status:** Proposed · **Date:** 2026-05-30 · **Deciders:** nguyenhuuca · **Tags:** infrastructure, api, frontend  
**Related PRD:** N/A · **Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** ✅ Follows Golden Path

---

## Context

Local videos (sourceType = `google_drive`) are currently served via HTTP Range requests on raw MP4 files. This approach has several pain points:

- Range-based MP4 responses are not CDN-cacheable — every byte-range combination is a unique cache key
- The application maintains a Guava `byte[]` chunk cache in heap memory to avoid repeated disk reads
- Seeking requires custom chunk-boundary math in the application layer, causing byte-range mismatches
- No path to adaptive bitrate (ABR) streaming without a full rewrite
- Player compatibility depends on browsers correctly implementing Range request handling

YouTube videos are out of scope — they are embedded via `embedLink` and served entirely by YouTube's infrastructure.

---

## Decision Drivers

- Eliminate application-level byte[] cache to reduce heap pressure
- Enable CDN caching of video segments (immutable, fixed-size)
- Use a standard protocol supported natively by all target browsers
- Keep ingest overhead low — re-encoding is unacceptable for a sync pipeline
- Maintain backward compatibility during rollout

---

## Considered Options

### Option 1: Keep Range/MP4 (status quo)
Continue serving raw MP4 bytes with HTTP Range headers and the existing Guava chunk cache.

| Pros | Cons |
|------|------|
| No migration effort | Not CDN-cacheable (varied byte ranges) |
| Byte-precise seeking | Guava byte[] cache consumes heap |
| Wide browser support | Custom chunk-boundary logic is brittle |
| | No ABR path without full rewrite |

### Option 2: HLS with FFmpeg stream-copy (chosen)
Segment videos into `.ts` chunks and `.m3u8` playlists at ingest time using FFmpeg `-c copy` (no re-encode). Serve segments directly via `FileSystemResource`. Use `hls.js` on Chrome/Firefox; native HLS on Safari.

| Pros | Cons |
|------|------|
| Segments are immutable — trivially CDN-cacheable | FFmpeg invocation per video at ingest time |
| Eliminates application-level byte[] cache | More complex storage layout (directory per video) |
| Standard protocol — no custom player code | Adds `hls.js` dependency (~300 KB) to frontend |
| Foundation for ABR and AES-128 DRM | |
| FFmpeg stream-copy is fast (< 1s per GB) | |

### Option 3: MPEG-DASH
Segment using DASH standard instead of HLS.

| Pros | Cons |
|------|------|
| Similar CDN and ABR benefits to HLS | Less native browser support than HLS |
| | `dash.js` is more complex than `hls.js` |
| | No meaningful advantage over HLS for this use case |

### Option 4: On-demand segmentation (first request)
Generate HLS segments lazily when first requested.

| Pros | Cons |
|------|------|
| No ingest overhead | Unacceptable first-play latency |
| | Concurrent first-plays cause resource contention |

---

## Decision Outcome
**Chosen Option:** Option 2 — HLS with FFmpeg stream-copy

**Rationale:** Stream-copy segmentation at ingest time imposes negligible overhead (no re-encode, < 1s per GB) while delivering immutable segments that are CDN-cacheable. Eliminating the Guava `byte[]` chunk cache reduces heap pressure with no loss of functionality — the OS page cache handles hot segments transparently. HLS has broader native browser support than DASH, and `hls.js` is a simpler integration than `dash.js`. The rollout is feature-flagged and backward-compatible: the old Range endpoint remains until all videos are transcoded.

### Quantified Impact

| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| Storage overhead | 1× | ~1.01× | Stream-copy ≈ source size |
| Ingest CPU | None | < 1s per GB | FFmpeg `-c copy` |
| Server heap (chunk cache) | Guava byte[] allocated | Eliminated | OS page cache sufficient |

---

## Consequences
**Positive:**
- Standard HLS playback across all browsers with no custom player code
- Application-level byte[] chunk cache eliminated — lower heap pressure
- Immutable segments enable trivial CDN caching in future
- Foundation for ABR renditions and AES-128 DRM

**Negative:**
- FFmpeg must be available in all deployment environments
- Storage layout is more complex (directory per video vs single file)
- Frontend gains `hls.js` dependency

**Risks:**
- FFmpeg process failure leaves video in `PROCESSING` state — mitigated by nightly retry scheduler
- Feature-flag rollout requires managing two serving paths concurrently during transition

---

## Validation
- [ ] Tech Strategy alignment confirmed
- [ ] Related plan document created: [docs/plans/plan-hls-migration.md](../plans/plan-hls-migration.md)

---

## Links
- [RFC 8216 — HTTP Live Streaming](https://datatracker.ietf.org/doc/html/rfc8216)
- [hls.js documentation](https://github.com/video-dev/hls.js)
- [FFmpeg HLS muxer](https://ffmpeg.org/ffmpeg-formats.html#hls-1)
- Plan document: `docs/plans/plan-hls-migration.md`

---

## Changelog
| Date | Author | Change |
|------|--------|--------|
| 2026-05-30 | nguyenhuuca | Initial draft |
| 2026-05-31 | nguyenhuuca | Restructured to new ADR template; technical details moved to plan doc |
