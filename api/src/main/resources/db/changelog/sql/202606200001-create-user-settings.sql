CREATE TABLE user_settings (
    user_id             BIGINT      PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    notify_new_content  BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_email        BOOLEAN     NOT NULL DEFAULT TRUE,
    default_quality     VARCHAR(10) NOT NULL DEFAULT 'AUTO',
    incognito_enabled   BOOLEAN     NOT NULL DEFAULT FALSE,
    profile_private     BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
