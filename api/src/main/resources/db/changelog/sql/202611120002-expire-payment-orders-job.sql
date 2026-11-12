-- Flip overdue PENDING payment orders to EXPIRED every 15 minutes (ADR-0037).
INSERT INTO scheduled_job (name, cron, zone, enabled, description)
VALUES ('expire-payment-orders', '0 */15 * * * *', NULL, TRUE, 'Expire overdue payment orders');
