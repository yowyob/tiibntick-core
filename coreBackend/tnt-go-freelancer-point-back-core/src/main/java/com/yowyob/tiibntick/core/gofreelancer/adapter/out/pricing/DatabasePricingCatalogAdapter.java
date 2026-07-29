package com.yowyob.tiibntick.core.gofreelancer.adapter.out.pricing;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.ExchangeRatePort;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.PricingCatalogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Real implementation of {@link PricingCatalogPort} that reads subscription prices from the
 * {@code pricing_catalog} table (prices stored in XAF) and converts them to the requested
 * currency using {@link ExchangeRatePort}.
 *
 * This replaces the previous mock {@link FixedPricingCatalogAdapter}.
 */
@Primary
@Component
@RequiredArgsConstructor
public class DatabasePricingCatalogAdapter implements PricingCatalogPort {

    private final DatabaseClient dbClient; // R2DBC non‑blocking client
    private final ExchangeRatePort exchangeRatePort;

    @Override
    public Mono<Money> getSubscriptionPrice(String subscriptionType, String countryCode) {
        // 1️⃣ Fetch the price in XAF from the DB (FREE is stored with price 0)
        return dbClient.sql("SELECT price_xaf FROM pricing_catalog WHERE subscription_type = :type")
                .bind("type", subscriptionType.toUpperCase())
                .map((row, meta) -> row.get("price_xaf", BigDecimal.class))
                .first()
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Unknown subscription type: " + subscriptionType)))
                // 2️⃣ Convert to target currency when needed
                .flatMap(priceXaf -> {
                    // If the client is in Cameroon (or any non‑EU/US country) keep XAF
                    if (!"FR".equalsIgnoreCase(countryCode) &&
                        !"BE".equalsIgnoreCase(countryCode) &&
                        !"DE".equalsIgnoreCase(countryCode) &&
                        !"US".equalsIgnoreCase(countryCode)) {
                        return Mono.just(Money.of(priceXaf, "XAF"));
                    }
                    // Determine target currency based on country
                    String targetCurrency = ("FR".equalsIgnoreCase(countryCode) ||
                                            "BE".equalsIgnoreCase(countryCode) ||
                                            "DE".equalsIgnoreCase(countryCode)) ? "EUR" : "USD";
                    // Convert from XAF to target
                    Money source = Money.of(priceXaf, "XAF");
                    return exchangeRatePort.convert(source, targetCurrency);
                });
    }
}
