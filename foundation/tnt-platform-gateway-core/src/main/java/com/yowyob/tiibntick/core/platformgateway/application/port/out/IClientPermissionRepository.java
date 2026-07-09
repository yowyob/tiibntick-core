package com.yowyob.tiibntick.core.platformgateway.application.port.out;

import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientPermission;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Secondary (outbound) port for {@link ClientPermission} (granted scope) persistence.
 *
 * @author MANFOUO Braun
 */
public interface IClientPermissionRepository {

    Mono<ClientPermission> save(ClientPermission permission);

    Flux<ClientPermission> findByPlatformClientId(UUID platformClientId);

    Mono<Void> deleteByPlatformClientId(UUID platformClientId);
}
