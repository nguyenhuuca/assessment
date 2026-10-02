ALTER TABLE video_comments
    ADD COLUMN status            VARCHAR(20)  NOT NULL DEFAULT 'VISIBLE',
    ADD COLUMN moderation_reason VARCHAR(30),
    ADD COLUMN moderation_note   VARCHAR(500),
    ADD COLUMN moderated_by      VARCHAR(255),
    ADD COLUMN moderated_at      TIMESTAMPTZ;

ALTER TABLE video_comments
    ADD CONSTRAINT chk_video_comments_status CHECK (status IN ('VISIBLE', 'REMOVED'));

CREATE INDEX idx_video_comments_status_created ON video_comments (status, created_at DESC);
