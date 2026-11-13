package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.payment.CreateOrderRequest;
import com.canhlabs.funnyapp.dto.payment.OrderDto;
import com.canhlabs.funnyapp.dto.payment.OrderStatusDto;
import com.canhlabs.funnyapp.dto.payment.PlanDto;
import com.canhlabs.funnyapp.entity.PaymentOrder;
import com.canhlabs.funnyapp.entity.PremiumPlan;
import com.canhlabs.funnyapp.enums.PaymentType;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.PaymentOrderRepository;
import com.canhlabs.funnyapp.repo.PremiumPlanRepository;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.service.PaymentOrderService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.utils.VietQrUrlBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PaymentOrderServiceImpl implements PaymentOrderService {

    static final int MAX_NAME = 50;
    static final int MAX_MESSAGE = 200;
    static final int CODE_LENGTH = 8;
    static final int MAX_CODE_ATTEMPTS = 10;
    private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    static final Duration PURGE_AFTER = Duration.ofDays(7);

    private final PaymentOrderRepository orderRepo;
    private final PremiumPlanRepository planRepo;
    private final AppProperties appProps;
    private final FeatureFlagService featureFlagService;
    private final SecureRandom random = new SecureRandom();
    private final Pattern codePattern;

    public PaymentOrderServiceImpl(PaymentOrderRepository orderRepo, PremiumPlanRepository planRepo,
                                   AppProperties appProps, FeatureFlagService featureFlagService) {
        this.orderRepo = orderRepo;
        this.planRepo = planRepo;
        this.appProps = appProps;
        this.featureFlagService = featureFlagService;
        String prefix = appProps.getSepay().getCodePrefix() == null ? "" : appProps.getSepay().getCodePrefix().toUpperCase(Locale.ROOT);
        this.codePattern = Pattern.compile(Pattern.quote(prefix) + "[A-Z0-9]{" + CODE_LENGTH + "}");
    }

    @Override
    public boolean isOrderingEnabled() {
        // Harness flag gates new orders only; the webhook keeps crediting orders already shown (money may be in flight)
        return appProps.getSepay().isOrderingConfigured()
                && featureFlagService.isEnabled(AppConstant.Flags.SEPAY_PAYMENTS, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanDto> listActivePlans() {
        return planRepo.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(p -> PlanDto.builder().id(p.getId()).code(p.getCode()).name(p.getName())
                        .durationDays(p.getDurationDays()).priceVnd(p.getPriceVnd()).build())
                .toList();
    }

    @Override
    @Transactional
    public OrderDto createOrder(CreateOrderRequest request, Long userId) {
        AppProperties.Sepay sepay = appProps.getSepay();
        if (!isOrderingEnabled()) {
            throw CustomException.of(HttpStatus.SERVICE_UNAVAILABLE, 5031, "error.payment.notConfigured");
        }
        if (request == null || request.getType() == null) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 4001, "error.payment.typeRequired");
        }
        PaymentOrder order = request.getType() == PaymentType.PREMIUM
                ? premiumOrder(request, userId)
                : donationOrder(request, userId, sepay);
        order.setCode(newUniqueCode(sepay.getCodePrefix()));
        order.setExpiresAt(Instant.now().plus(sepay.getOrderTtlHours(), ChronoUnit.HOURS));
        PaymentOrder saved = orderRepo.save(order);

        long amount = saved.getExpectedAmount() != null ? saved.getExpectedAmount() : request.getAmount();
        return OrderDto.builder()
                .code(saved.getCode())
                .type(saved.getType())
                .status(saved.getStatus())
                .amount(amount)
                .expiresAt(saved.getExpiresAt())
                .qrUrl(VietQrUrlBuilder.build(sepay, amount, saved.getCode()))
                .bankCode(sepay.getBankCode())
                .bankAccount(sepay.getBankAccount())
                .accountName(sepay.getAccountName())
                .memo(saved.getCode())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStatusDto getByCode(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!codePattern.matcher(normalized).matches()) {
            throw CustomException.of(HttpStatus.NOT_FOUND, 4041, "error.payment.orderNotFound");
        }
        PaymentOrder o = orderRepo.findByCode(normalized)
                .orElseThrow(() -> CustomException.of(HttpStatus.NOT_FOUND, 4041, "error.payment.orderNotFound"));
        Long amount = o.getPaidAmount() > 0 ? Long.valueOf(o.getPaidAmount()) : o.getExpectedAmount();
        return OrderStatusDto.builder().code(o.getCode()).type(o.getType()).status(o.getStatus())
                .amount(amount).expiresAt(o.getExpiresAt()).build();
    }

    @Override
    @Transactional
    public int expireStaleOrders() {
        Instant now = Instant.now();
        int expired = orderRepo.expirePending(now);
        if (expired > 0) {
            log.info("Expired {} stale payment order(s)", expired);
        }
        int purged = orderRepo.purgeUnpaidExpired(now.minus(PURGE_AFTER));
        if (purged > 0) {
            log.info("Purged {} old unpaid payment order(s)", purged);
        }
        return expired;
    }

    private PaymentOrder donationOrder(CreateOrderRequest request, Long userId, AppProperties.Sepay sepay) {
        Long amount = request.getAmount();
        if (amount == null || amount < sepay.getMinDonation()) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 4002, "error.payment.amountTooLow", sepay.getMinDonation());
        }
        return PaymentOrder.builder()
                .type(PaymentType.DONATION)
                .userId(userId)
                // amount the donor picked, shown to admins; it never makes a donation UNDERPAID (PaymentSettlement)
                .expectedAmount(amount)
                .donorName(clean(request.getDonorName(), MAX_NAME, "error.payment.nameTooLong"))
                .message(clean(request.getMessage(), MAX_MESSAGE, "error.payment.messageTooLong"))
                .build();
    }

    private PaymentOrder premiumOrder(CreateOrderRequest request, Long userId) {
        if (userId == null) {
            throw CustomException.of(HttpStatus.UNAUTHORIZED, 4011, "error.auth.required");
        }
        PremiumPlan plan = request.getPlanId() == null ? null : planRepo.findById(request.getPlanId()).orElse(null);
        if (plan == null || !plan.isActive()) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 4003, "error.payment.planInvalid");
        }
        return PaymentOrder.builder()
                .type(PaymentType.PREMIUM)
                .userId(userId)
                .planId(plan.getId())
                .expectedAmount(plan.getPriceVnd())
                .build();
    }

    /** Trims; blank becomes null; over-long input is rejected rather than silently cut. */
    private static String clean(String value, int max, String errorKey) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 4004, errorKey, max);
        }
        return trimmed;
    }

    private String newUniqueCode(String prefix) {
        for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
            StringBuilder sb = new StringBuilder(prefix == null ? "" : prefix);
            for (int j = 0; j < CODE_LENGTH; j++) {
                sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!orderRepo.existsByCode(code)) {
                return code;
            }
        }
        throw CustomException.of(HttpStatus.INTERNAL_SERVER_ERROR, 5001, "SYSTEM_ERROR");
    }
}
