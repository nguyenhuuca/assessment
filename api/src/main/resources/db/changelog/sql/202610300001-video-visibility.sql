--liquibase formatted sql

--changeset nguyenhuuca:202610300001-video-visibility
-- One visibility value per video (ADR-0035): FEED, COLLECTION_ONLY or HIDDEN replaces is_hide.
ALTER TABLE video_sources
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'FEED';
UPDATE video_sources SET visibility = 'HIDDEN' WHERE is_hide = true;
ALTER TABLE video_sources
    ADD CONSTRAINT chk_video_sources_visibility
        CHECK (visibility IN ('FEED', 'COLLECTION_ONLY', 'HIDDEN'));

-- Feed reads only FEED rows (replaces the is_hide predicate from 202610240002).
DROP INDEX IF EXISTS idx_video_sources_feed;
CREATE INDEX idx_video_sources_feed
    ON video_sources (priority DESC, created_at DESC)
    WHERE visibility = 'FEED' AND status = 'PUBLISHED';

ALTER TABLE video_sources DROP COLUMN is_hide;

-- Visibility chosen in the Import form, copied onto video_sources when the row is created.
ALTER TABLE video_import_jobs
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'FEED';
ALTER TABLE video_import_jobs
    ADD CONSTRAINT chk_video_import_jobs_visibility
        CHECK (visibility IN ('FEED', 'COLLECTION_ONLY'));

-- saveInfo / upsertInfo look the job up by its Drive file id.
CREATE INDEX idx_video_import_jobs_drive_file_id
    ON video_import_jobs (drive_file_id)
    WHERE drive_file_id IS NOT NULL;

--rollback DROP INDEX IF EXISTS idx_video_import_jobs_drive_file_id;
--rollback ALTER TABLE video_import_jobs DROP CONSTRAINT chk_video_import_jobs_visibility;
--rollback ALTER TABLE video_import_jobs DROP COLUMN visibility;
--rollback ALTER TABLE video_sources ADD COLUMN is_hide BOOLEAN NOT NULL DEFAULT false;
--rollback UPDATE video_sources SET is_hide = (visibility = 'HIDDEN');
--rollback DROP INDEX IF EXISTS idx_video_sources_feed;
--rollback CREATE INDEX idx_video_sources_feed ON video_sources (priority DESC, created_at DESC) WHERE is_hide = false AND status = 'PUBLISHED';
--rollback ALTER TABLE video_sources DROP CONSTRAINT chk_video_sources_visibility;
--rollback ALTER TABLE video_sources DROP COLUMN visibility;
