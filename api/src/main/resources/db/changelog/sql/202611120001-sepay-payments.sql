-- SePay payments: donations + Premium (ADR-0037, plan PAY-1). Amounts are VND as BIGINT.

-- Premium plans, editable by admins. Seeded inactive: the owner turns them on once benefits are decided.
CREATE TABLE premium_plans
(
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(32)  NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    duration_days INTEGER      NOT NULL CHECK (duration_days > 0),
    price_vnd     BIGINT       NOT NULL CHECK (price_vnd > 0),
    active        BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO premium_plans (code, name, duration_days, price_vnd, active, sort_order)
VALUES ('PREMIUM_1M', 'Premium 1 month', 30, 49000, FALSE, 1),
       ('PREMIUM_12M', 'Premium 12 months', 365, 490000, FALSE, 2);

-- One order per QR shown. The code goes into the transfer memo; SePay reports it back.
-- user_id/plan_id use SET NULL / RESTRICT so money records outlive account deletion and plan edits.
CREATE TABLE payment_orders
(
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL UNIQUE,
    type            VARCHAR(16)  NOT NULL CHECK (type IN ('DONATION', 'PREMIUM')),
    user_id         BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    plan_id         BIGINT       REFERENCES premium_plans (id) ON DELETE RESTRICT,
    expected_amount BIGINT       CHECK (expected_amount IS NULL OR expected_amount > 0),
    paid_amount     BIGINT       NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PAID', 'UNDERPAID', 'EXPIRED', 'CANCELLED', 'RESOLVED')),
    donor_name      VARCHAR(50),
    message         VARCHAR(200),
    expires_at      TIMESTAMPTZ  NOT NULL,
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- user_id is not required here: ON DELETE SET NULL must keep a deleted buyer's PREMIUM order (money record)
    CHECK (type <> 'PREMIUM' OR (plan_id IS NOT NULL AND expected_amount IS NOT NULL))
);

CREATE INDEX idx_payment_orders_status ON payment_orders (status, created_at DESC);
CREATE INDEX idx_payment_orders_user ON payment_orders (user_id, created_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX idx_payment_orders_pending_expiry ON payment_orders (expires_at) WHERE status = 'PENDING';

-- Every transfer SePay reports, matched or not. sepay_id is SePay's transaction id = idempotency key.
CREATE TABLE sepay_transactions
(
    sepay_id              BIGINT PRIMARY KEY,
    order_id              BIGINT       REFERENCES payment_orders (id) ON DELETE SET NULL,
    match_status          VARCHAR(16)  NOT NULL CHECK (match_status IN ('MATCHED', 'UNMATCHED', 'IGNORED')),
    source                VARCHAR(16)  NOT NULL CHECK (source IN ('WEBHOOK', 'RECONCILE')),
    transfer_type         VARCHAR(8)   NOT NULL,
    amount                BIGINT       NOT NULL CHECK (amount >= 0),
    code                  VARCHAR(64),
    content               VARCHAR(500),
    gateway               VARCHAR(64),
    account_number_masked VARCHAR(32),
    reference_code        VARCHAR(64),
    transaction_date      TIMESTAMPTZ,
    received_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_sepay_transactions_order ON sepay_transactions (order_id) WHERE order_id IS NOT NULL;
CREATE INDEX idx_sepay_transactions_unmatched ON sepay_transactions (received_at DESC) WHERE match_status = 'UNMATCHED';

-- Premium entitlement: active while premium_until > now(). NULL = never bought.
ALTER TABLE users ADD COLUMN premium_until TIMESTAMPTZ NULL;
