package com.yowyob.tiibntick.core.administration.application.port.in;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Days during which a dispute may still be opened on a DELIVERED or RETURNED parcel (platform option
 * {@code disputeFilingWindowDays}, lot L4). Service-to-service read: no permission, unlike
 * {@link ManageTntPlatformOptionsUseCase#getPlatformOptions}.
 */
public interface ResolveDisputeFilingWindowUseCase {

    /** Used when neither the tenant nor the system tenant has platform options. */
    int DEFAULT_DAYS = 7;

    /** @return the tenant's window, else the system tenant's, else {@link #DEFAULT_DAYS} */
    Mono<Integer> filingWindowDays(UUID tenantId);
}
