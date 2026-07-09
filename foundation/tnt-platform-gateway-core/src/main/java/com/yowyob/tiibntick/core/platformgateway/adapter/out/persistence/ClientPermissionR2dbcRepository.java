package com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence;

import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity.ClientPermissionEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for {@code tnt_client_permissions}.
 *
 * @author MANFOUO Braun
 */
public interface ClientPermissionR2dbcRepository extends ReactiveCrudRepository<ClientPermissionEntity, String> {

    Flux<ClientPermissionEntity> findByPlatformClientId(String platformClientId);

    Mono<Void> deleteByPlatformClientId(String platformClientId);
}
