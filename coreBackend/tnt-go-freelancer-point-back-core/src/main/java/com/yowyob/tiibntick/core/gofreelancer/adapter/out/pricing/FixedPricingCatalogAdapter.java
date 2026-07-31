package com.yowyob.tiibntick.core.gofreelancer.adapter.out.pricing;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PricingCatalogPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Fixed mock implementation of PricingCatalogPort.
 * In a real-world scenario, this should query a database or the billing module.
 */
@Component
public class FixedPricingCatalogAdapter implements PricingCatalogPort {

    @Override
    public Mono<Money> getSubscriptionPrice(String subscriptionType, String countryCode) {
        // FREE plans are always 0
        if (subscriptionType != null && subscriptionType.toUpperCase().contains("FREE")) {
            if ("FR".equalsIgnoreCase(countryCode) || "US".equalsIgnoreCase(countryCode)) {
                return Mono.just(Money.of(BigDecimal.ZERO, "EUR")); // Fallback currency
            }
            return Mono.just(Money.of(BigDecimal.ZERO, "XAF"));
        }

        // Mock pricing catalog
        if ("FR".equalsIgnoreCase(countryCode) || "BE".equalsIgnoreCase(countryCode) || "DE".equalsIgnoreCase(countryCode)) {
            // Europe pricing
            if ("ADVANCE".equalsIgnoreCase(subscriptionType)) return Mono.just(Money.of(BigDecimal.valueOf(20.0), "EUR"));
            if ("STANDARD".equalsIgnoreCase(subscriptionType) || "PREMIUM".equalsIgnoreCase(subscriptionType)) return Mono.just(Money.of(BigDecimal.valueOf(10.0), "EUR"));
            return Mono.just(Money.of(BigDecimal.valueOf(5.0), "EUR")); // Basic
        } else if ("US".equalsIgnoreCase(countryCode)) {
            // US pricing
            if ("ADVANCE".equalsIgnoreCase(subscriptionType)) return Mono.just(Money.of(BigDecimal.valueOf(25.0), "USD"));
            if ("STANDARD".equalsIgnoreCase(subscriptionType) || "PREMIUM".equalsIgnoreCase(subscriptionType)) return Mono.just(Money.of(BigDecimal.valueOf(15.0), "USD"));
            return Mono.just(Money.of(BigDecimal.valueOf(7.0), "USD")); // Basic
        } else {
            // Default to XAF (Cameroon, etc.)
            if ("ADVANCE".equalsIgnoreCase(subscriptionType)) return Mono.just(Money.of(BigDecimal.valueOf(15000.0), "XAF"));
            if ("STANDARD".equalsIgnoreCase(subscriptionType) || "PREMIUM".equalsIgnoreCase(subscriptionType)) return Mono.just(Money.of(BigDecimal.valueOf(10000.0), "XAF"));
            return Mono.just(Money.of(BigDecimal.valueOf(7500.0), "XAF")); // Basic
        }
    }
}
