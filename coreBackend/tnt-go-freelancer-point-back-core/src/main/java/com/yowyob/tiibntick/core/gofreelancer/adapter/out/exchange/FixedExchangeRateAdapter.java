package com.yowyob.tiibntick.core.gofreelancer.adapter.out.exchange;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.ExchangeRatePort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fixed mock implementation of ExchangeRatePort.
 * In the future, this should call an external API (like Flutterwave/Stripe)
 * or a database table with up-to-date rates.
 */
@Component
public class FixedExchangeRateAdapter implements ExchangeRatePort {

    @Override
    public Mono<Money> convert(Money sourceAmount, String targetCurrency) {
        if (sourceAmount.currencyCode().equalsIgnoreCase(targetCurrency)) {
            return Mono.just(sourceAmount);
        }

        // Extremely simple mockup logic
        BigDecimal rate = BigDecimal.ONE;

        if ("EUR".equalsIgnoreCase(sourceAmount.currencyCode()) && "XAF".equalsIgnoreCase(targetCurrency)) {
            rate = BigDecimal.valueOf(655.957);
        } else if ("XAF".equalsIgnoreCase(sourceAmount.currencyCode()) && "EUR".equalsIgnoreCase(targetCurrency)) {
            rate = BigDecimal.valueOf(1.0 / 655.957);
        } else if ("USD".equalsIgnoreCase(sourceAmount.currencyCode()) && "XAF".equalsIgnoreCase(targetCurrency)) {
            rate = BigDecimal.valueOf(600.0);
        } else if ("XAF".equalsIgnoreCase(sourceAmount.currencyCode()) && "USD".equalsIgnoreCase(targetCurrency)) {
            rate = BigDecimal.valueOf(1.0 / 600.0);
        } else {
            // Default 1:1 if not found in mock
            rate = BigDecimal.ONE;
        }

        BigDecimal converted = sourceAmount.amount().multiply(rate).setScale(2, RoundingMode.HALF_UP);
        return Mono.just(Money.of(converted, targetCurrency));
    }
}
