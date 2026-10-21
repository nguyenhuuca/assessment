-- Issue state pushed by the GitHub webhook (ADR-0026 D7).
ALTER TABLE app_report
    ADD COLUMN github_state        VARCHAR(10) CHECK (github_state IN ('OPEN', 'CLOSED')),
    ADD COLUMN github_state_reason VARCHAR(16),
    ADD COLUMN github_state_at     TIMESTAMPTZ;
CREATE INDEX idx_app_report_issue_number ON app_report (issue_number) WHERE issue_number IS NOT NULL;
