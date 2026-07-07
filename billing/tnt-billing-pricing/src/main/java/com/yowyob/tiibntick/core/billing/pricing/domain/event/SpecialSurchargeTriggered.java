package com.yowyob.tiibntick.core.billing.pricing.domain.event;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a special surcharge rule fires during price evaluation.
 *
 * <p>Useful for analytics (which surcharges are most frequently triggered),
 * audit trails, and alerting.
 *
 * @author MANFOUO Braun
 */
@Value
@Builder
public class SpecialSurchargeTriggered {

    UUID eventId;
    UUID policyId;
    String surchargeCode;
    BigDecimal amount;
    String currencyCode;
    String triggerContext;
    Instant occurredAt;

    public static SpecialSurchargeTriggered of(UUID policyId, String surchargeCode,
                                                BigDecimal amount, String currency,
                                                String context) {
        return SpecialSurchargeTriggered.builder()
                .eventId(UUID.randomUUID())
                .policyId(policyId)
                .surchargeCode(surchargeCode)
                .amount(amount)
                .currencyCode(currency)
                .triggerContext(context)
                .occurredAt(Instant.now())
                .build();
    }
}
