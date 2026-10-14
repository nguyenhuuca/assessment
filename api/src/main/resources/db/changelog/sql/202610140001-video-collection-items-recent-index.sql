-- Cover lookup (newest item per collection) and the per-collection video page both order by added_at DESC.
CREATE INDEX idx_video_collection_items_recent ON video_collection_items (collection_id, added_at DESC, video_id DESC);
