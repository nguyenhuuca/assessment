-- Reconcile SePay once a day at 00:15 Vietnam time for the previous day (T-1), instead of every 10 minutes (PAY-5).
UPDATE scheduled_job
SET cron = '0 15 0 * * *', zone = 'Asia/Ho_Chi_Minh', description = 'Reconcile yesterday''s SePay transfers (daily)'
WHERE name = 'reconcile-sepay';
