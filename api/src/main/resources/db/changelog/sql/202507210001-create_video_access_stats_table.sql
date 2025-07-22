CREATE TABLE IF NOT EXISTS video_access_stats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id TEXT,
    hit_count INTEGER NOT NULL DEFAULT 1,
    last_accessed_at TIMESTAMP NOT NULL DEFAULT now(),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP

);