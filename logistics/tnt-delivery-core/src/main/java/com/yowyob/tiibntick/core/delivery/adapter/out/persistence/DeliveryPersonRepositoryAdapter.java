package com.yowyob.tiibntick.core.delivery.adapter.out.persistence;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper.DeliveryPersonPersistenceMapper;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcDeliveryPersonRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryPersonRepository;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryPersonStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Persistence adapter for {@code DeliveryPersonRepository}.
 *
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class DeliveryPersonRepositoryAdapter implements DeliveryPersonRepository {

    private final R2dbcDeliveryPersonRepository repo;

    @Override
    public Mono<DeliveryPerson> save(DeliveryPerson dp) {
        var entity = DeliveryPersonPersistenceMapper.toEntity(dp);
        return repo.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return repo.save(entity);
                })
                .map(DeliveryPersonPersistenceMapper::toDomain);
    }

    @Override
    public Mono<DeliveryPerson> findById(UUID tenantId, UUID deliveryPersonId) {
        return repo.findByTenantIdAndId(tenantId, deliveryPersonId)
                .map(DeliveryPersonPersistenceMapper::toDomain);
    }

    @Override
    public Mono<DeliveryPerson> findByActorId(UUID tenantId, UUID actorId) {
        return repo.findByTenantIdAndActorId(tenantId, actorId)
                .map(DeliveryPersonPersistenceMapper::toDomain);
    }

    @Override
    public Flux<DeliveryPerson> findAvailableNear(UUID tenantId, GeoCoordinates center, double radiusKm) {
        double latDeg = radiusKm / 111.0;
        double lonDeg = radiusKm / (111.0 * Math.cos(Math.toRadians(center.latitude())));
        return repo.findAvailableNear(tenantId,
                center.latitude() - latDeg, center.latitude() + latDeg,
                center.longitude() - lonDeg, center.longitude() + lonDeg)
                .map(DeliveryPersonPersistenceMapper::toDomain);
    }

    @Override
    public Flux<DeliveryPerson> findByStatus(UUID tenantId, DeliveryPersonStatus status) {
        return repo.findByTenantIdAndStatus(tenantId, status.name())
                .map(DeliveryPersonPersistenceMapper::toDomain);
    }
}
