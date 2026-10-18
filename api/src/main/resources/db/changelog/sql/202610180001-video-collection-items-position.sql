-- User-defined order inside a collection. DEFAULT 0 keeps old pods inserting during a rolling deploy.
ALTER TABLE video_collection_items ADD COLUMN position INTEGER NOT NULL DEFAULT 0;

-- Keep today's order: newest added first.
UPDATE video_collection_items i
SET position = r.rn - 1
FROM (SELECT collection_id, video_id,
             row_number() OVER (PARTITION BY collection_id ORDER BY added_at DESC, video_id DESC) AS rn
      FROM video_collection_items) r
WHERE i.collection_id = r.collection_id AND i.video_id = r.video_id;

DROP INDEX idx_video_collection_items_recent;
CREATE INDEX idx_video_collection_items_position ON video_collection_items (collection_id, position, video_id);
