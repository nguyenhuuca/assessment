-- Schedules for AppScheduler jobs; the job code stays in Java (see ADR-0023).
CREATE TABLE scheduled_job
(
    name        VARCHAR(64) PRIMARY KEY,
    cron        VARCHAR(64) NOT NULL,
    zone        VARCHAR(64),
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    description VARCHAR(255),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- cleanup-old-videos is disabled: its old cron (30 Feb) never fired and its body is a no-op.
INSERT INTO scheduled_job (name, cron, zone, enabled, description)
VALUES ('make-poem',              '0 45 10 * * *',  'Asia/Ho_Chi_Minh', TRUE,  'Generate video descriptions'),
       ('log-stats',              '0 */5 * * * *',  NULL,               TRUE,  'Log cache hit/miss statistics'),
       ('sync-drive-videos',      '0 */15 * * * *', NULL,               TRUE,  'Sync new videos from Google Drive'),
       ('notification-digest',    '0 */15 * * * *', NULL,               TRUE,  'Send notification digest emails'),
       ('notification-retention', '0 30 3 * * *',   'Asia/Ho_Chi_Minh', TRUE,  'Purge old read notifications'),
       ('cleanup-old-videos',     '0 0 3 30 2 *',   NULL,               FALSE, 'Clean up least-accessed videos (no-op)');
