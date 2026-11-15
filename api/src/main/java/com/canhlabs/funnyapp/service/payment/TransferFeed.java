package com.canhlabs.funnyapp.service.payment;

import java.time.Instant;
import java.util.List;

/** A provider's pull API, used by reconciliation and by the admin "check this order now" action. */
public interface TransferFeed {

    /** False while the API credentials are missing. */
    boolean isConfigured();

    /**
     * Incoming transfers whose memo or extracted code matches {@code query}, dated in {@code [from, to)}.
     * The provider's search may be fuzzy; callers filter on the exact code.
     *
     * @throws ProviderApiException when the API is not configured or cannot be read
     */
    List<FeedTransfer> fetch(String query, Instant from, Instant to);
}
