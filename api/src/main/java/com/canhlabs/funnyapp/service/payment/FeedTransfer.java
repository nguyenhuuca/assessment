package com.canhlabs.funnyapp.service.payment;

/** A transfer read from a provider's pull API; {@code deliveredByWebhook} when the provider says its webhook got through. */
public record FeedTransfer(BankTransfer transfer, boolean deliveredByWebhook) {
}
