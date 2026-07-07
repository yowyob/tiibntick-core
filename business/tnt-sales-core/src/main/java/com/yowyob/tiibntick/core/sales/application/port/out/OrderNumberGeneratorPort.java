package com.yowyob.tiibntick.core.sales.application.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Generates unique, sequential order numbers for a tenant.
 * Format: TNT-ORD-{AGENCY_CODE}-{YEAR}-{SEQ}
 * Author: MANFOUO Braun
 */
public interface OrderNumberGeneratorPort {
    Mono<String> generate(UUID tenantId, UUID agencyId, int year);
}
