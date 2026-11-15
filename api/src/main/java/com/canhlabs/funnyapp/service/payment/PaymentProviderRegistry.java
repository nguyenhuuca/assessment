package com.canhlabs.funnyapp.service.payment;

import com.canhlabs.funnyapp.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Every {@link PaymentProvider} bean by id. {@code app.payment.provider} picks the one used for new orders; all
 * registered providers keep accepting webhooks and reconciliation, so orders issued before a switch still settle.
 */
@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providers = new LinkedHashMap<>();
    private final PaymentProvider active;

    public PaymentProviderRegistry(List<PaymentProvider> all, AppProperties props) {
        for (PaymentProvider p : all) {
            if (providers.putIfAbsent(p.id(), p) != null) {
                throw new IllegalStateException("Two payment providers share the id " + p.id());
            }
        }
        String wanted = props.getPayment().getProvider() == null ? ""
                : props.getPayment().getProvider().trim().toLowerCase(Locale.ROOT);
        this.active = providers.get(wanted);
        if (active == null) {
            throw new IllegalStateException("app.payment.provider=" + wanted + " is not one of " + providers.keySet());
        }
    }

    /** The provider new orders are issued with. */
    public PaymentProvider active() {
        return active;
    }

    public Optional<PaymentProvider> byId(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(providers.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<PaymentProvider> all() {
        return providers.values();
    }
}
