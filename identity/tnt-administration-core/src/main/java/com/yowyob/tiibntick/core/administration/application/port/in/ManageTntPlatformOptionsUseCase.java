package com.yowyob.tiibntick.core.administration.application.port.in;

import com.yowyob.tiibntick.core.administration.domain.model.TntPlatformOptions;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Gets and updates TiiBnTick platform options for a specific tenant.
 *
 * @author MANFOUO Braun
 */
public interface ManageTntPlatformOptionsUseCase {
    Mono<TntPlatformOptions> getPlatformOptions(UUID tenantId);

    Mono<TntPlatformOptions> updatePlatformOptions(UUID tenantId, TntPlatformOptions options);

    Mono<TntPlatformOptions> initializeDefaultOptions(UUID tenantId);
}
