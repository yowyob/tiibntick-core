package com.yowyob.tiibntick.core.gofreelancer.domain.port.out;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import reactor.core.publisher.Mono;

public interface PricingCatalogPort {
    
    /**
     * Retrieves the base price of a subscription plan for a specific country.
     * 
     * @param subscriptionType string representation of the plan (e.g. STANDARD, ADVANCE, BASIC)
     * @param countryCode country code (e.g. CM, FR, US)
     * @return the local price with currency
     */
    Mono<Money> getSubscriptionPrice(String subscriptionType, String countryCode);
}
