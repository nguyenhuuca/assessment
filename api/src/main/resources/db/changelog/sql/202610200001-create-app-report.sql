-- In-app reports turned into GitHub issues (ADR-0026).
CREATE TABLE app_report
(
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id   BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    reporter_role VARCHAR(20)  NOT NULL,
    type          VARCHAR(16)  NOT NULL CHECK (type IN ('BUG', 'ENHANCEMENT', 'ISSUE')),
    title         VARCHAR(120) NOT NULL,
    description   TEXT         NOT NULL,
    page          VARCHAR(32),
    page_path     VARCHAR(300),
    app_version   VARCHAR(40),
    user_agent    VARCHAR(300),
    screen_width  INT,
    auto_label    VARCHAR(16)  CHECK (auto_label IN ('AUTO', 'AUTO_MERGE')),
    autopilot     BOOLEAN      NOT NULL DEFAULT FALSE,
    labels        VARCHAR(200) NOT NULL DEFAULT '',
    status        VARCHAR(10)  NOT NULL CHECK (status IN ('CREATED', 'FAILED')),
    issue_number  INT,
    issue_url     VARCHAR(300),
    error         VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_app_report_created_at ON app_report (created_at DESC);
-- BR-3 cap count
CREATE INDEX idx_app_report_autopilot_cap ON app_report (created_at)
    WHERE status = 'CREATED' AND autopilot AND reporter_role = 'ADMIN';
