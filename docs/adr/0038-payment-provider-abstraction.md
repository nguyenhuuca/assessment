# ADR-0038: Payment Provider Abstraction (SePay as the first adapter)

## Metadata

**Status:** Accepted · **Date:** 2026-11-15 · **Deciders:** nguyenhuuca · **Tags:** payments, integration, architecture, data-model
**Related:** [ADR-0037](0037-sepay-bank-transfer-payments.md) · [Plan](../plans/plan-payment-provider-abstraction.md) · **Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** No new technology. Ports and adapters inside the existing layered Spring app; Liquibase migration; no new dependency.

---

## Context

ADR-0037 built donations and Premium on SePay. SePay is one vendor among several that do the same job for Vietnamese bank transfers (Casso, PayOS, a bank's own open API, or a card gateway later). SePay Live does not accept the owner's personal VCB account yet (session blocker 2026-11-14), so a vendor change is a real possibility.

Today SePay is wired through the whole payment feature:

| Area | SePay coupling today |
|---|---|
| Domain logic | `SepayWebhookServiceImpl.process()` holds the vendor-neutral rules (dedupe, code extraction, order match, settlement) next to SePay HMAC and JSON parsing |
| Ingestion model | `SepayTxPayload` (numeric `long id`), `TxSource` |
| Database | table `sepay_transactions`, PK `sepay_id BIGINT`; reconcile v2 UUIDs are squeezed into a negative 53-bit hash to fit that PK |
| Order creation | `PaymentOrderServiceImpl` reads `AppProperties.Sepay` and calls `VietQrUrlBuilder` (SePay QR host) |
| Reconcile | `SepayReconcileServiceImpl` mixes the SePay HTTP client with the neutral "send undelivered transfers through ingestion, tally, log" loop |
| Admin | `AdminPaymentService.linkTransaction(long sepayId)`, `checkOrderOnSepay`, `/orders/{id}/check-sepay`, error keys `error.payment.sepay*` |
| Frontend | `sepayId` fields, `checkOrderOnSepay`, i18n `pay.checkSepay` |
| Config | `app.sepay.*` mixes our bank account and business rules (code prefix, TTL, min donation) with SePay secrets |
| Ops | job `reconcile-sepay`, Harness flags `sepay_payments_enabled`, `sepay_reconcile_enabled`, webhook path `/webhooks/sepay` |

Changing vendor today touches every row above: about 25 backend files, 6 frontend files, a migration and both i18n bundles.

---

## Decision Drivers

- A vendor change touches one adapter package plus configuration: no change to orders, settlement, admin, frontend or schema
- No behaviour change for SePay: same HMAC rules, same dedupe, same amount rules, same Grafana log line
- Two providers can run side by side during a switch: orders created on the old one still get late webhooks and reconcile
- Never double-credit, never lose a transfer (unchanged from ADR-0037)
- Payments are not live yet (blocked on the bank account), so the schema rename is cheap now and expensive after launch

---

## Considered Options

### Option 1: Ports and adapters with a provider registry (chosen)

A small `PaymentProvider` port with three capabilities, a vendor-neutral transfer model, neutral services, and one adapter package per vendor. A `PaymentProviderRegistry` maps provider id to adapter; `app.payment.provider` selects the one used for new orders, and every configured adapter keeps accepting webhooks and reconcile.

- ✅ New vendor = new package + config; core and frontend untouched
- ✅ Side-by-side providers make a switch safe for in-flight orders
- ✅ Neutral core is testable without HTTP or HMAC
- ❌ One-time refactor and a table rename now

### Option 2: Keep SePay classes, add `if (provider == X)` branches later

- ✅ No work now
- ❌ Every vendor adds branches across services, schema and UI; the negative-hash id trick repeats for any vendor with string ids

### Option 3: Abstract only the HTTP clients (thin wrapper)

- ✅ Small change
- ❌ Webhook auth, payload shape, id type and QR rules are the parts that actually differ between vendors; the table and admin API stay SePay-shaped

---

## Decision Outcome

**Option 1.**

### Port

```java
public interface PaymentProvider {
    String id();                                    // "sepay"; stored on orders and transactions
    boolean isConfigured();                         // secrets present
    PaymentInstructions instructions(String orderCode, long amountVnd);   // QR URL + bank fields shown to the payer
    WebhookResult receive(WebhookRequest request);  // auth + parse; no DB access
    Optional<TransferFeed> feed();                  // empty when the vendor has no pull API
}

public interface TransferFeed {                     // reconcile / "check order now"
    List<FeedTransfer> fetch(String query, Instant from, Instant to);   // throws ProviderApiException
}
```

- `WebhookRequest(Map<String,String> headers, byte[] body, String remoteAddr)`; `WebhookResult` is `Accepted(List<BankTransfer>, String ackBody)` or `Rejected(int httpStatus)`. The controller maps it to HTTP and never knows HMAC or JSON fields.
- `BankTransfer(provider, providerTxId (String), direction IN/OUT, amount, code, content, bank, accountNumber, referenceCode, transactionDate)`. `providerTxId` is a string, so SePay numeric ids and v2 UUIDs fit as they are.
- `FeedTransfer(BankTransfer transfer, boolean deliveredByWebhook)`.

### Neutral core (no vendor names)

| Component | Was | Responsibility |
|---|---|---|
| `PaymentIngestionService` | `SepayWebhookServiceImpl.process` | idempotent store, code extraction, order match, `PaymentSettlement.apply` |
| `PaymentReconcileService` | loop in `SepayReconcileServiceImpl` | daily T-1 pass and per-order check over every provider with a feed; tally and the Grafana log line |
| `PaymentSettlement` | unchanged | amount rules, Premium |
| `PaymentWebhookController` | `SepayWebhookController` | `POST /webhooks/payments/{provider}`; 404 unknown provider, 413 over 64 KiB, then the provider decides (SePay: 503 no secret, 403 IP, 401 signature, 400 payload) |

### SePay adapter (`service/payment/sepay/`)

`SepayProvider` (HMAC verify, IP allowlist, webhook JSON, VietQR URL), `SepayTransferFeed` (API v2 client, paging, rate limit), `SepayProperties`. All current SePay rules move here unchanged.

### Data model

`sepay_transactions` → `payment_transactions`: surrogate `id BIGSERIAL` PK, `provider VARCHAR(16)`, `provider_tx_id VARCHAR(64)`, `UNIQUE (provider, provider_tx_id)`; other columns kept. Existing rows: `provider='sepay'`, `provider_tx_id = sepay_id::text`. `payment_orders` gains `provider VARCHAR(16) NOT NULL DEFAULT 'sepay'` so an order is checked against the provider that issued it. The negative-hash `storageId` goes away for new rows.

### Configuration

```yaml
app:
  payment:
    provider: ${PAYMENT_PROVIDER:sepay}        # used for new orders
    bank-account / bank-code / account-name    # our receiving account, not a vendor setting
    code-prefix / order-ttl-hours / min-donation
    sepay:                                     # adapter-only
      webhook-secret / api-token / api-base-url / qr-base-url / allowed-ips
```

Existing `SEPAY_*` env vars stay valid as fallbacks (`${PAYMENT_BANK_ACCOUNT:${SEPAY_BANK_ACCOUNT:}}`), so no secret has to change at deploy.

### Compatibility kept on purpose

- `POST /webhooks/sepay` stays as an alias of `/webhooks/payments/sepay`: the URL is configured in the SePay dashboard.
- Harness flag keys stay (`sepay_payments_enabled`, `sepay_reconcile_enabled`); only the Java constants get neutral names. Renaming a key needs the owner in Harness and gains nothing.
- The reconcile log line keeps its exact text (`SePay reconcile run: day=…`): the prefix is the provider display name, so the Grafana panel keeps working.

### Quantified Impact

| Change | Today | After |
|---|---|---|
| Files touched to add or switch a vendor | ~25 BE + 6 FE + migration + i18n | 1 adapter package (3–4 classes + tests) + config + env |
| Schema change for a new vendor | yes | none |
| Frontend change for a new vendor | yes | none (provider name comes from the API) |

### Adding or switching a provider

1. `service/payment/<vendor>/`: `<Vendor>Provider implements PaymentProvider` (`@Component`), plus a `TransferFeed` if the vendor has a pull API. Return `BankTransfer` with the vendor's own transaction id as a string.
2. Test class extends `PaymentProviderContractTest`; add the vendor's own webhook/parse tests.
3. `app.payment.<vendor>.*` in `AppProperties` + `application.yaml`; secrets as env/Helm values.
4. Point the vendor's webhook at `/api/v1/funny-app/webhooks/payments/<vendor>`.
5. `PAYMENT_PROVIDER=<vendor>` for new orders. Keep the old adapter registered until its last orders expire, so late webhooks and reconcile still settle them.

No change to orders, settlement, admin API, frontend, i18n or schema.

---

## Consequences

### Positive
- Vendor switch or a second vendor (e.g. a card gateway) is additive.
- Core payment rules get tests that need no HMAC or HTTP stubs.
- String transaction ids remove the negative-hash workaround.

### Negative
- One refactor of a feature that just shipped (tests must move with the code).
- Table rename during a rolling deploy: an old pod writing to `sepay_transactions` fails for the seconds both pods run. Acceptable because payments are not live.

### Neutral
- A pull-only vendor (no webhook) is supported: `receive` returns `Rejected(404)` and the reconcile feed does the work.
- A hosted-checkout gateway (redirect + IPN) fits the same port: `instructions` returns a redirect URL instead of a QR.

---

## Validation

- All existing SePay tests pass after moving (behaviour unchanged), plus a reusable `PaymentProviderContractTest` every adapter extends.
- A fake second adapter in tests proves the core and admin work with two providers at once.
- Sandbox webhook on SePay Test mode still credits an order end to end; reconcile log line still parsed by Grafana.

---

## Links

- [ADR-0037 SePay bank-transfer payments](0037-sepay-bank-transfer-payments.md)
- [Plan: payment provider abstraction](../plans/plan-payment-provider-abstraction.md)

---

## Changelog

| Date | Change |
|---|---|
| 2026-11-15 | Proposed |
| 2026-11-15 | Accepted and built (PP-1..PP-6); webhook check order and log line wording updated to match the code |
