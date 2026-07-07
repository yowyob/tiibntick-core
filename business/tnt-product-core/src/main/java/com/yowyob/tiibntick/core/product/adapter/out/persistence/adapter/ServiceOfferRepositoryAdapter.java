package com.yowyob.tiibntick.core.product.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.product.adapter.out.persistence.entity.ServiceOfferEntity;
import com.yowyob.tiibntick.core.product.adapter.out.persistence.repository.ServiceOfferR2dbcRepository;
import com.yowyob.tiibntick.core.product.application.port.out.ServiceOfferRepository;
import com.yowyob.tiibntick.core.product.domain.model.ServiceOffer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.UUID;

@Component
public class ServiceOfferRepositoryAdapter implements ServiceOfferRepository {
    private final ServiceOfferR2dbcRepository r2dbc;
    public ServiceOfferRepositoryAdapter(ServiceOfferR2dbcRepository r2dbc) { this.r2dbc = r2dbc; }

    @Override
    public Mono<ServiceOffer> save(ServiceOffer offer) {
        var _entity = ServiceOfferEntity.fromDomain(offer);
        return r2dbc.existsById(_entity.getId())
                .flatMap(exists -> {
                    _entity.setNew(!exists);
                    return r2dbc.save(_entity);
                })
                .map(ServiceOfferEntity::toDomain);
    }

    @Override
    public Mono<ServiceOffer> findById(UUID offerId) {
        return r2dbc.findById(offerId).map(ServiceOfferEntity::toDomain);
    }

    @Override
    public Flux<ServiceOffer> findByProvider(UUID tenantId, UUID providerId) {
        return r2dbc.findByTenantIdAndProviderId(tenantId, providerId).map(ServiceOfferEntity::toDomain);
    }

    @Override
    public Flux<ServiceOffer> findPublishedByTenant(UUID tenantId) {
        return r2dbc.findPublishedByTenantId(tenantId).map(ServiceOfferEntity::toDomain);
    }

    @Override
    public Flux<ServiceOffer> findAllById(List<UUID> offerIds) {
        return r2dbc.findAllById(offerIds).map(ServiceOfferEntity::toDomain);
    }

    @Override
    public Flux<ServiceOffer> findMatchingForMission(UUID tenantId, double weightKg, double distanceKm) {
        return r2dbc.findMatchingOffers(tenantId, weightKg, distanceKm).map(ServiceOfferEntity::toDomain);
    }
}
