-- Force sync upserts by Drive file id (ON CONFLICT). Prod pre-checked 2026-10-11: no duplicate (source_type, source_id).
CREATE UNIQUE INDEX ux_video_sources_source ON video_sources (source_type, source_id);
