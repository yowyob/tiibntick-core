package com.yowyob.tiibntick.core.administration.application.port.out;

import com.yowyob.tiibntick.core.administration.domain.model.TntPlatformOptions;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for TntPlatformOptions persistence.
 * Author: MANFOUO Braun
 */
public interface TntPlatformOptionsRepository {
    Mono<TntPlatformOptions> findByTenantId(UUID tenantId);
    Mono<TntPlatformOptions> save(TntPlatformOptions options);
}
