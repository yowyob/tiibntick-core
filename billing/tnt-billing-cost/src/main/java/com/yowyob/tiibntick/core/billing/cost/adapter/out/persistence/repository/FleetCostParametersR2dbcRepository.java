package com.yowyob.tiibntick.core.billing.cost.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.billing.cost.adapter.out.persistence.entity.FleetCostParametersEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for FleetCostParametersEntity.
 * @author MANFOUO Braun
 */
public interface FleetCostParametersR2dbcRepository
        extends ReactiveCrudRepository<FleetCostParametersEntity, UUID> {

    @Query("SELECT * FROM fleet_cost_parameters WHERE owner_org_id = :ownerOrgId LIMIT 1")
    Mono<FleetCostParametersEntity> findByOwnerOrgId(String ownerOrgId);

    @Query("SELECT EXISTS(SELECT 1 FROM fleet_cost_parameters WHERE owner_org_id = :ownerOrgId)")
    Mono<Boolean> existsByOwnerOrgId(String ownerOrgId);
}
