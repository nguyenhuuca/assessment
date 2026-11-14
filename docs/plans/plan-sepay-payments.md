# Plan: SePay Payments — Donations and Premium

<!--
Implementation Plan
Filename: docs/plans/plan-sepay-payments.md
Owner: Builder (/builder)
Handoff to: Builder (/swarm-execute), QA Engineer (/qa-engineer), Security Auditor (/security-auditor)
Related Skills: swarm-plan, testing
-->

## Overview

**Status:** Planned (2026-11-12)
**Author:** nguyenhuuca
**Date:** 2026-11-12
**Tracking:** `docs/tracking.md` row "SePay Payments"; `TASKS.md` section "SePay payments"
**Related PRD / ADR / Sequence:** [PRD](../prd/PRD-sepay-payments.md) · [ADR-0037](../adr/0037-sepay-bank-transfer-payments.md) · [Sequence](../sequence/sequence-sepay-payments.md)

## Objective

Guests and users donate by VietQR; logged-in users buy Premium plans that extend `users.premium_until`; SePay's bank-transfer webhook confirms payments automatically; admins see and fix everything in a Donations tab.

## Scope

### In Scope
FR-1..FR-10 of the PRD.

### Out of Scope
Payment Gateway, gating features behind Premium, refunds, auto-renewal, public supporters wall, email receipts.

## Technical Approach

### Architecture Changes
- **Backend** (`api/.../funnyapp`): entities `PremiumPlan`, `PaymentOrder`, `SepayTransaction` + repos; `users.premium_until`; `AppProperties.Sepay`; `PaymentController` (public + user), `SepayWebhookController` (permitAll, HMAC), `AdminPaymentController` (`/admin/payments/**`, ADMIN); services `PaymentOrderServiceImpl`, `SepayWebhookServiceImpl` (verify + `process(tx, source)` shared with reconcile), `SepayReconcileServiceImpl`; jobs in `AppScheduler`; `NotificationType.DONATION_RECEIVED`, `PREMIUM_PURCHASED`, `PAYMENT_UNDERPAID` in `ADMIN_TYPES` + `PaymentNotificationListener`.
- **Frontend** (`webapp/src`): `api/payments.js`, `hooks/usePayments.js`; `components/payments/DonatePage.jsx` (nav key `donate`, `SIDE_NAV` + `MOBILE_NAV` overflow), `PaymentQr.jsx`, `PremiumPlans.jsx`; Settings Premium section + badge; admin `DONATIONS` tab (`AdminPaymentsTab.jsx`); namespace `payments` (en + vi, parity test).
- Flows: see the [sequence diagrams](../sequence/sequence-sepay-payments.md).

### Key Decisions (from ADR-0037)
| Decision | Choice |
|---|---|
| Integration | Bank-transfer webhook + VietQR; transaction API v2 only for reconciliation |
| Webhook auth | HMAC-SHA256 over `{timestamp}.{raw_body}`, 300 s window, constant-time compare; IP allowlist optional |
| Idempotency | `sepay_transactions.sepay_id` PK, `ON CONFLICT DO NOTHING` |
| Money | VND `BIGINT`; one DB transaction per transfer |
| Entitlement | `users.premium_until`; extend from `max(now, premium_until)` |
| Amount rules | DONATION any; PREMIUM ≥ price → PAID (surplus kept), < price → UNDERPAID → admin resolves; amounts accumulate; EXPIRED still matches |
| Order code | prefix `SEPAY_CODE_PREFIX` (default `FM`) + 8 chars from `SecureRandom` over A-Z0-9, unique |
| Webhook response | `200 {"success":true}` for every authenticated request, incl. duplicates/unmatched |

### Config (env → `app.sepay.*`)
`SEPAY_WEBHOOK_SECRET`, `SEPAY_API_TOKEN`, `SEPAY_API_BASE_URL` (live `https://userapi.sepay.vn`, sandbox `https://userapi-sandbox.sepay.vn`), `SEPAY_BANK_ACCOUNT`, `SEPAY_BANK_CODE`, `SEPAY_ACCOUNT_NAME`, `SEPAY_CODE_PREFIX` (FM), `SEPAY_QR_BASE_URL` (`https://qr.sepay.vn/img`), `SEPAY_ORDER_TTL_HOURS` (24), `SEPAY_MIN_DONATION` (10000), `SEPAY_ALLOWED_IPS` (empty = off). The reconcile job is switched by the Harness flag `sepay_reconcile_enabled` (default off), not an env var.

**On/off switch:** Harness flag `sepay_payments_enabled` (boolean, default `false`, evaluated per user). New orders and the donate nav (`GET /payments/config`) need the bank env vars **and** the flag on. The webhook is not gated by the flag, so a transfer for an order already shown is still credited after the flag is turned off.

## Implementation Steps

Each task merges to `main` on its own (trunk-based); dependents start after the prerequisite lands. Sizes target ≤ 400 changed LOC excluding tests.

| ID | Task | Acceptance criteria | Blocked by |
|----|------|---------------------|------------|
| **PAY-1** | Liquibase `202611120001-sepay-payments.sql`: `premium_plans` (+ seed `PREMIUM_1M` 30 d, `PREMIUM_12M` 365 d, both `active=false`), `payment_orders`, `sepay_transactions`, `users.premium_until`; JPA entities, enums (`PaymentType`, `PaymentOrderStatus`, `TxMatchStatus`, `TxSource`), repositories | App boots locally with migration applied; `BeanConstructorTest` passes; repo method `insertIgnore(...)` returns 0 on duplicate (test with mock/JPA slice where available); no `ddl-auto` changes | — |
| **PAY-2** | `AppProperties.Sepay` + `application.yaml` env mapping; `PaymentOrderService` create/get; `VietQrUrlBuilder` (URL-encoded `acc, bank, amount, des, template=compact`); `PaymentController`: `GET /payments/plans` (active), `POST /payments/orders` (`@RateLimited`, `@AuditLog`; DONATION guest ok, PREMIUM needs JWT), `GET /payments/orders/{code}` (status, amount, type only — no PII); whitelist public paths in `AppConstant`; `expireStaleOrders()` job in `AppScheduler` | Unit tests: code format/uniqueness retry, min donation 400, inactive plan 400, guest PREMIUM 401, expiry job; QR URL test; `/payments/orders/{code}` unknown → 404; coverage ≥ 90% | PAY-1 |
| **PAY-3** | `SepayWebhookController` `POST /webhooks/sepay` (503 unset secret, 413 > 64 KiB, 401 bad/stale signature, optional IP allowlist) + `SepayWebhookServiceImpl.verify` and `process(tx, source)`: dedupe insert, `out` → IGNORED, code from `code` else regex on `content`, amount rules, premium extension, publish `PaymentEvent` | Tests for every branch in sequence §3 and the amount table in the plan (exact, over, under, accumulate to PAID, expired order, cancelled order, duplicate replay = no double credit, premium extension from future expiry); responds `{"success":true}`; nothing secret/PII logged | PAY-2 |
| **PAY-4** | Admin notifications: `NotificationType` `DONATION_RECEIVED`, `PREMIUM_PURCHASED`, `PAYMENT_UNDERPAID` (in `ADMIN_TYPES`); `UserRepo.findAdmins()`; `PaymentNotificationListener` (`@Async @TransactionalEventListener(AFTER_COMMIT)` → `upsertGrouped` + `NotificationPublisher.unreadChanged`); FE notification renderers + i18n | Listener test: each admin gets one notification per event; rollback publishes nothing; FE notification item renders amount (formatted VND) and order code | PAY-3 |
| **PAY-5** | Reconciliation (as built): `SepayReconcileServiceImpl` on SePay API v2 `GET /v2/transactions?q=FM&transfer_type=in&transaction_date_from=yesterday 00:00&transaction_date_sort=asc`, pages until `has_more=false` (max 10, 400 ms apart for the 3 req/s limit). Counts `webhook_success=1` rows as delivered and skips them, drops rows without the code prefix and rows dated today (no account filter, like the webhook: sandbox accounts differ). v2 ids are UUIDs, so each is stored as a stable negative 53-bit `sepay_id` (webhook ids are positive); `process` also dedupes on bank reference + amount so a transfer is never credited twice. No cursor: each run reads one whole day. Job `reconcile-sepay` daily at 00:15 Asia/Ho_Chi_Minh for the previous day T-1 (`transaction_date_from` = yesterday 00:00, rows from today dropped; `scheduled_job`, was every 10 min with a 72 h window; migration 202611140002), each run logs one INFO line `SePay reconcile run: day, result, total, webhook, reconciled, already, failed` for the Grafana Business Stats row, gated by the Harness flag `sepay_reconcile_enabled` and a non-blank `SEPAY_API_TOKEN` | Stubbed-HTTP tests: flag off / blank token = no call, paging, skips (delivered, stored, today's rows, foreign, malformed), per-run totals log line, one failure does not stop the rest, 401/429/bad JSON/unreachable = nothing processed; reference dedupe in `process` | PAY-3 |
| **PAY-6** | Admin API `/admin/payments/**` (`@HasPermission(ADMIN)`): paged orders (type/status filter), unmatched transactions, totals (sum paid by type, 30-day + all-time), `POST transactions/{sepayId}/link {orderCode}`, `POST orders/{id}/resolve {grantPremium}`, plans CRUD (`PUT/POST/PATCH active`) | Controller + service tests incl. permission test (USER → 403), link re-runs amount rules, resolve is idempotent, price must be > 0, plan code unique | PAY-3 |
| **PAY-7** | User Premium: `premium`/`premiumUntil` on login/me DTO; FE `AuthContext` keeps them; Settings "Premium" section (status, expiry, buy/renew → donate page Premium tab); Premium badge in profile | DTO test; FE tests: badge shown only when `premiumUntil > now`, Settings shows expiry date localized | PAY-1 |
| **PAY-8** | FE Donate page: nav item `donate` (icon `volunteer_activism`, desktop + mobile overflow + title lookup), tabs Donate / Premium; amount presets (20k/50k/100k/custom), name/message; `PaymentQr` (QR image, bank, account, holder, amount, memo with copy buttons, countdown); polling `refetchInterval` 3 s until PAID/EXPIRED; thank-you screen; Premium tab lists active plans (login prompt for guests); namespace `payments` en/vi | Vitest: create order → QR rendered with memo; polling stops on PAID/EXPIRED; guest sees login prompt on Premium; i18n parity test green; lint/build green | PAY-2 |
| **PAY-9** | FE admin `DONATIONS` tab (`TABS`, `TAB_QUERY`, render branch, `tabs.DONATIONS` en/vi): totals cards, orders table (filters, status badges, surplus), unmatched transfers with "link to order", resolve underpaid (ConfirmModal), plans editor | Vitest for each action calling the right endpoint and invalidating `['admin','payments']`; table uses `formatBytes`-style shared `formatVnd` helper | PAY-6 |
| **PAY-13** | Admin "Check on SePay" per order (as built): row menu on PENDING / EXPIRED / UNDERPAID orders calls `POST /admin/payments/orders/{id}/check-sepay`, which searches API v2 with `q=<order code>` from the order's creation (minus 1 h) and records undelivered transfers through the same path; no flag or age gate, only the API token. 503 when the token is missing, 502 when SePay cannot be read | Service, controller and tab tests: found / none / already stored / errors | PAY-5 |
| **PAY-10** | Ops + launch: `docs/` runbook section (SePay dashboard: link bank, webhook URL `https://canh-labs.com/api/v1/funny-app/webhooks/sepay`, HMAC auth, payment-code prefix), env/Helm secrets, Tech Strategy row for SePay, ADR-0037 → Accepted; SePay **test mode** simulate transfer end-to-end; verify QR host; prod smoke with a 10,000 VND donation; enable plans | Sandbox simulate → order PAID and admin notified; prod donation visible in admin tab; secrets not in repo/logs; tracking.md ✅ | PAY-4, PAY-5, PAY-7, PAY-8, PAY-9 |

Dependency graph:
```
PAY-1 ─┬─ PAY-2 ─┬─ PAY-3 ─┬─ PAY-4 ─┐
       │         │         ├─ PAY-5 ─┤
       │         │         └─ PAY-6 ─ PAY-9 ─┤
       │         └─ PAY-8 ─────────────────────┤
       └─ PAY-7 ───────────────────────────────┴─ PAY-10
```
Parallel after PAY-3: PAY-4, PAY-5, PAY-6 (backend) with PAY-7, PAY-8 (frontend).

## Files to Modify (main ones)
- `api/src/main/resources/db/changelog/sql/202611120001-sepay-payments.sql` (new)
- `api/.../config/AppProperties.java`, `application.yaml`, `utils/AppConstant.java` (whitelist), `filter/WebSecurityConfig.java` (only if whitelist is not enough)
- `api/.../web/{PaymentController,SepayWebhookController,AdminPaymentController}.java` (new)
- `api/.../service/{PaymentOrderService,SepayWebhookService,SepayReconcileService}` + `impl/` (new); `scheduler/AppScheduler.java`
- `api/.../enums/NotificationType.java`, `service/notification/PaymentNotificationListener.java` (new)
- `webapp/src/components/layout/AppShell.jsx`, `components/payments/*` (new), `components/admin/AdminView.jsx`, `components/admin/AdminPaymentsTab.jsx` (new), `contexts/AuthContext.jsx`, Settings view, `i18n/locales/{en,vi}/{payments,admin,shell,notifications,settings}.json`
- `.claude/rules/tech-strategy.md` (SePay row, PAY-10)

## Testing Strategy
- **Unit (JUnit 5 / Mockito):** HMAC verify vectors (computed in test from a fixed secret), amount-rule table, dedupe, expiry, reconcile paging, permission tests.
- **Frontend (Vitest):** order flow, polling stop conditions, admin actions, i18n parity.
- **Manual:** SePay test mode "simulate transfer"; replay the same webhook from the SePay log (must not double credit); prod 10,000 VND donation.
- **App boot:** run `startLocal.sh` after PAY-1/PAY-3 (no `@SpringBootTest` in CI — see BeanConstructorTest note).

## Rollback Plan
- Feature is dark until env secrets are set (webhook 503, order creation 503 when bank info missing) and plans are `active=false`.
- Rollback = unset `SEPAY_*` or revert FE nav entry; tables are additive (no destructive migration). Received transfers stay recorded.

## Risks
See PRD "Risks & Mitigations". Extra: `qr.sepay.vn/img` host is undocumented (official doc shows `vietqr.app/img`) → configurable `SEPAY_QR_BASE_URL`, verified in PAY-10.
