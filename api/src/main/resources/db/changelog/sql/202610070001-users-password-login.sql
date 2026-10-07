ALTER TABLE users
    ADD COLUMN password_set_at      TIMESTAMPTZ,
    ADD COLUMN failed_login_count   INT         NOT NULL DEFAULT 0,
    ADD COLUMN locked_until         TIMESTAMPTZ,
    ADD COLUMN credentials_version  INT         NOT NULL DEFAULT 0;
