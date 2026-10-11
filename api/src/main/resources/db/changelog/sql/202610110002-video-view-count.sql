-- Real view counter (1 view = >= 3 s of playback, deduped per viewer per video for 30 min in memory).
ALTER TABLE video_sources
    ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;
