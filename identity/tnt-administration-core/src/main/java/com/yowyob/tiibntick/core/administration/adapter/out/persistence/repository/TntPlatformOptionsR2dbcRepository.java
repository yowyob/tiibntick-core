package com.yowyob.tiibntick.core.administration.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.administration.adapter.out.persistence.entity.TntPlatformOptionsEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for TntPlatformOptions entities.
 * Author: MANFOUO Braun
 */
public interface TntPlatformOptionsR2dbcRepository
        extends ReactiveCrudRepository<TntPlatformOptionsEntity, UUID> {

    @Query("SELECT * FROM administration.tnt_platform_options WHERE tenant_id = :tenantId")
    Mono<TntPlatformOptionsEntity> findByTenantId(UUID tenantId);
}
