package com.yowyob.tiibntick.core.actor.adapter.out.persistence;

import com.yowyob.tiibntick.core.actor.adapter.out.persistence.entity.ClientProfileEntity;
import com.yowyob.tiibntick.core.actor.adapter.out.persistence.repository.ClientProfileSpringDataRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IClientProfileRepository;
import com.yowyob.tiibntick.core.actor.domain.model.ClientProfile;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class ClientProfileR2dbcRepositoryAdapter implements IClientProfileRepository {

    private final ClientProfileSpringDataRepository repository;
    private final R2dbcEntityTemplate entityTemplate;

    public ClientProfileR2dbcRepositoryAdapter(ClientProfileSpringDataRepository repository,
                                               R2dbcEntityTemplate entityTemplate) {
        this.repository = repository;
        this.entityTemplate = entityTemplate;
    }

    @Override
    public Mono<Boolean> existsByActorId(UUID tenantId, UUID actorId) {
        return repository.existsByTenantIdAndActorId(tenantId, actorId);
    }

    @Override
    public Mono<ClientProfile> findByActorId(UUID tenantId, UUID actorId) {
        return repository.findByTenantIdAndActorId(tenantId, actorId).map(this::toDomain);
    }

    @Override
    public Mono<ClientProfile> save(ClientProfile profile) {
        var entity = toEntity(profile);
        return repository.existsById(entity.id())
                .flatMap(exists -> exists
                        ? entityTemplate.update(entity)
                        : entityTemplate.insert(entity))
                .map(this::toDomain);
    }

    private ClientProfileEntity toEntity(ClientProfile p) {
        return new ClientProfileEntity(
                p.id(), p.tenantId(), p.actorId(),
                p.actorStatus().name(), p.kycStatus().name(),
                p.hasLocation() ? p.currentLocation().latitude() : null,
                p.hasLocation() ? p.currentLocation().longitude() : null,
                p.rating().score(), p.rating().totalRatings(), p.rating().lastUpdatedAt(),
                ActorJsonMapper.badgesToJson(p.badges()),
                p.createdAt(), p.updatedAt(),
                ActorJsonMapper.favoriteAddressIdsToJson(p.favoriteAddressIds()),
                p.loyaltyScore(),
                p.preferredPaymentMethod());
    }

    private ClientProfile toDomain(ClientProfileEntity e) {
        return ClientProfile.rehydrate(
                e.id(), e.tenantId(), e.actorId(),
                e.actorStatus(), e.kycStatus(),
                e.locationLat(), e.locationLng(), null,
                null, null,
                e.ratingScore(), e.ratingTotal(), e.ratingUpdatedAt(),
                ActorJsonMapper.badgesFromJson(e.badgesJson()),
                e.createdAt(), e.updatedAt(),
                ActorJsonMapper.favoriteAddressIdsFromJson(e.favoriteAddressIdsJson()),
                e.loyaltyScore(),
                e.preferredPaymentMethod());
    }
}
