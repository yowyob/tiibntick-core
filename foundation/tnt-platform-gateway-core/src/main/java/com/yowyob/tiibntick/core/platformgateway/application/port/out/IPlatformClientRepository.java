package com.yowyob.tiibntick.core.platformgateway.application.port.out;

import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientStatus;
import com.yowyob.tiibntick.core.platformgateway.domain.model.Environment;
import com.yowyob.tiibntick.core.platformgateway.domain.model.PlatformClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Secondary (outbound) port for {@link PlatformClient} persistence.
 *
 * @author MANFOUO Braun
 */
public interface IPlatformClientRepository {

    Mono<PlatformClient> save(PlatformClient client);

    Mono<PlatformClient> findById(UUID id);

    Mono<PlatformClient> findByClientId(String clientId);

    Mono<Boolean> existsByClientId(String clientId);

    Flux<PlatformClient> findPage(String platformCode, Environment environment, ClientStatus status, int page, int size);

    Mono<Long> count(String platformCode, Environment environment, ClientStatus status);
}
