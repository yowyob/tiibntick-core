package com.yowyob.tiibntick.core.billing.cost.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.billing.cost.adapter.out.persistence.entity.FleetCostParametersEntity;
import com.yowyob.tiibntick.core.billing.cost.adapter.out.persistence.repository.FleetCostParametersR2dbcRepository;
import com.yowyob.tiibntick.core.billing.cost.application.port.out.IFleetCostParametersPort;
import com.yowyob.tiibntick.core.billing.cost.domain.model.FleetCostParameters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Adapter implementing {@link IFleetCostParametersPort} via Spring Data R2DBC.
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FleetCostParametersAdapter implements IFleetCostParametersPort {

    private final FleetCostParametersR2dbcRepository repository;

    @Override
    public Mono<FleetCostParameters> findByOwnerOrgId(String ownerOrgId) {
        return repository.findByOwnerOrgId(ownerOrgId)
                .map(FleetCostParametersEntity::toDomain);
    }

    @Override
    public Mono<FleetCostParameters> save(FleetCostParameters params) {
        // Upsert: find existing or create new
        return repository.findByOwnerOrgId(params.ownerOrgId())
                .flatMap(existing -> {
                    FleetCostParametersEntity updated = FleetCostParametersEntity.fromDomain(params);
                    updated.setId(existing.getId());
                    updated.setNew(false);
                    return repository.save(updated);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    FleetCostParametersEntity created = FleetCostParametersEntity.fromDomain(params);
                    created.setNew(true);
                    return repository.save(created);
                }))
                .map(FleetCostParametersEntity::toDomain);
    }
}
