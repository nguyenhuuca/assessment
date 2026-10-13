CREATE TABLE video_collections (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(60)  NOT NULL,
    visibility  VARCHAR(10)  NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PRIVATE', 'PUBLIC')),
    is_default  BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_video_collections_default_private CHECK (NOT is_default OR visibility = 'PRIVATE')
);
CREATE UNIQUE INDEX uq_video_collections_user_name    ON video_collections(user_id, lower(name));
CREATE UNIQUE INDEX uq_video_collections_public_name  ON video_collections(lower(name)) WHERE visibility = 'PUBLIC';
CREATE UNIQUE INDEX uq_video_collections_user_default ON video_collections(user_id) WHERE is_default;
CREATE INDEX idx_video_collections_public ON video_collections(updated_at DESC) WHERE visibility = 'PUBLIC';

CREATE TABLE video_collection_items (
    collection_id UUID        NOT NULL REFERENCES video_collections(id) ON DELETE CASCADE,
    video_id      BIGINT      NOT NULL REFERENCES video_sources(id)     ON DELETE CASCADE,
    added_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (collection_id, video_id)
);
CREATE INDEX idx_video_collection_items_video ON video_collection_items(video_id);
