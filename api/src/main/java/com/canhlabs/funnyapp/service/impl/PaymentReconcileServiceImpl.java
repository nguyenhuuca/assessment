package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.enums.TxSource;
import com.canhlabs.funnyapp.repo.PaymentTransactionRepository;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.service.PaymentIngestionService;
import com.canhlabs.funnyapp.service.PaymentReconcileService;
import com.canhlabs.funnyapp.service.payment.BankTransfer;
import com.canhlabs.funnyapp.service.payment.FeedTransfer;
import com.canhlabs.funnyapp.service.payment.PaymentProvider;
import com.canhlabs.funnyapp.service.payment.PaymentProviderRegistry;
import com.canhlabs.funnyapp.service.payment.ProviderApiException;
import com.canhlabs.funnyapp.service.payment.TransferFeed;
import com.canhlabs.funnyapp.utils.AppConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reconciliation over every provider that has a pull API (ADR-0038). Each daily run covers the previous calendar
 * day (T-1) in Vietnam time; the job runs at 00:15. Rows the provider says its webhook delivered are counted and
 * skipped; the rest go through {@link PaymentIngestionService}, whose bank reference + amount dedupe also stops a
 * late webhook retry from crediting the same transfer twice.
 */
@Slf4j
@Service
public class PaymentReconcileServiceImpl implements PaymentReconcileService {

    private static final ZoneId BANK_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Bank and server clocks differ, and feeds filter on bank time. */
    static final Duration CHECK_SLACK = Duration.ofHours(1);

    private final PaymentProviderRegistry registry;
    private final PaymentIngestionService ingestion;
    private final PaymentTransactionRepository txRepo;
    private final FeatureFlagService flags;
    private final AppProperties props;
    private final Clock clock;

    @Autowired
    public PaymentReconcileServiceImpl(PaymentProviderRegistry registry, PaymentIngestionService ingestion,
                                       PaymentTransactionRepository txRepo, FeatureFlagService flags,
                                       AppProperties props) {
        this(registry, ingestion, txRepo, flags, props, Clock.systemUTC());
    }

    PaymentReconcileServiceImpl(PaymentProviderRegistry registry, PaymentIngestionService ingestion,
                                PaymentTransactionRepository txRepo, FeatureFlagService flags,
                                AppProperties props, Clock clock) {
        this.registry = registry;
        this.ingestion = ingestion;
        this.txRepo = txRepo;
        this.flags = flags;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public int reconcile() {
        if (!flags.isEnabled(AppConstant.Flags.PAYMENT_RECONCILE, false)) {
            log.debug("Payment reconcile skipped: flag off");
            return 0;
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), BANK_ZONE);
        LocalDate day = today.minusDays(1);
        Instant from = day.atStartOfDay(BANK_ZONE).toInstant();
        Instant to = today.atStartOfDay(BANK_ZONE).toInstant();
        String prefix = prefix();
        int processed = 0;
        for (PaymentProvider provider : registry.all()) {
            TransferFeed feed = provider.feed().orElse(null);
            if (feed == null) {
                continue;
            }
            if (!feed.isConfigured()) {
                log.warn("{} reconcile skipped: API is not configured", provider.displayName());
                continue;
            }
            processed += reconcileDay(provider, feed, prefix, day, from, to);
        }
        return processed;
    }

    private int reconcileDay(PaymentProvider provider, TransferFeed feed, String prefix, LocalDate day,
                             Instant from, Instant to) {
        List<FeedTransfer> rows = List.of();
        String result = "ok";
        try {
            rows = feed.fetch(prefix, from, to).stream().filter(carries(t -> carriesPrefix(t, prefix))).toList();
        } catch (ProviderApiException e) {
            // the next run reads the whole day again
            result = "api_error";
            log.warn("{} reconcile: {}", provider.displayName(), e.getMessage());
        }
        long delivered = rows.stream().filter(FeedTransfer::deliveredByWebhook).count();
        List<BankTransfer> open = undelivered(rows);
        Tally t = processNew(provider, open);
        // Grafana "Business Stats" (SePay Reconcile row) parses this line: keep its text and INFO level.
        // total = webhook + reconciled + already + failed
        log.info("{} reconcile run: day={}, result={}, total={}, webhook={}, reconciled={}, already={}, failed={}",
                provider.displayName(), day, result, delivered + open.size(), delivered, t.processed(), t.already(),
                t.failed());
        return t.processed();
    }

    @Override
    public CheckResult checkCode(String providerId, String code, Instant since) {
        PaymentProvider provider = registry.byId(providerId)
                .orElseThrow(() -> new ProviderApiException("unknown payment provider " + providerId, false));
        TransferFeed feed = provider.feed()
                .orElseThrow(() -> new ProviderApiException(provider.displayName() + " has no transfer API", false));
        String wanted = code.trim().toUpperCase(Locale.ROOT);
        List<FeedTransfer> rows = feed.fetch(wanted, since.minus(CHECK_SLACK), Instant.now(clock).plus(CHECK_SLACK))
                .stream().filter(carries(t -> carriesCode(t, wanted))).toList();
        List<BankTransfer> found = undelivered(rows);
        int processed = processNew(provider, found).processed();
        log.info("{} check for order {}: {} undelivered transfer(s), {} processed",
                provider.displayName(), wanted, found.size(), processed);
        return new CheckResult(found.size(), processed);
    }

    /** Outcome of one pass over undelivered transfers. */
    record Tally(int processed, int already, int failed) {
    }

    /** Sends each transfer not stored yet through ingestion; one failure does not stop the rest. */
    private Tally processNew(PaymentProvider provider, List<BankTransfer> txs) {
        if (txs.isEmpty()) {
            return new Tally(0, 0, 0);
        }
        Set<String> known = txRepo.findStoredTxIds(provider.id(),
                txs.stream().map(BankTransfer::providerTxId).toList());
        int processed = 0;
        int failed = 0;
        for (BankTransfer tx : txs) {
            if (known.contains(tx.providerTxId())) {
                continue;
            }
            try {
                // process() also skips a transfer stored under another id (same bank reference + amount)
                ingestion.process(tx, TxSource.RECONCILE);
                processed++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("{} reconcile could not process tx {}: {}", provider.displayName(), tx.providerTxId(),
                        e.getClass().getSimpleName());
            }
        }
        return new Tally(processed, txs.size() - processed - failed, failed);
    }

    private static List<BankTransfer> undelivered(List<FeedTransfer> rows) {
        return rows.stream().filter(r -> !r.deliveredByWebhook()).map(FeedTransfer::transfer).toList();
    }

    private static Predicate<FeedTransfer> carries(Predicate<BankTransfer> test) {
        return r -> test.test(r.transfer());
    }

    /** Provider search also matches inside unrelated words; keep transfers whose code or memo carries the prefix. */
    static boolean carriesPrefix(BankTransfer t, String prefix) {
        return (t.code() != null && t.code().trim().toUpperCase(Locale.ROOT).startsWith(prefix))
                || contains(t.content(), prefix);
    }

    /** One order: the provider's extracted code is that order's code, or the memo contains it. */
    static boolean carriesCode(BankTransfer t, String code) {
        return (t.code() != null && t.code().trim().equalsIgnoreCase(code)) || contains(t.content(), code);
    }

    private static boolean contains(String value, String part) {
        return value != null && value.toUpperCase(Locale.ROOT).contains(part);
    }

    private String prefix() {
        return props.getPayment().getCodePrefix().trim().toUpperCase(Locale.ROOT);
    }
}
