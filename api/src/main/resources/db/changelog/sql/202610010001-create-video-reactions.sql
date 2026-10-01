CREATE TABLE video_reactions (
    user_id    BIGINT      NOT NULL REFERENCES users(id)         ON DELETE CASCADE,
    video_id   BIGINT      NOT NULL REFERENCES video_sources(id) ON DELETE CASCADE,
    reaction   VARCHAR(10) NOT NULL CHECK (reaction IN ('LIKE', 'DISLIKE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, video_id)
);

CREATE INDEX idx_video_reactions_video ON video_reactions(video_id, reaction);
