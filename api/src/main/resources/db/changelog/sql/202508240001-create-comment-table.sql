CREATE TABLE video_comments (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                video_id varchar(100) NOT NULL,
                                user_id VARCHAR(255) NOT NULL,
                                guest_name VARCHAR(255),
                                guest_token_hash VARCHAR(100),
                                content TEXT NOT NULL,
                                created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
                                updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
                                parent_id VARCHAR(100) NULL
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_video_comments_parent_id ON video_comments(parent_id);
CREATE INDEX IF NOT EXISTS idx_video_comments_video_created ON video_comments(video_id, created_at);