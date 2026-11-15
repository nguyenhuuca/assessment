--liquibase formatted sql
--changeset nguyenhuuca:202611150001-payment-transactions
-- Vendor-neutral payment storage (ADR-0038): sepay_transactions -> payment_transactions keyed by
-- (provider, provider_tx_id) with a surrogate id; orders remember the provider that issued them.

ALTER TABLE sepay_transactions RENAME TO payment_transactions;
ALTER TABLE payment_transactions DROP CONSTRAINT sepay_transactions_pkey;

-- BIGSERIAL fills existing rows from the new sequence
ALTER TABLE payment_transactions ADD COLUMN id BIGSERIAL;
ALTER TABLE payment_transactions ADD CONSTRAINT payment_transactions_pkey PRIMARY KEY (id);

ALTER TABLE payment_transactions ADD COLUMN provider VARCHAR(16) NOT NULL DEFAULT 'sepay';
ALTER TABLE payment_transactions ADD COLUMN provider_tx_id VARCHAR(64);
UPDATE payment_transactions SET provider_tx_id = sepay_id::text;
ALTER TABLE payment_transactions ALTER COLUMN provider_tx_id SET NOT NULL;
ALTER TABLE payment_transactions ALTER COLUMN provider DROP DEFAULT;
ALTER TABLE payment_transactions DROP COLUMN sepay_id;
ALTER TABLE payment_transactions
    ADD CONSTRAINT uq_payment_transactions_provider_tx UNIQUE (provider, provider_tx_id);

ALTER INDEX idx_sepay_transactions_order RENAME TO idx_payment_transactions_order;
ALTER INDEX idx_sepay_transactions_unmatched RENAME TO idx_payment_transactions_unmatched;
-- Second dedupe key: the same bank transfer reported under another id
CREATE INDEX idx_payment_transactions_reference ON payment_transactions (reference_code, amount)
    WHERE reference_code IS NOT NULL;

-- DEFAULT keeps old pods inserting during a rolling deploy
ALTER TABLE payment_orders ADD COLUMN provider VARCHAR(16) NOT NULL DEFAULT 'sepay';

UPDATE scheduled_job
SET name = 'reconcile-payments', description = 'Reconcile yesterday''s provider transfers (daily)'
WHERE name = 'reconcile-sepay';

--rollback UPDATE scheduled_job SET name = 'reconcile-sepay', description = 'Reconcile yesterday''s SePay transfers (daily)' WHERE name = 'reconcile-payments';
--rollback ALTER TABLE payment_orders DROP COLUMN provider;
--rollback DROP INDEX IF EXISTS idx_payment_transactions_reference;
--rollback ALTER INDEX idx_payment_transactions_unmatched RENAME TO idx_sepay_transactions_unmatched;
--rollback ALTER INDEX idx_payment_transactions_order RENAME TO idx_sepay_transactions_order;
--rollback DELETE FROM payment_transactions WHERE provider <> 'sepay' OR provider_tx_id !~ '^-?[0-9]+$';
--rollback ALTER TABLE payment_transactions ADD COLUMN sepay_id BIGINT;
--rollback UPDATE payment_transactions SET sepay_id = provider_tx_id::bigint;
--rollback ALTER TABLE payment_transactions DROP CONSTRAINT uq_payment_transactions_provider_tx;
--rollback ALTER TABLE payment_transactions DROP CONSTRAINT payment_transactions_pkey;
--rollback ALTER TABLE payment_transactions DROP COLUMN id;
--rollback ALTER TABLE payment_transactions DROP COLUMN provider;
--rollback ALTER TABLE payment_transactions DROP COLUMN provider_tx_id;
--rollback ALTER TABLE payment_transactions ADD CONSTRAINT sepay_transactions_pkey PRIMARY KEY (sepay_id);
--rollback ALTER TABLE payment_transactions RENAME TO sepay_transactions;
