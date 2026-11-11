-- Size in bytes of the cached full video file (admin list). NULL until known.
ALTER TABLE video_sources
    ADD COLUMN file_size BIGINT NULL;
