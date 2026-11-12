# Sequence Diagrams: SePay Payments (Donations and Premium)

- **PRD:** [PRD-sepay-payments](../prd/PRD-sepay-payments.md)
- **ADR:** [0037-sepay-bank-transfer-payments](../adr/0037-sepay-bank-transfer-payments.md)
- **Spec:** —
- **Status:** Draft (2026-11-12) — class names are the planned ones from [plan-sepay-payments](../plans/plan-sepay-payments.md)

## 1. Donation happy path (guest or user)

A visitor picks an amount, scans the VietQR, and the page flips to "thank you" once SePay reports the transfer.

```mermaid
sequenceDiagram
    autonumber
    actor U as Visitor
    participant FE as Browser (DonatePage.jsx / usePaymentOrder)
    participant PC as PaymentController
    participant PS as PaymentOrderServiceImpl
    participant DB as PostgreSQL
    participant Bank as Bank app
    participant SP as SePay
    participant WC as SepayWebhookController
    participant WS as SepayWebhookServiceImpl
    participant N as PaymentNotificationListener

    U->>FE: choose amount, optional name/message
    FE->>PC: POST /payments/orders {type: DONATION, amount}
    PC->>PS: createOrder() (@RateLimited)
    PS->>DB: insert payment_orders (code FMxxxxxxxx, PENDING, expires_at)
    PS-->>FE: {code, amount, bank info, qrUrl}
    FE-->>U: show VietQR + copy buttons
    loop every 3 s until PAID or expired
        FE->>PC: GET /payments/orders/{code}
        PC-->>FE: {status: PENDING}
    end
    U->>Bank: scan QR, transfer (memo = code)
    Bank-->>SP: incoming transfer on linked account
    SP->>WC: POST /webhooks/sepay (X-SePay-Signature, X-SePay-Timestamp)
    WC->>WS: verify HMAC(timestamp.rawBody), |now - ts| <= 300 s
    WS->>DB: insert sepay_transactions ON CONFLICT DO NOTHING
    WS->>DB: order.paid_amount += amount, status = PAID
    WC-->>SP: 200 {"success": true}
    WS-)N: PaymentPaidEvent (AFTER_COMMIT, @Async)
    N->>DB: upsert admin notification DONATION_RECEIVED
    FE->>PC: GET /payments/orders/{code}
    PC-->>FE: {status: PAID}
    FE-->>U: thank-you screen
```

## 2. Premium purchase, including underpaid

```mermaid
sequenceDiagram
    autonumber
    actor U as Logged-in user
    participant FE as Browser (PremiumPlans / usePaymentOrder)
    participant PC as PaymentController
    participant PS as PaymentOrderServiceImpl
    participant WS as SepayWebhookServiceImpl
    participant DB as PostgreSQL
    actor A as Admin

    U->>FE: pick plan (e.g. 12 months)
    FE->>PC: POST /payments/orders {type: PREMIUM, planId} (JWT)
    PC->>PS: createOrder() — 401 if guest, 400 if plan inactive
    PS->>DB: insert order (expected_amount = plan.price)
    PS-->>FE: {code, amount, qrUrl}
    Note over U,WS: user transfers; webhook arrives as in flow 1
    WS->>DB: paid_amount += amount
    alt paid_amount >= expected_amount
        WS->>DB: status = PAID; users.premium_until = max(now, premium_until) + days
        Note right of WS: surplus = paid - expected, shown in admin tab
    else paid_amount < expected_amount
        WS->>DB: status = UNDERPAID
        WS-)A: notification PAYMENT_UNDERPAID
        A->>PC: POST /admin/payments/orders/{id}/resolve {grantPremium: true|false}
        PC->>DB: status = RESOLVED (+ premium extension if granted)
    end
    FE->>PC: GET /auth/me
    PC-->>FE: {premium: true, premiumUntil}
```

## 3. Webhook rejection and edge cases

```mermaid
sequenceDiagram
    autonumber
    participant SP as SePay (or attacker)
    participant WC as SepayWebhookController
    participant WS as SepayWebhookServiceImpl
    participant DB as PostgreSQL

    SP->>WC: POST /webhooks/sepay
    alt secret not configured
        WC-->>SP: 503
    else body > 64 KiB
        WC-->>SP: 413
    else bad signature or stale timestamp
        WC-->>SP: 401 (fixed log line, no payload logged)
    else duplicate sepay id (retry / replay)
        WS->>DB: insert … ON CONFLICT DO NOTHING (0 rows)
        WC-->>SP: 200 {"success": true} (no-op)
    else transferType = out
        WS->>DB: insert match_status = IGNORED
        WC-->>SP: 200 {"success": true}
    else no code / unknown code / CANCELLED order
        WS->>DB: insert match_status = UNMATCHED
        WC-->>SP: 200 {"success": true}
    end
```

## 4. Reconciliation job (missed webhooks)

```mermaid
sequenceDiagram
    autonumber
    participant J as AppScheduler (every 10 min)
    participant R as SepayReconcileServiceImpl
    participant API as SePay userapi v2
    participant WS as SepayWebhookServiceImpl
    participant DB as PostgreSQL

    J->>R: reconcile()
    R->>DB: read last since_id cursor
    loop pages (per_page=100, asc) — respect 3 req/s
        R->>API: GET /v2/transactions?since_id=&transfer_type=in (Bearer token)
        API-->>R: data[], pagination
        R->>WS: process(tx, source = RECONCILE) — same path as webhook
        WS->>DB: insert ON CONFLICT DO NOTHING; match if new
    end
    R->>DB: save new cursor
    alt 429 / 5xx
        R-->>J: stop, keep cursor, retry next run
    end
```

## 5. Order expiry job

```mermaid
sequenceDiagram
    autonumber
    participant J as AppScheduler (every 15 min)
    participant PS as PaymentOrderServiceImpl
    participant DB as PostgreSQL
    J->>PS: expireStaleOrders()
    PS->>DB: UPDATE payment_orders SET status = EXPIRED WHERE status = PENDING AND expires_at < now()
    Note over PS,DB: a late transfer still matches an EXPIRED order (money received) and moves it to PAID / UNDERPAID
```
