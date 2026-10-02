ALTER TABLE video_comments DROP CONSTRAINT chk_video_comments_status;
ALTER TABLE video_comments
    ADD CONSTRAINT chk_video_comments_status CHECK (status IN ('VISIBLE', 'REMOVED', 'DELETED'));
