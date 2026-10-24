-- HLS rendition state per video (ADR-0031). NONE until the SUPER_ADMIN turns HLS on.
ALTER TABLE video_sources
    ADD COLUMN hls_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN hls_error  VARCHAR(40);

ALTER TABLE video_sources
    ADD CONSTRAINT chk_video_sources_hls_status
        CHECK (hls_status IN ('NONE', 'PENDING', 'PROCESSING', 'READY', 'ERROR'));

-- Worker queue lookups touch only the few in-flight rows.
CREATE INDEX idx_video_sources_hls_queue
    ON video_sources (updated_at)
    WHERE hls_status IN ('PENDING', 'PROCESSING');
