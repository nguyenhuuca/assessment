# ADR-0037: SePay Bank-Transfer Webhook (VietQR) for Donations and Premium

## Metadata

**Status:** Proposed · **Date:** 2026-11-12 · **Deciders:** nguyenhuuca · **Tags:** payments, integration, security, data-model
**Related PRD:** [PRD-sepay-payments](../prd/PRD-sepay-payments.md) · **Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** ⚠️ Adds a new external integration (SePay) — not in the Golden Paths "External Integrations" table; the table must gain a row "Payments | SePay bank-transfer webhook" when this ADR is accepted. All internals follow the Golden Path (Spring Boot, JPA, Liquibase, `@Scheduled` in `AppScheduler`, React Query).

---

## Context

The app has no way to receive money (PRD-sepay-payments). The owner wants donations from guests and users, plus a paid Premium tier with an expiry. Users are Vietnamese and pay mostly by bank transfer / VietQR.

SePay offers two integration styles:
- **Bank-transfer webhook:** SePay watches a linked bank account and POSTs every incoming transfer; the app matches it to an order by a payment code in the memo. No merchant onboarding.
- **Payment Gateway:** create an order, POST a signed form to `pay.sepay.vn`, receive an IPN. Supports cards/NAPAS; needs gateway activation, IPN retry/signature behaviour is not documented.

Money handling is hard to undo once users have paid, so the data model and the trust boundary are a one-way door.

---

## Decision Drivers

- Works for guests without accounts and without card details
- No merchant onboarding for the MVP
- Never double-credit; never lose a received transfer
- Forged notifications must not grant Premium
- Fits the existing webhook pattern (`GitHubWebhookController`: HMAC, permitAll, size cap, 503 when unset)

---

## Considered Options

### Option A — Bank-transfer webhook + VietQR (chosen)
App creates an order with a code, shows a VietQR (`acc`, `bank`, `amount`, `des=<code>`); SePay webhook with **HMAC-SHA256** auth (`X-SePay-Signature: sha256=<hex>` over `{timestamp}.{raw_body}`, `X-SePay-Timestamp`, 300 s window).
- ✅ No onboarding, works for guests, same pattern as the GitHub webhook
- ✅ Every transfer stored, even unmatched → nothing lost
- ❌ Users can break the memo → unmatched transfers need admin linking
- ❌ Bank transfer only (no cards)

### Option B — SePay Payment Gateway (checkout + IPN)
- ✅ Cards/NAPAS, hosted checkout, order object on SePay side
- ❌ Merchant activation; IPN signature/retry undocumented (research 2026-11-12); redirect flow heavier on mobile
- ❌ Two secrets and a signed form flow for a small donation feature

### Option C — Poll the transaction API only (no webhook)
- ✅ No public endpoint
- ❌ Latency = poll interval; 3 req/s rate limit; thank-you screen not "realtime"

Webhook auth sub-options: API key header (`Authorization: Apikey …`) is a static bearer secret replayable forever if leaked; **HMAC with timestamp** is chosen. IP allowlist is optional defence-in-depth (config, off by default) because the VM sits behind a proxy and SePay's IP list can change.

---

## Decision Outcome

**Option A**, plus the Option C API as a **reconciliation** job to recover webhooks missed beyond SePay's ~33 min retry window. As built (PAY-5): API v2 searched by the code prefix, once a day at 00:15 Asia/Ho_Chi_Minh over the previous day (T-1); v2 has UUID ids, so there is no `since_id` cursor. Option B can be added later as another order "channel" without changing the order model.

### Data model (Liquibase)
- `premium_plans(id, code UNIQUE, name, duration_days, price_vnd BIGINT, active, sort_order, timestamps)` — admin-editable.
- `payment_orders(id, code UNIQUE, type DONATION|PREMIUM, user_id NULL, plan_id NULL, expected_amount BIGINT NULL, paid_amount BIGINT DEFAULT 0, status PENDING|PAID|UNDERPAID|EXPIRED|CANCELLED|RESOLVED, donor_name, message, expires_at, paid_at, timestamps)`.
- `sepay_transactions(sepay_id BIGINT PRIMARY KEY, order_id NULL FK, match_status MATCHED|UNMATCHED|IGNORED, amount BIGINT, transfer_type, code, content, gateway, account_number_masked, reference_code, transaction_date, source WEBHOOK|RECONCILE, received_at)` — the PK is the idempotency key (`INSERT … ON CONFLICT DO NOTHING`).
- `users.premium_until TIMESTAMPTZ NULL` — entitlement = `premium_until > now()`; extension = `max(now, premium_until) + days`.

Amounts are VND `BIGINT`. Transfer insert, order update and premium extension run in one transaction; notifications fire `AFTER_COMMIT`.

### Consequences
**Positive:** guests can donate; no gateway onboarding; replay-safe; every received transfer is auditable.
**Negative:** unmatched transfers need manual linking; Premium renewal is manual (no auto-debit); a new external dependency and three new secrets to operate.
**Neutral:** Premium grants no benefits until a follow-up decides them.

---

## Validation
- Unit tests: signature verify (valid, wrong secret, stale timestamp, missing header), dedupe, amount rules (exact, over, under, accumulate, expired order).
- SePay test mode "simulate transfer" end-to-end before prod; prod smoke with a 10,000 VND donation.

## Links
- SePay webhooks: https://docs.sepay.vn/tich-hop-webhooks.html · HMAC auth: https://developer.sepay.vn/en/sepay-webhooks/xac-thuc
- VietQR image: https://developer.sepay.vn/en/tien-ich-khac/tao-qr-code · Transaction API v2: https://developer.sepay.vn/vi/sepay-api/v2/giao-dich/danh-sach
- Pattern: `api/.../web/GitHubWebhookController.java`, `GitHubWebhookServiceImpl.java`

## Changelog
| Date | Change |
|------|--------|
| 2026-11-12 | Proposed (/swarm-plan) |
| 2026-11-14 | Reconciliation as built: API v2 by code prefix, daily at 00:15 for day T-1 (was every 10 min, `since_id`) |
