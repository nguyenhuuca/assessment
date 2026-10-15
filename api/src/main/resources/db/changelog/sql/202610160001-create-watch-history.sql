CREATE TABLE watch_history
(
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    video_id     BIGINT      NOT NULL REFERENCES video_sources (id) ON DELETE CASCADE,
    watched_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    position_sec INT         NOT NULL DEFAULT 0 CHECK (position_sec >= 0),
    duration_sec INT CHECK (duration_sec > 0),
    PRIMARY KEY (user_id, video_id)
);

CREATE INDEX idx_watch_history_user_recent ON watch_history (user_id, watched_at DESC);
