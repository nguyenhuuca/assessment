--changeset canhlabs:add_video_source_priority
ALTER TABLE video_sources ADD COLUMN priority INTEGER NOT NULL DEFAULT 0;
CREATE INDEX idx_video_sources_priority ON video_sources(priority DESC);
