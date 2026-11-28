package com.yowyob.tiibntick.core.delivery.application.port.out;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * How long after a parcel is DELIVERED or RETURNED a dispute may still be opened (platform option
 * {@code disputeFilingWindowDays} of the administration module, lot L4).
 */
public interface DisputeFilingWindowPort {

    Duration DEFAULT = Duration.ofDays(7);

    /** @return the tenant's window, {@link #DEFAULT} when unknown */
    Mono<Duration> filingWindow(UUID tenantId);
}
