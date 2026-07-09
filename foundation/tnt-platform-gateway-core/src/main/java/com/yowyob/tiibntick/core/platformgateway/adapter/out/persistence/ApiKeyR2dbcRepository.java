package com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence;

import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity.ApiKeyEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * Spring Data R2DBC repository for {@code tnt_api_keys}.
 *
 * @author MANFOUO Braun
 */
public interface ApiKeyR2dbcRepository extends ReactiveCrudRepository<ApiKeyEntity, String> {

    Flux<ApiKeyEntity> findByPlatformClientId(String platformClientId);
}
