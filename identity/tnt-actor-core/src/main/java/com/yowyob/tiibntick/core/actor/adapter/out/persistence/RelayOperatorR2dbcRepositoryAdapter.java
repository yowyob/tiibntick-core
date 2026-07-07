package com.yowyob.tiibntick.core.actor.adapter.out.persistence;

import com.yowyob.tiibntick.core.actor.adapter.out.persistence.entity.RelayOperatorProfileEntity;
import com.yowyob.tiibntick.core.actor.adapter.out.persistence.repository.RelayOperatorSpringDataRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.actor.domain.model.RelayOperatorProfile;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class RelayOperatorR2dbcRepositoryAdapter implements IRelayOperatorRepository {

    private final RelayOperatorSpringDataRepository repository;
    private final R2dbcEntityTemplate entityTemplate;

    public RelayOperatorR2dbcRepositoryAdapter(RelayOperatorSpringDataRepository repository,
                                               R2dbcEntityTemplate entityTemplate) {
        this.repository = repository;
        this.entityTemplate = entityTemplate;
    }

    @Override
    public Mono<Boolean> existsByActorId(UUID tenantId, UUID actorId) {
        return repository.existsByTenantIdAndActorId(tenantId, actorId);
    }

    @Override
    public Mono<RelayOperatorProfile> findById(UUID tenantId, UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<RelayOperatorProfile> findByActorId(UUID tenantId, UUID actorId) {
        return repository.findByTenantIdAndActorId(tenantId, actorId).map(this::toDomain);
    }

    @Override
    public Mono<RelayOperatorProfile> findByHubId(UUID tenantId, UUID hubId) {
        return repository.findByTenantIdAndHubId(tenantId, hubId).map(this::toDomain);
    }

    @Override
    public Flux<RelayOperatorProfile> findByTenantId(UUID tenantId) {
        return repository.findAllByTenantId(tenantId).map(this::toDomain);
    }

    @Override
    public Mono<RelayOperatorProfile> save(RelayOperatorProfile profile) {
        var entity = toEntity(profile);
        return repository.existsById(entity.id())
                .flatMap(exists -> exists
                        ? entityTemplate.update(entity)
                        : entityTemplate.insert(entity))
                .map(this::toDomain);
    }

    private RelayOperatorProfileEntity toEntity(RelayOperatorProfile p) {
        return new RelayOperatorProfileEntity(
                p.id(), p.tenantId(), p.actorId(),
                p.actorStatus().name(), p.kycStatus().name(),
                p.hasLocation() ? p.currentLocation().latitude() : null,
                p.hasLocation() ? p.currentLocation().longitude() : null,
                p.hasLocation() ? p.currentLocation().accuracy() : null,
                p.hasLocation() ? p.currentLocation().timestamp() : null,
                p.hasLocation() ? p.currentLocation().source().name() : null,
                p.rating().score(), p.rating().totalRatings(), p.rating().lastUpdatedAt(),
                ActorJsonMapper.badgesToJson(p.badges()),
                p.createdAt(), p.updatedAt(),
                p.hubId(),
                ActorJsonMapper.availabilitySlotsToJson(p.openingHours()),
                p.declaredCapacityParcels());
    }

    private RelayOperatorProfile toDomain(RelayOperatorProfileEntity e) {
        return RelayOperatorProfile.rehydrate(
                e.id(), e.tenantId(), e.actorId(),
                e.actorStatus(), e.kycStatus(),
                e.locationLat(), e.locationLng(), e.locationAccuracy(),
                e.locationTimestamp(), e.locationSource(),
                e.ratingScore(), e.ratingTotal(), e.ratingUpdatedAt(),
                ActorJsonMapper.badgesFromJson(e.badgesJson()),
                e.createdAt(), e.updatedAt(),
                e.hubId(),
                ActorJsonMapper.availabilitySlotsFromJson(e.openingHoursJson()),
                e.declaredCapacityParcels());
    }
}
