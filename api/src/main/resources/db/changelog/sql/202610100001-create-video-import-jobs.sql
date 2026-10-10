CREATE TABLE video_import_jobs
(
    id               BIGSERIAL PRIMARY KEY,
    source_url       TEXT         NOT NULL,
    normalized_url   TEXT         NOT NULL,
    platform         VARCHAR(16)  NOT NULL,
    requested_title  VARCHAR(200),
    resolved_title   VARCHAR(200),
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    progress_pct     SMALLINT     NOT NULL DEFAULT 0,
    downloaded_bytes BIGINT       NOT NULL DEFAULT 0,
    total_bytes      BIGINT       NOT NULL DEFAULT 0,
    scheduled_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at       TIMESTAMPTZ,
    finished_at      TIMESTAMPTZ,
    drive_file_id    VARCHAR(128),
    error_code       VARCHAR(48),
    error_message    VARCHAR(500),
    attempts         SMALLINT     NOT NULL DEFAULT 0,
    created_by       BIGINT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_video_import_jobs_status_scheduled ON video_import_jobs (status, scheduled_at);
CREATE INDEX idx_video_import_jobs_created ON video_import_jobs (created_at DESC, id DESC);

-- at most one active import per normalized URL
CREATE UNIQUE INDEX uq_video_import_jobs_active_url ON video_import_jobs (normalized_url)
    WHERE status IN ('PENDING', 'DOWNLOADING', 'UPLOADING');
