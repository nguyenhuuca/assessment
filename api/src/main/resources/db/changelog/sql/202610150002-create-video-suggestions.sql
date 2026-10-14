CREATE TABLE video_suggestions
(
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_url     TEXT         NOT NULL,
    normalized_url TEXT         NOT NULL,
    platform       VARCHAR(16)  NOT NULL,
    title          VARCHAR(200),
    note           VARCHAR(300),
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reject_reason  VARCHAR(32),
    reject_note    VARCHAR(300),
    reviewed_by    BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at    TIMESTAMPTZ,
    import_job_id  BIGINT       REFERENCES video_import_jobs (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- at most one pending suggestion per normalized URL
CREATE UNIQUE INDEX uq_video_suggestions_pending_url ON video_suggestions (normalized_url) WHERE status = 'PENDING';
CREATE INDEX idx_video_suggestions_status ON video_suggestions (status, created_at DESC);
CREATE INDEX idx_video_suggestions_user ON video_suggestions (user_id, created_at DESC);
CREATE INDEX idx_video_suggestions_job ON video_suggestions (import_job_id) WHERE import_job_id IS NOT NULL;
