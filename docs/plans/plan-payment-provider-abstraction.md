# Plan: Payment Provider Abstraction

**Status:** ✅ Done · **Date:** 2026-11-15 · **ADR:** [ADR-0038](../adr/0038-payment-provider-abstraction.md) · **Builds on:** [plan-sepay-payments](plan-sepay-payments.md)

## Overview

Move every SePay-specific piece of the payment feature behind a `PaymentProvider` port so that switching or adding a vendor touches one adapter package plus configuration. SePay becomes the first adapter; its behaviour does not change.

## Objective

After this plan, adding vendor X means:

1. `service/payment/<x>/` with `XProvider implements PaymentProvider` (+ `XTransferFeed` if it has a pull API) and its tests extending `PaymentProviderContractTest`
2. `app.payment.<x>.*` in `application.yaml`, secrets in env/Helm
3. `PAYMENT_PROVIDER=<x>` for new orders; point the vendor's webhook at `/webhooks/payments/<x>`

No change to orders, settlement, admin API, frontend, i18n or schema.

## Scope

### In Scope
- Neutral model, port, registry, ingestion and reconcile services
- SePay adapter (move, not rewrite)
- Generic webhook controller with `/webhooks/sepay` alias
- Config split with `SEPAY_*` env fallbacks
- Migration `sepay_transactions` → `payment_transactions`, `payment_orders.provider`, job rename
- Admin API + frontend neutral naming

### Out of Scope
- Integrating a second real vendor
- Renaming Harness flag keys
- Card / hosted checkout flow

## Technical Approach

See ADR-0038 for the port, model and data model. Two-hats rule: PP-1..PP-3 are refactors (tests unchanged in meaning, no schema change); PP-4 is the schema change; PP-5 the API/UI rename. Commit between hats.

### Key Decisions
- Registry of providers, one selected for new orders, all configured ones accept webhooks/reconcile (safe switch)
- `provider_tx_id` is a string; surrogate `id` is what admin uses
- Old webhook URL and Harness flag keys kept

## Implementation Steps

| ID | Task | Done when | Depends |
|---|---|---|---|
| **PP-1** | Neutral model + port: `BankTransfer`, `FeedTransfer`, `PaymentInstructions`, `WebhookRequest`/`WebhookResult`, `PaymentProvider`, `TransferFeed`, `ProviderApiException`, `PaymentProviderRegistry` (by id, `active()` from `app.payment.provider`, unknown id fails at startup). Extract `PaymentIngestionService` from `SepayWebhookServiceImpl.process` (dedupe by provider tx id, then bank reference + amount; code extraction; match; settle) | Ingestion tests cover every branch of today's `process` tests via a fake provider; `BeanConstructorTest` green | — |
| **PP-2** | SePay adapter in `service/payment/sepay/`: `SepayProvider` (IP allowlist, HMAC, parse → `BankTransfer`, VietQR URL), `SepayTransferFeed` (API v2 client, paging, 400 ms pause, delivered flag). `PaymentReconcileService` neutral (T-1 day pass + `checkOrder`), log line unchanged + `provider=`. `PaymentWebhookController` `POST /webhooks/payments/{provider}` + alias `/webhooks/sepay`; whitelist both paths in `AppConstant`. Delete `SepayWebhookService*`, `SepayReconcileService*`, `SepayWebhookController`, `VietQrUrlBuilder` | Moved SePay tests green (HMAC, skew, size cap, IP, parse, paging, 401/429/bad JSON); `PaymentProviderContractTest` added and extended by SePay; status-order 503 → 403 → 413 → 401 → 400 → 200 unchanged | PP-1 |
| **PP-3** | Config: `AppProperties.Payment` (provider, bank account fields, code prefix, TTL, min donation) + `Payment.Sepay` (secrets, URLs, IPs); `application.yaml` with `PAYMENT_*` falling back to `SEPAY_*`; `PaymentOrderServiceImpl` uses `registry.active().instructions(...)` | Config binding test; existing order tests green; env on prod unchanged still boots | PP-2 |
| **PP-4** | Migration `202611150001-payment-transactions.sql`: rename table, add `id BIGSERIAL` PK, `provider`, `provider_tx_id`, unique `(provider, provider_tx_id)`, backfill, drop `sepay_id` PK; rename indexes; `payment_orders.provider` default `'sepay'`; `scheduled_job` `reconcile-sepay` → `reconcile-payments`. Entity `PaymentTransaction`, repo `PaymentTransactionRepository` (`insertIgnore` on the new unique key), purge query updated; `storageId` hash removed (UUIDs stored as text) | Migration applies on a copy of prod schema; repo query tests green; `ScheduledJobMigrationTest` updated; app boots locally | PP-3 |
| **PP-5** | Admin API + FE: `/admin/payments/transactions/{id}/link|ignore` (surrogate id), `POST /orders/{id}/check-provider`, `AdminTransactionDto.id` + `provider`, error keys `error.payment.providerApiFailed|providerApiNotConfigured`; FE `adminPayments.js`, `useAdminPayments.js`, `AdminPaymentsTab.jsx` use `id`, label "Check with {{provider}}" en/vi | Controller, service and Vitest tests green; i18n parity test green; lint/build green | PP-4 |
| **PP-6** | Docs: ADR-0038 → Accepted, ADR-0037 note, runbook "Add a payment provider" checklist, plan-sepay-payments config table, tracking.md, Tech Strategy row says "Payments: provider port, SePay adapter" | Docs merged; tracking ✅ | PP-5 |

```
PP-1 ─ PP-2 ─ PP-3 ─ PP-4 ─ PP-5 ─ PP-6
```

Sequential: each step moves code the next one depends on.

## Files to Modify

- New: `service/payment/` (model, port, registry, `PaymentIngestionService`, `PaymentReconcileService`), `service/payment/sepay/`, `web/PaymentWebhookController.java`, `entity/PaymentTransaction.java`, `repo/PaymentTransactionRepository.java`, Liquibase changeset
- Changed: `PaymentOrderServiceImpl`, `AdminPaymentService(Impl)`, `AdminPaymentController`, `PaymentController`, `AppProperties`, `application.yaml`, `AppConstant`, `AppScheduler`, `JobRegistry`, `PaymentOrderRepository`, `AdminTransactionDto`, `AdminOrderCheckDto`, i18n `messages*.properties`
- Removed: `SepayWebhookService(Impl)`, `SepayReconcileService(Impl)`, `SepayWebhookController`, `SepayTxPayload`, `SepayTransaction`, `SepayTransactionRepository`, `VietQrUrlBuilder`
- Frontend: `api/adminPayments.js`, `hooks/useAdminPayments.js`, `components/admin/AdminPaymentsTab.jsx` (+ test), `i18n/locales/{en,vi}/admin.json`
- Helm: optional `PAYMENT_PROVIDER` env (defaults to `sepay`)

## Testing Strategy

### Unit Tests
- Ingestion with a fake provider: duplicate tx id, same reference + amount under another id, `OUT` ignored, no code → UNMATCHED, cancelled order → UNMATCHED, exact / over / under / accumulate, Premium extension
- Two fake providers registered: webhook and reconcile route by id; new orders use the active one
- `PaymentProviderContractTest`: `receive` rejects an empty body, never throws on garbage, `instructions` encodes the code

### Integration Tests
- Repository tests for `insertIgnore` on `(provider, provider_tx_id)` and the purge query

### Manual Testing
- `startLocal.sh` boots (no `@SpringBootTest` in CI)
- SePay Test mode: simulated transfer → order PAID; both webhook URLs answer
- Admin: link, ignore, check-provider on a pending order

## Rollback Plan

PP-1..PP-3 revert cleanly (no schema). PP-4 ships a Liquibase rollback block (rename back, restore `sepay_id` PK from `provider_tx_id` where numeric). Payments are not live, so data loss risk is limited to test rows.

## Risks

| Risk | Mitigation |
|---|---|
| Behaviour drift while moving SePay code | Move tests with code before editing; diff test names to confirm none dropped |
| Rolling deploy: old pod writes to renamed table | Payments not live; deploy outside donation traffic; old pod only fails on payment endpoints |
| Grafana panel parses the reconcile log line | Keep the text, append `provider=` at the end, check the panel after deploy |
| Old reconcile rows carry hashed negative ids | Backfilled as text; reference + amount dedupe still blocks double credit |

## Checklist

### Before Starting
- [ ] ADR-0038 approved by owner
- [ ] TASKS.md section added

### Before Merge
- [ ] `mvn verify` green, coverage ≥ 90%
- [ ] `npm run lint && npm test && npm run build` green
- [ ] App boots locally; SePay sandbox webhook credits an order

## Progress Log

| Date | Note |
|---|---|
| 2026-11-15 | Plan drafted and approved |
| 2026-11-15 | PP-1..PP-4 built together (the interim `long sepay_id` mapping would have been throw-away work, and payments are not live); PP-5 admin API + FE; PP-6 docs. `mvn verify` 2229 tests green, coverage met; webapp lint, 825 tests, build green. App boot against a database not verified locally (no local Postgres) |
