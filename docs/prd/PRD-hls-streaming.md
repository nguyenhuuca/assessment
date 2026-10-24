# PRD: HLS Streaming for Selected Videos (Feature Flag)

<!--
Product Requirements Document
Filename: docs/prd/PRD-hls-streaming.md
Owner: nguyenhuuca
Handoff to: Architect (/architect), UI/UX Designer (/ui-ux-designer)
Related Skills: scope, planning-artifacts
-->

## Overview

**Status:** Approved
**Author:** nguyenhuuca
**Date:** 2026-10-24
**Version:** 1.0
**Tracking:** `docs/tracking.md` row "HLS Streaming"
**PR-FAQ:** N/A. A learning feature on an existing playback path, not a new user-facing initiative.
**Stakeholders:** nguyenhuuca (owner, SUPER_ADMIN), viewers of Drive videos
**Replaces:** the removed ADR-0006 and `plan-hls-migration.md` (deleted 2026-10-24 in `196e9f54`: they predated the Drive force sync, admin delete and the React player)

## Problem Statement

Drive videos (`VideoSource.sourceType = 'google_drive'`) play only through HTTP Range on the raw `.full` file (`GET /v1/funny-app/video-stream/stream/{fileId}`, built in `Converter.java:73-74`). The owner wants to learn and try HLS (`.m3u8` playlist + `.ts` segments) on the real app, without risking playback for everyone and without filling the disk.

This is a learning feature. There is no measured pain point with Range playback today.

### Evidence

**Quantitative Evidence:**
- None. There is no playback-failure or latency data pointing at Range. The owner's goal is to try HLS.
- Disk on the prod VM is nearly full (owner, 2026-10-24). An HLS copy costs about 1× the `.full` size per video, so converting every video is not possible.

**Qualitative Evidence:**
- Owner: "work for fun"; "Hiện tại tranh hết disk. Chỉ video nào được chọn mới thực hiện HLS."
- A review of the old plan (2026-10-24) found that HLS for every video at ingest time no longer fits: there are two download paths (scheduler and admin force sync), an admin delete action (#58), and a 350-line `VideoPlayer.jsx` with resume and watch history.

## Goals & Success Metrics

| Goal | Metric | Target |
|------|--------|--------|
| Switch playback without a deploy | Toggling Harness flag `hls_streaming_enabled` changes what the player uses for READY videos | ON → HLS, OFF → Range, at the next video-list fetch (≤ 5 min `staleTime`, or a reload); 0 playback errors caused by the switch |
| Every selected video gets HLS | Videos the SUPER_ADMIN turned HLS on for, by status | 100% READY, or ERROR with a reason (unsupported codec, low disk) and Range fallback |
| No harm to the VM | Disk and CPU incidents caused by transcoding | 0 (one job at a time, disk guard) |

## User Stories

### Viewer (guest or signed in)

- As a viewer, I want a video to keep playing whichever path it uses, so that the switch is invisible to me.
  - Acceptance: with the flag ON, a READY video plays through HLS (hls.js on Chrome/Firefox/Android, native on Safari/iOS). If HLS fails, the player falls back to the Range URL at the same position. Resume position and watch history work the same on both paths.
- As a viewer swiping through reels, I want HLS videos to start like the others, so that swiping stays smooth.
  - Acceptance: `VideoSwiper` creates the HLS instance for the active slide only and destroys it when the slide changes. No polling per slide.

### SUPER_ADMIN

- As the SUPER_ADMIN, I want to turn HLS on for one video, so that I choose which videos use disk for segments.
  - Acceptance: a toggle on the row in `AdminVideoTable` starts a transcode. The row shows `PENDING → PROCESSING → READY` or `ERROR` with a reason.
- As the SUPER_ADMIN, I want to turn HLS off for a video, so that I get the disk space back.
  - Acceptance: turning it off deletes `video-cache/hls/{fileId}/` and sets the status to NONE. The `.full` file is not touched.
- As the SUPER_ADMIN, I want one global switch, so that I can turn HLS off for everyone at once if something breaks.
  - Acceptance: Harness flag `hls_streaming_enabled` OFF → the API stops returning `hlsUrl`, so every client uses Range. Segments stay on disk.

## Requirements

### Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | `VideoSource` gets an HLS status (`NONE`, `PENDING`, `PROCESSING`, `READY`, `ERROR`) and an error reason; default `NONE` | Must Have | Liquibase SQL changeset in `api/src/main/resources/db/changelog/sql/` (`includeAll`) |
| FR-2 | SUPER_ADMIN-only endpoints under `/v1/funny-app/admin/videos/{id}/hls` to turn HLS on and off | Must Have | ADMIN can see the status but not change it, like the Drive force sync (ADMIN views, SUPER_ADMIN acts) |
| FR-3 | Turning HLS on queues a transcode. A single worker runs one job at a time | Must Have | 1 vCPU VM, see the deadlock memory |
| FR-4 | Before transcoding, `ffprobe` checks the codecs. Only H.264 video + AAC/MP3 audio (or no audio) are stream-copied (`-c copy`). Anything else → ERROR "unsupported codec" | Must Have | No re-encode in v1 |
| FR-5 | Disk guard: if free space < `.full` size + 1 GB, the job fails with ERROR "not enough disk" before FFmpeg starts | Must Have | |
| FR-6 | Output is `video-cache/hls/{fileId}/playlist.m3u8` + `seg_N.ts`, about 6 s each, VOD playlist. READY only when FFmpeg exits 0 and the playlist exists. A failed job removes its partial output | Must Have | |
| FR-7 | Turning HLS off, or deleting the video (#58 admin delete), removes `video-cache/hls/{fileId}/` | Must Have | The `.full` file and the Range endpoint stay |
| FR-8 | Public endpoints serve the playlist and segments with correct MIME types. Segment names are validated against a strict regex (no `..`, `/`). Segments get long immutable cache headers | Must Have | Same whitelist as `/video-stream/**` (`AppConstant.java:74-75`), no token in the URL |
| FR-9 | The video DTO includes `hlsUrl` only when flag `hls_streaming_enabled` is ON **and** the status is READY. Otherwise it is null and the client uses Range | Must Have | Flag read through `FeatureFlagService` with default `false` |
| FR-10 | `VideoPlayer` and `VideoSwiper` play `hlsUrl` when present (hls.js lazy-loaded, native HLS on Safari), keep resume position and watch history, and fall back to the Range URL on a fatal HLS error | Must Have | |
| FR-11 | `AdminVideoTable` shows an HLS status column for Drive videos, with an on/off toggle for the SUPER_ADMIN and the error reason on ERROR | Must Have | Read-only for ADMIN |
| FR-12 | A job left in PROCESSING after a restart is reset to PENDING and picked up again at startup | Should Have | |
| FR-13 | Log each job's duration and output size; log the total HLS disk use after each job | Nice to Have | Structured log, no new metric |

### Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Performance | Stream-copy transcode of a 500 MB video < 30 s on the prod VM; playlist and segment responses < 50 ms (static file) |
| NFR-2 | Availability | Range playback is never affected: the flag OFF, any ERROR, or a missing segment directory all fall back to Range |
| NFR-3 | Security | Segment and fileId validation against path traversal (CWE-22). The FFmpeg command is built from a list of arguments, never a shell string (CWE-78). On/off endpoints are SUPER_ADMIN-only |
| NFR-4 | Resources | At most one FFmpeg process at a time; HLS storage only for selected videos |
| NFR-5 | Bundle | hls.js loaded with a dynamic import only when an `hlsUrl` is played; it is not added to the main chunk |

## Scope

### In Scope

- Per-video HLS on/off by the SUPER_ADMIN, with status in `AdminVideoTable`
- Stream-copy transcoding with a codec check, disk guard and a single-job queue
- Public playlist and segment endpoints
- Global Harness flag `hls_streaming_enabled` (on/off for everyone)
- HLS playback with Range fallback in `VideoPlayer` and `VideoSwiper`
- Cleanup of the HLS directory on toggle off and on video delete

### Out of Scope

- Automatic transcoding at ingest (scheduler or force sync) and bulk backfill: the disk is nearly full
- Deleting `.full` files, removing the Range endpoint or the Guava chunk cache: Range is the fallback
- Re-encoding HEVC/VP9/other codecs to H.264
- Several quality levels (ABR), AES-128 encryption or DRM
- Flag targeting per user or by percentage
- CDN configuration for segments
- Adding FFmpeg to the Docker image (prod runs on the VM; tracked as a risk)

## Dependencies

| Dependency | Owner | Status | Risk |
|------------|-------|--------|------|
| FFmpeg + ffprobe on the prod VM (ffmpeg already used by `FfmpegServiceImpl` for thumbnails) | nguyenhuuca | Available (both verified 2026-10-24) | Low |
| Harness FF (`FeatureFlagService`, used by `permission_enforcement_enabled`) | nguyenhuuca | Available; new flag to be created in Harness | Low |
| hls.js (new npm dependency) | — | Not installed | Low |
| Existing video delete (#58) and `DriveFileLocks` | — | In code | Med: delete must also clean the HLS directory without racing a running job |
| Free disk space on the VM | nguyenhuuca | Nearly full | High: limits how many videos can have HLS |

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Disk fills up during a transcode | M | H | Disk guard (FR-5); one job at a time; SA picks videos one by one; toggle off frees space |
| Source codec is HEVC/VP9 and cannot be stream-copied | H | M | `ffprobe` check (FR-4) → ERROR with a reason; Range keeps working |
| FFmpeg hogs the single vCPU and stalls requests | L | H | Stream copy uses little CPU; single job; run on its own thread, never on the request thread |
| hls.js breaks resume or watch history in the player | M | M | Keep one `<video>` element; attach HLS to it; tests for resume and fallback |
| Delete during a running transcode leaves files | L | L | Delete takes the same per-file lock; the job checks after FFmpeg exits and cleans up |
| Flag flip not seen until the list is refetched | H | L | Accepted: up to the 5 min `staleTime` of `useVideos`, or a reload |
| K8s/Helm deploy has no FFmpeg (Dockerfile has only curl) | L | M | Out of scope; documented; HLS jobs fail with ERROR, Range keeps working |

## Open Questions

- [x] Segment length: 6 s (owner, 2026-10-24).
- [x] Is `ffprobe` installed next to `ffmpeg` on the VM? Yes (owner checked, 2026-10-24).
- [x] Should guest users get HLS too? Yes (owner, 2026-10-24): the flag is global and the stream endpoints are public today.

## Appendix

### Mockups/Wireframes

N/A. The only UI change is one column with a toggle in `AdminVideoTable`, using the existing table styles.

### Research

- [RFC 8216: HTTP Live Streaming](https://datatracker.ietf.org/doc/html/rfc8216)
- [hls.js](https://github.com/video-dev/hls.js)
- [FFmpeg HLS muxer](https://ffmpeg.org/ffmpeg-formats.html#hls-2)
- Feasibility review of the old plan (chat, 2026-10-24): the gaps it found are covered by FR-2, FR-7, FR-8 and FR-10 here

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product | nguyenhuuca | 2026-10-24 | Approved |
| Engineering | nguyenhuuca | 2026-10-24 | Approved |
| Design | N/A | | N/A |

---

## Next Steps & Handoffs

After PRD approval:

1. [ ] **Architect Review**: ADR for the storage layout, the transcode queue and the flag check
   - Trigger: `/architect`
   - Output: ADR (`docs/adr/0031-hls-streaming.md`)

2. [ ] **Spec + sequence diagrams**: `/spec docs/prd/PRD-hls-streaming.md docs/adr/0031-hls-streaming.md`
   - Output: `docs/specs/spec-hls-streaming.md`, `docs/sequence/sequence-hls-streaming.md`

3. [ ] **Plan**: `/swarm-plan` → `docs/plans/plan-hls-streaming.md`
