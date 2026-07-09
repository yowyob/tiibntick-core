package com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence;

import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity.PlatformClientEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for {@code tnt_platform_clients}. Filtered/paginated
 * listing lives in {@code PlatformClientRepositoryAdapter} (via {@code R2dbcEntityTemplate}
 * + dynamic {@code Criteria}) rather than here, to avoid the well-known R2DBC Postgres
 * pitfall of binding {@code NULL} to an optional filter parameter in a derived/@Query method.
 *
 * @author MANFOUO Braun
 */
public interface PlatformClientR2dbcRepository extends ReactiveCrudRepository<PlatformClientEntity, String> {

    Mono<PlatformClientEntity> findByClientId(String clientId);

    Mono<Boolean> existsByClientId(String clientId);
}
