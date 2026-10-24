-- Public feed query (published, not hidden, ordered by priority then newest) reads only the few visible rows.
CREATE INDEX IF NOT EXISTS idx_video_sources_feed
    ON video_sources (priority DESC, created_at DESC)
    WHERE is_hide = false AND status = 'PUBLISHED';
