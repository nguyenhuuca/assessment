-- Cap count covers CONTRIBUTOR reports too (ADR-0030 D6, spec BR-11).
DROP INDEX IF EXISTS idx_app_report_autopilot_cap;
CREATE INDEX idx_app_report_autopilot_cap ON app_report (created_at)
    WHERE status = 'CREATED' AND autopilot AND reporter_role IN ('ADMIN', 'CONTRIBUTOR');
