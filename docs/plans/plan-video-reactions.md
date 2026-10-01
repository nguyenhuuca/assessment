# Plan: Video Reactions (Like / Unlike / Dislike)

<!--
Implementation Plan
Filename: docs/plans/plan-video-reactions.md
Owner: Builder (/builder)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)
-->

## Overview

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-10-01
**Related Trello:** [9] Backend – Implement Like/Unlike and View Count APIs (like part only), [10] Frontend – Like/Unlike Button
**Decision class:** Two-Way Door (new isolated table + endpoints, no change to existing schemas/APIs) → plan only, no ADR

## Problem

`VoteButtons.jsx` calls `POST /v1/funny-app/video/like` and `/video/unlike`, but **no backend endpoint exists** → Spring returns
`404 "No static resource v1/funny-app/video/like"`. The FE also has logic bugs:

1. Dislike button calls `unlike` (dislike ≠ remove like).
2. Toggling a vote off only changes local state — no API call.
3. `catch {}` swallows errors, so the UI shows a vote the server never stored.
4. `video.upvotes` / `downvotes` are never returned by `/video-stream/list` (`VideoDto` has no such fields), so counts always start at 0.

## Objective

Authenticated users can **like**, **dislike**, or **remove** their reaction on a feed video (one reaction per user per video).
Everyone sees like/dislike counts; logged-in users see their own reaction persisted across reloads.

## Scope

### In Scope
- Videos from the swipe feed (`video_sources`, `GET /video-stream/list`)
- New table `video_reactions`, entity, repo, service, controller, DTOs
- FE: `api.put`, `api/reactions.js`, `useVideoReaction` hook, rewrite `VoteButtons`
- Unit tests BE (Mockito + `@WebMvcTest`) and FE (Vitest)

### Out of Scope
- YouTube `top-videos` (`youtube_video` table — counts come from YouTube API)
- Guest (unauthenticated) reactions
- Reaction counts embedded in `/video-stream/list` (see Decisions)
- View count tracking (other half of Trello [9])
- Hot-score integration (future: like/dislike as a signal for `priority`)

---

## Technical Approach

### Architecture

```
[VideoSwiper] → [VoteButtons]
                     ↓ useVideoReaction(videoId)   (React Query key ['reaction', videoId])
[api/reactions.js]
  GET    /v1/funny-app/videos/{videoId}/reaction   → summary (public; myReaction=null for guests)
  PUT    /v1/funny-app/videos/{videoId}/reaction   body {reaction: LIKE|DISLIKE}  (auth)
  DELETE /v1/funny-app/videos/{videoId}/reaction   (auth)  ← "unlike" / "un-dislike"
        ↓ Spring Boot
[VideoReactionController] → [VideoReactionService / Impl] → [VideoReactionRepository]
        ↓
[PostgreSQL: video_reactions]
```

All three endpoints return the same `ReactionSummaryDto`, so FE replaces its cache with the server truth after each mutation.

```json
{ "videoId": 42, "likeCount": 10, "dislikeCount": 2, "myReaction": "LIKE" }
```

### Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Data model | 1 row per (user, video) with `reaction` enum column | Like ⇄ dislike is a single UPDATE; mutual exclusion enforced by PK |
| Write API | `PUT` (set) + `DELETE` (clear), video-centric URL | Idempotent; client never tracks reaction IDs; matches REST + comments URL style (`/videos/{id}/...`) |
| Upsert | Native `INSERT … ON CONFLICT (user_id, video_id) DO UPDATE` in repository | Race-free under concurrent clicks; no raw SQL in service layer |
| Counts | `COUNT(*) … GROUP BY reaction` on demand, index `(video_id, reaction)` | Exact, simple; feed fetches one video at a time. Denormalized counters deferred until needed |
| Counts not in list API | Separate `GET /reaction` per active video | Keeps `/video-stream/list` user-agnostic and cacheable; no change to `VideoDto` |
| FK | `user_id → users(id) ON DELETE CASCADE`, `video_id → video_sources(id) ON DELETE CASCADE` | Reactions are meaningless once user/video is gone |
| Unauthed write | `AppUtils.getCurrentUser()` in service → 401/403 | Same as `UserSettingsServiceImpl` |
| Rate limit | `@RateLimited(permit = 30)` on PUT/DELETE | Prevents click spam; consistent with other writes |
| CORS | PUT/DELETE already in `setAllowedMethods` (`WebSecurityConfig.java:124`) | No change needed |

### Schema

```sql
CREATE TABLE video_reactions (
    user_id    BIGINT      NOT NULL REFERENCES users(id)         ON DELETE CASCADE,
    video_id   BIGINT      NOT NULL REFERENCES video_sources(id) ON DELETE CASCADE,
    reaction   VARCHAR(10) NOT NULL CHECK (reaction IN ('LIKE', 'DISLIKE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, video_id)
);
CREATE INDEX idx_video_reactions_video ON video_reactions(video_id, reaction);
```

---

## Implementation Steps

### Phase 1: Backend foundation (0.5 day) — `BE-R1`
- [ ] Migration `api/src/main/resources/db/changelog/sql/202610010001-create-video-reactions.sql` (schema above; `includeAll` picks it up)
- [ ] `enums/ReactionType.java` (`LIKE`, `DISLIKE`)
- [ ] `entity/VideoReaction.java` + `@Embeddable VideoReactionId(userId, videoId)`; `@Enumerated(STRING)`
- [ ] `repo/VideoReactionRepository.java`
  - `upsert(userId, videoId, reaction)` — `@Modifying @Query(nativeQuery = true)` with `ON CONFLICT … DO UPDATE SET reaction = EXCLUDED.reaction, updated_at = now()`
  - `deleteByIdUserIdAndIdVideoId(userId, videoId)`
  - `findReaction(userId, videoId)` → `Optional<ReactionType>`
  - `countByReaction(videoId)` → `List<ReactionCount>` projection (`GROUP BY reaction`)

**AC:** app starts, Liquibase applies migration on clean DB and on DB from `db/dump.sql`.

### Phase 2: Service (0.5 day) — `BE-R2` (depends on BE-R1)
- [ ] `dto/reaction/ReactionSummaryDto.java` (`videoId, likeCount, dislikeCount, myReaction`)
- [ ] `dto/reaction/ReactionRequest.java` (`@NotNull ReactionType reaction`)
- [ ] `service/VideoReactionService.java` + `service/impl/VideoReactionServiceImpl.java`
  - `getSummary(videoId)` — `@Transactional(readOnly = true)`; myReaction only if a user is authenticated
  - `react(videoId, type)` — `@Transactional`; verify video exists & not hidden (`VideoSourceRepository.existsById…`) else 404; upsert; return summary
  - `removeReaction(videoId)` — `@Transactional`; idempotent delete; return summary
- [ ] `VideoReactionServiceImplTest` (Mockito): like, dislike, switch like→dislike, remove, remove when none, unknown video, unauthenticated write, guest summary has `myReaction=null`

**AC:** all service tests green; no raw SQL in service.

### Phase 3: Controller (0.5 day) — `BE-R3` (depends on BE-R2)
- [ ] `web/VideoReactionController.java` at `AppConstant.API.BASE_URL + "/videos/{videoId}/reaction"`, returns `ResultObjectInfo<ReactionSummaryDto>`; Swagger `@Operation`; `@RateLimited(permit = 30)` on PUT/DELETE
- [ ] Security: GET public — add `"/v1/funny-app/videos/*/reaction"` GET handling consistent with comments; PUT/DELETE must require a JWT (verify that `ALLOW_ALL_METHOD` entry does not bypass auth for writes)
- [ ] `VideoReactionControllerTest` (`@WebMvcTest`): 200 GET guest, 200 PUT/DELETE authed, 400 invalid body, 401 PUT without token, 404 unknown video
- [ ] `mvn verify` passes coverage gate (`api/.coverage-threshold`, +1%)

**AC:** `curl -X PUT …/videos/1/reaction -H 'Authorization: <jwt>' -d '{"reaction":"LIKE"}'` → counts updated; without JWT → 401.

### Phase 4: Frontend data layer (0.5 day) — `FE-R1` (can start in parallel against the contract above; integration depends on BE-R3)
- [ ] `src/api/client.js`: add `put: (path, body) => request('PUT', path, body)`
- [ ] `src/api/reactions.js`: `get(videoId)`, `set(videoId, reaction)`, `remove(videoId)`
- [ ] `src/api/videos.js`: remove dead `like` / `unlike`
- [ ] `src/hooks/useVideoReaction.js`: `useQuery(['reaction', videoId], enabled: !!videoId)` + `useMutation` with optimistic update (`onMutate` snapshot → `onError` rollback → `onSuccess` `setQueryData` with server summary); refetch on login/logout (include `isLoggedIn` in key)
- [ ] Hook tests (Vitest)

### Phase 5: Frontend UI (0.5 day) — `FE-R2` (depends on FE-R1)
- [ ] Rewrite `components/video/VoteButtons.jsx` using the hook:
  - Like: `none|DISLIKE → set(LIKE)`, `LIKE → remove()` (unlike)
  - Dislike: `none|LIKE → set(DISLIKE)`, `DISLIKE → remove()`
  - Show `likeCount`; dislike shows label (optionally count)
  - Not logged in → buttons don't call API; show hint "Đăng nhập để thích video" (same guard style as comments `isLoggedIn`)
  - Disable while mutation pending; error → rollback (no silent `catch {}`)
- [ ] Rewrite `__tests__/VoteButtons.test.jsx`: like, unlike, dislike, switch, guest guard, rollback on API error, reset on video change
- [ ] `npm run lint && npm run test && npm run build`

### Phase 6: Verification (0.25 day) — `QA-R1` (depends on BE-R3, FE-R2)
- [ ] Manual E2E: login → like → reload (persists) → switch to dislike → unlike; second user sees aggregated counts; guest sees counts, cannot vote
- [ ] Confirm no 404 in network tab; update Trello [9]/[10] and `docs/tracking.md`

---

## Dependency Graph

```
BE-R1 ──► BE-R2 ──► BE-R3 ──┐
                            ├──► QA-R1
FE-R1 ──► FE-R2 ────────────┘
(FE-R1 can start immediately against the API contract)
```

**Estimate:** ~2.5 days total (≈1.5 days on critical path with BE/FE in parallel).

## Risks

| Risk | Mitigation |
|------|-----------|
| `ALLOW_ALL_METHOD` style whitelisting makes PUT/DELETE public | Service always calls `AppUtils.getCurrentUser()`; controller test asserts 401 without token |
| Rolling deploy + new table → cached-plan errors | Additive table only (no `ALTER` on existing tables) |
| COUNT query cost on hot videos | Covered by `(video_id, reaction)` index; move to counter columns / Guava cache if p95 > 50 ms |
| Spam clicking | `@RateLimited` + FE disables buttons while pending |
