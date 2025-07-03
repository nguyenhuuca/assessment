ALTER TABLE video_sources
ADD COLUMN is_hide boolean DEFAULT false,
ADD COLUMN thumbnail_path TEXT;