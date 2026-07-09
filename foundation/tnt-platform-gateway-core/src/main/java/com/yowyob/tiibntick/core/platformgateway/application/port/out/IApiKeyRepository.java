package com.yowyob.tiibntick.core.platformgateway.application.port.out;

import com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKey;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Secondary (outbound) port for {@link ApiKey} persistence.
 *
 * @author MANFOUO Braun
 */
public interface IApiKeyRepository {

    Mono<ApiKey> save(ApiKey apiKey);

    Mono<ApiKey> findById(UUID id);

    /**
     * All keys (any status) for a client — indexed on {@code platform_client_id}, and
     * bounded to a handful of rows per client (a rotation window rarely holds more than
     * 2-3), so filtering by status in memory (see
     * {@code PlatformClientAuthenticationService}) is not a full-table-scan concern.
     */
    Flux<ApiKey> findAllByPlatformClientId(UUID platformClientId);

    Mono<Void> markLastUsed(UUID id, Instant lastUsedAt);
}
