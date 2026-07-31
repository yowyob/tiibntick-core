package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import reactor.core.publisher.Mono;

public interface ExchangeRatePort {
    /**
     * Converts an amount from one currency to another.
     *
     * @param sourceAmount the original amount and currency
     * @param targetCurrency the currency code to convert to
     * @return a Mono containing the converted Money object
     */
    Mono<Money> convert(Money sourceAmount, String targetCurrency);
}
