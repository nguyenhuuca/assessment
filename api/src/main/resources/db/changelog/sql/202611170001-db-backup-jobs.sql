-- Scheduled DB backups to Google Drive (ADR-0039). Rows are enabled; the env switch
-- BACKUP_ENABLED (app.backup.enabled, default false) decides at run time whether a run does anything.
INSERT INTO scheduled_job (name, cron, zone, enabled, description) VALUES
  ('db-backup-daily',   '0 0 3 * * *',    'Asia/Ho_Chi_Minh', TRUE, 'DB backup to Google Drive (keep 7)'),
  ('db-backup-weekly',  '0 30 3 * * SUN', 'Asia/Ho_Chi_Minh', TRUE, 'DB backup to Google Drive (keep 4)'),
  ('db-backup-monthly', '0 0 4 1 * *',    'Asia/Ho_Chi_Minh', TRUE, 'DB backup to Google Drive (keep 12)');
