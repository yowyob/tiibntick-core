package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The platform commission rate applied to the settlement of a delivery (decision H5).
 *
 * <p>Extension point only: the rate stays the fixed 5 % ({@code FixedCommissionRateAdapter}). Reading it from the
 * billing policy is a business decision that has not been taken; an implementation doing so replaces the default
 * adapter without touching the payment flow. Empty or failing → the caller uses the fixed 5 %.</p>
 */
public interface CommissionRatePort {

    Mono<BigDecimal> deliveryCommissionRate(UUID tenantId, UUID deliveryId);
}
