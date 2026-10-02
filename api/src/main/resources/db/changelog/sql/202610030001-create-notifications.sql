CREATE TABLE notifications (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type          VARCHAR(30)  NOT NULL,
    group_key     VARCHAR(120) NOT NULL,
    video_id      VARCHAR(100),
    comment_id    UUID,
    actor_display VARCHAR(100),
    actor_count   INT          NOT NULL DEFAULT 1,
    payload       JSONB        NOT NULL DEFAULT '{}',
    read_at       TIMESTAMPTZ,
    emailed_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_unread ON notifications (user_id, read_at, updated_at DESC);

CREATE UNIQUE INDEX uq_notifications_unread_group
    ON notifications (user_id, group_key) WHERE read_at IS NULL;

CREATE INDEX idx_notifications_digest ON notifications (emailed_at, read_at) WHERE emailed_at IS NULL;
