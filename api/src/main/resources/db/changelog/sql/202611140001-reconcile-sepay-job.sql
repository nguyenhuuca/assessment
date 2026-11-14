-- Recover SePay transfers whose webhook was lost, every 10 minutes (ADR-0037, PAY-5).
-- Enabled here; the Harness flag sepay_reconcile_enabled decides at run time whether a pass calls SePay.
INSERT INTO scheduled_job (name, cron, zone, enabled, description)
VALUES ('reconcile-sepay', '0 */10 * * * *', NULL, TRUE, 'Recover missed SePay webhooks');
