package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.entity.PaymentOrder;
import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.TransferDirection;
import com.canhlabs.funnyapp.enums.TxMatchStatus;
import com.canhlabs.funnyapp.enums.TxSource;
import com.canhlabs.funnyapp.repo.PaymentOrderRepository;
import com.canhlabs.funnyapp.repo.PaymentTransactionRepository;
import com.canhlabs.funnyapp.service.PaymentIngestionService;
import com.canhlabs.funnyapp.service.payment.BankTransfer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Transfer-to-order matching, the same for every provider (ADR-0037, ADR-0038). */
@Slf4j
@Service
public class PaymentIngestionServiceImpl implements PaymentIngestionService {

    static final int MAX_CONTENT = 500;
    static final int MAX_CODE = 64;
    static final int MAX_FIELD = 64;

    private final PaymentOrderRepository orderRepo;
    private final PaymentTransactionRepository txRepo;
    private final PaymentSettlement settlement;
    private final Clock clock;
    private final Pattern codePattern;
    private final String codePrefix;

    @Autowired
    public PaymentIngestionServiceImpl(AppProperties props, PaymentOrderRepository orderRepo,
                                       PaymentTransactionRepository txRepo, PaymentSettlement settlement) {
        this(props, orderRepo, txRepo, settlement, Clock.systemUTC());
    }

    PaymentIngestionServiceImpl(AppProperties props, PaymentOrderRepository orderRepo,
                                PaymentTransactionRepository txRepo, PaymentSettlement settlement, Clock clock) {
        this.orderRepo = orderRepo;
        this.txRepo = txRepo;
        this.settlement = settlement;
        this.clock = clock;
        this.codePrefix = props.getPayment().getCodePrefix().toUpperCase(Locale.ROOT);
        this.codePattern = Pattern.compile(Pattern.quote(codePrefix) + "[A-Z0-9]{8}");
    }

    @Override
    @Transactional
    public void process(BankTransfer tx, TxSource source) {
        if (isStoredUnderAnotherId(tx)) {
            log.info("{} tx {} skipped: same bank reference and amount already stored (source={})",
                    tx.provider(), tx.providerTxId(), source);
            return;
        }
        String code = extractCode(tx);
        if (tx.direction() != TransferDirection.IN || tx.amount() <= 0) {
            store(tx, source, null, TxMatchStatus.IGNORED, code);
            log.info("{} tx {} ignored (source={})", tx.provider(), tx.providerTxId(), source);
            return;
        }
        Optional<PaymentOrder> found = code == null ? Optional.empty() : orderRepo.findByCodeForUpdate(code);
        if (found.isEmpty() || found.get().getStatus() == PaymentOrderStatus.CANCELLED) {
            store(tx, source, null, TxMatchStatus.UNMATCHED, code);
            log.info("{} tx {} unmatched (source={})", tx.provider(), tx.providerTxId(), source);
            return;
        }
        PaymentOrder order = found.get();
        // Not redundant with the early check: a concurrent delivery of the same transfer may have committed while we
        // waited for the order lock, and under READ COMMITTED this statement sees it. Do not remove.
        if (isStoredUnderAnotherId(tx) || isSameTransferForOrder(tx, order)) {
            log.warn("{} tx {} skipped after lock: same transfer already stored (source={})",
                    tx.provider(), tx.providerTxId(), source);
            return;
        }
        if (store(tx, source, order.getId(), TxMatchStatus.MATCHED, code) == 0) {
            log.info("{} tx {} already processed (source={})", tx.provider(), tx.providerTxId(), source);
            return;
        }
        settlement.apply(order, tx.provider() + ":" + tx.providerTxId(), tx.amount(), Instant.now(clock));
    }

    /**
     * Second dedupe key next to (provider, provider_tx_id): the bank reference plus amount. It keeps a transfer from
     * being credited twice when the pull API reports it under another id, or a second provider sees the same account.
     */
    private boolean isStoredUnderAnotherId(BankTransfer tx) {
        String ref = cut(tx.referenceCode(), MAX_FIELD);
        return ref != null && !ref.isBlank()
                && txRepo.existsSameTransferUnderOtherId(ref, tx.amount(), tx.provider(), tx.providerTxId());
    }

    /** Fallback when the transfer has no bank reference: same order, amount and timestamp under another id. */
    private boolean isSameTransferForOrder(BankTransfer tx, PaymentOrder order) {
        String ref = cut(tx.referenceCode(), MAX_FIELD);
        if (ref != null && !ref.isBlank()) {
            return false;
        }
        if (tx.transactionDate() == null) {
            log.debug("{} tx {} has no reference and no timestamp: fallback dedupe skipped",
                    tx.provider(), tx.providerTxId());
            return false;
        }
        return txRepo.existsSameTransferForOrderUnderOtherId(order.getId(), tx.amount(),
                tx.transactionDate().truncatedTo(ChronoUnit.MICROS),
                tx.provider(), tx.providerTxId());
    }

    private int store(BankTransfer tx, TxSource source, Long orderId, TxMatchStatus status, String code) {
        return txRepo.insertIgnore(tx.provider(), tx.providerTxId(), orderId, status.name(), source.name(),
                tx.direction().name().toLowerCase(Locale.ROOT), tx.amount(), cut(code, MAX_CODE),
                cut(tx.content(), MAX_CONTENT), cut(tx.bank(), MAX_FIELD), maskAccount(tx.accountNumber()),
                cut(tx.referenceCode(), MAX_FIELD), tx.transactionDate());
    }

    /** The provider's own extracted code when it carries our prefix, else the first prefix+8 token in the memo. */
    String extractCode(BankTransfer tx) {
        if (tx.code() != null) {
            String c = tx.code().trim().toUpperCase(Locale.ROOT);
            if (c.startsWith(codePrefix)) {
                return c;
            }
        }
        if (tx.content() == null) {
            return null;
        }
        Matcher m = codePattern.matcher(tx.content().toUpperCase(Locale.ROOT));
        return m.find() ? m.group() : null;
    }

    static String maskAccount(String account) {
        if (account == null || account.isBlank()) {
            return null;
        }
        String a = account.trim();
        return a.length() <= 4 ? "****" : "****" + a.substring(a.length() - 4);
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
