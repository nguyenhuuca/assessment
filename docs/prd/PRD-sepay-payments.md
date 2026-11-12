# PRD: SePay Payments — Donations and Premium

## Overview

**Status:** Draft · **Author:** nguyenhuuca · **Date:** 2026-11-12 · **Version:** 0.1
**Tracking:** `docs/tracking.md` row "SePay Payments"; `TASKS.md` section "SePay payments"
**ADR:** [0037-sepay-bank-transfer-payments](../adr/0037-sepay-bank-transfer-payments.md) · **Sequence:** [sequence-sepay-payments](../sequence/sequence-sepay-payments.md) · **Plan:** [plan-sepay-payments](../plans/plan-sepay-payments.md)
**PR-FAQ:** — (Medium feature) · **Stakeholders:** owner/admin

## Problem Statement

Funny Movies runs on a paid VM and has no way to receive money. Viewers who want to support the app cannot, and there is no paid tier to fund hosting. Vietnamese users pay mostly by bank transfer / VietQR; card gateways need merchant onboarding.

### Evidence
- No payment, order or entitlement code exists in the repo (explore 2026-11-12); `PRD-user-settings.md` deferred billing to "v2+".
- `app.subscriptionStatusEnabled` exists only as a display flag.

## Goals & Success Metrics

| Goal | Metric |
|------|--------|
| Anyone can donate by VietQR | Donation confirmed on screen ≤ 10 s after SePay webhook, for ≥ 99% of matched transfers |
| Logged-in users can buy Premium | Premium active automatically after a correct transfer, no admin action |
| Admin sees every incoming transfer | 100% of SePay webhooks stored (matched or not); missed webhooks recovered by reconciliation within 15 min |
| No double credit | 0 duplicate credits (dedupe on SePay transaction id) |

## User Stories

1. As a **guest or user**, I pick an amount, scan a VietQR and see a thank-you screen once the money arrives.
2. As a **user**, I choose a Premium plan (e.g. 1 month / 12 months), pay by VietQR and my account shows Premium with an expiry date.
3. As a **Premium user**, I renew early and the new days are added after my current expiry.
4. As an **admin**, I get a notification for each donation / Premium purchase.
5. As an **admin**, I see orders and transfers in a Donations tab, fix underpaid or unmatched transfers, and edit plan prices without a deploy.

## Requirements

### Functional
- **FR-1** Create a payment order: type `DONATION` (guest or user, free amount ≥ 10,000 VND, optional display name ≤ 50 chars and message ≤ 200 chars) or `PREMIUM` (login required, active plan). Server returns order code, amount, bank info and VietQR image URL. Order expires after 24 h (configurable).
- **FR-2** Order code: unguessable, prefix configured to match SePay's payment-code pattern (e.g. `FM` + 8 chars A-Z0-9), placed in the transfer memo.
- **FR-3** SePay webhook endpoint verifies HMAC-SHA256 signature + timestamp, stores every incoming transfer once (dedupe by SePay `id`), and replies `200 {"success":true}`.
- **FR-4** Matching: code from SePay `code`, else regex on `content`. Amounts accumulate per order.
  - DONATION: any matched amount → `PAID`.
  - PREMIUM: paid ≥ price → `PAID`, Premium extended by plan days from `max(now, premium_until)`; surplus is recorded. Paid < price → `UNDERPAID` (admin resolves).
  - Transfer to an `EXPIRED` order still matches (money already received). `CANCELLED` orders do not match.
  - No code / unknown code / outgoing transfer → stored `UNMATCHED` / `IGNORED`.
- **FR-5** Order status endpoint (by code) for polling; the donate page switches to a thank-you screen on `PAID`.
- **FR-6** Admin notification (in-app, existing notification system) on `PAID` and `UNDERPAID`.
- **FR-7** Admin Donations tab: orders (filter by type/status), unmatched transfers, totals; actions: link an unmatched transfer to an order, mark an underpaid order resolved (grant Premium or keep as donation), CRUD Premium plans (name, days, price, active).
- **FR-8** User API exposes `premium` (bool) and `premiumUntil`; Settings shows Premium status and a buy/renew button; a Premium badge shows in the profile. No feature is gated yet (entitlement mechanism only).
- **FR-9** Reconciliation job polls SePay transaction API v2 (`since_id`) every 10 min and feeds missing transfers through the same matching path.
- **FR-10** Scheduled job marks `PENDING` orders past expiry as `EXPIRED`.

### Non-Functional
- **NFR-1 Security:** signature check over raw body, constant-time compare, 300 s timestamp window, 64 KiB body cap, secrets only from env, endpoint 503 when not configured. Order creation `@RateLimited`. No secrets, account numbers or transfer memos in logs beyond masked form.
- **NFR-2 Money:** VND as `BIGINT`, never floating point; every state change of an order is in one DB transaction with the transfer insert.
- **NFR-3 Idempotency:** replayed webhooks and reconciliation duplicates are no-ops (unique key on SePay transaction id).
- **NFR-4 Latency:** webhook answers in < 1 s (SePay timeout 30 s); notifications async after commit.
- **NFR-5 i18n:** all UI strings in `en` and `vi`.
- **NFR-6 Coverage:** CI 90% line coverage holds.

## Scope

### In Scope
Bank-transfer webhook integration, VietQR display, donations, Premium plans + entitlement, admin tab, admin notifications, reconciliation and expiry jobs.

### Out of Scope
- SePay Payment Gateway (cards/NAPAS checkout) — see ADR-0037.
- Gating actual features behind Premium (follow-up once benefits are decided).
- Refunds (handled manually outside the app), invoices/VAT, auto-renewal, public supporters wall, email receipts.

## Dependencies
- SePay account with the receiving bank account linked; webhook configured with HMAC auth; payment-code pattern set to the app prefix.
- Env vars on the VM/Helm: `SEPAY_WEBHOOK_SECRET`, `SEPAY_API_TOKEN`, `SEPAY_BANK_ACCOUNT`, `SEPAY_BANK_CODE`, `SEPAY_ACCOUNT_NAME`, `SEPAY_CODE_PREFIX`.

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| User edits/removes the memo → unmatched | Store as UNMATCHED; admin links it; UI tells user to keep memo unchanged with a copy button |
| Webhook outage > SePay retry window (~33 min) | Reconciliation job via transaction API |
| Forged webhook | HMAC + timestamp window; optional IP allowlist |
| QR image host (`qr.sepay.vn` vs `vietqr.app`) undocumented | Host is config; verified in sandbox task PAY-10 |
| Premium promised but no benefits yet | Plans inactive in prod until benefits are decided (owner toggles) |

## Open Questions
1. Which Premium benefits (Collection-only videos, HLS quality, badge…)? — deferred; mechanism only.
2. Final plan prices — admin sets after launch.

## Next Steps & Handoffs
ADR-0037 → sequence diagrams → plan → `/swarm-execute`.

## Version History
| Version | Date | Change |
|---------|------|--------|
| 0.1 | 2026-11-12 | Draft from /swarm-plan |
