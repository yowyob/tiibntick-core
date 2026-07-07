package com.yowyob.tiibntick.core.delivery.adapter.out.persistence;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper.DeliveryPersistenceMapper;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper.ParcelPersistenceMapper;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcDeliveryRepository;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcParcelRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Persistence adapter — implements {@link DeliveryRepository} using R2DBC.
 *
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class DeliveryRepositoryAdapter implements DeliveryRepository {

    private final R2dbcDeliveryRepository deliveryRepo;
    private final R2dbcParcelRepository parcelRepo;

    @Override
    public Mono<Delivery> save(Delivery delivery) {
        var parcelEntity = ParcelPersistenceMapper.toEntity(delivery.getParcel());
        var deliveryEntity = DeliveryPersistenceMapper.toEntity(delivery, delivery.getParcel());

        // Only INSERT parcel if it is new; existing parcels are managed by AnnouncementRepositoryAdapter
        // to avoid double-save version conflicts on the same transaction.
        return parcelRepo.existsById(parcelEntity.getId())
                .flatMap(parcelExists -> {
                    if (parcelExists) {
                        return Mono.just(parcelEntity);
                    }
                    parcelEntity.setNew(true);
                    return parcelRepo.save(parcelEntity);
                })
                .then(deliveryRepo.existsById(deliveryEntity.getId()))
                .flatMap(deliveryExists -> {
                    deliveryEntity.setNew(!deliveryExists);
                    return deliveryRepo.save(deliveryEntity);
                })
                .map(saved -> DeliveryPersistenceMapper.toDomain(saved,
                        ParcelPersistenceMapper.toDomain(parcelEntity)));
    }

    @Override
    public Mono<Delivery> findById(UUID tenantId, UUID deliveryId) {
        return deliveryRepo.findByTenantIdAndId(tenantId, deliveryId)
                .flatMap(e -> parcelRepo.findById(e.getParcelId())
                        .map(pe -> DeliveryPersistenceMapper.toDomain(e,
                                ParcelPersistenceMapper.toDomain(pe))));
    }

    @Override
    public Mono<Delivery> findByTrackingCode(String trackingCode) {
        return deliveryRepo.findByTrackingCode(trackingCode)
                .flatMap(e -> parcelRepo.findById(e.getParcelId())
                        .map(pe -> DeliveryPersistenceMapper.toDomain(e,
                                ParcelPersistenceMapper.toDomain(pe))));
    }

    @Override
    public Flux<Delivery> findBySenderId(UUID tenantId, UUID senderId) {
        return deliveryRepo.findByTenantIdAndSenderId(tenantId, senderId)
                .flatMap(e -> parcelRepo.findById(e.getParcelId())
                        .map(pe -> DeliveryPersistenceMapper.toDomain(e,
                                ParcelPersistenceMapper.toDomain(pe))));
    }

    @Override
    public Flux<Delivery> findByDeliveryPersonId(UUID tenantId, UUID deliveryPersonId) {
        return deliveryRepo.findByTenantIdAndDeliveryPersonId(tenantId, deliveryPersonId)
                .flatMap(e -> parcelRepo.findById(e.getParcelId())
                        .map(pe -> DeliveryPersistenceMapper.toDomain(e,
                                ParcelPersistenceMapper.toDomain(pe))));
    }

    @Override
    public Flux<Delivery> findByStatus(UUID tenantId, DeliveryStatus status) {
        return deliveryRepo.findByTenantIdAndStatus(tenantId, status.name())
                .flatMap(e -> parcelRepo.findById(e.getParcelId())
                        .map(pe -> DeliveryPersistenceMapper.toDomain(e,
                                ParcelPersistenceMapper.toDomain(pe))));
    }

    @Override
    public Flux<Delivery> findActiveByDeliveryPerson(UUID tenantId, UUID deliveryPersonId) {
        return deliveryRepo.findActiveByTenantIdAndDeliveryPersonId(tenantId, deliveryPersonId)
                .flatMap(e -> parcelRepo.findById(e.getParcelId())
                        .map(pe -> DeliveryPersistenceMapper.toDomain(e,
                                ParcelPersistenceMapper.toDomain(pe))));
    }

    @Override
    public Mono<Void> delete(UUID tenantId, UUID deliveryId) {
        return deliveryRepo.deleteById(deliveryId);
    }

    @Override
    public Mono<Delivery> findByIdNoTenant(UUID deliveryId) {
        return deliveryRepo.findById(deliveryId)
                .flatMap(e -> parcelRepo.findById(e.getParcelId())
                        .map(pe -> DeliveryPersistenceMapper.toDomain(e,
                                ParcelPersistenceMapper.toDomain(pe))));
    }

    @Override
    public reactor.core.publisher.Flux<Delivery> findByPausedByIncidentId(UUID incidentId) {
        return deliveryRepo.findByPausedByIncidentId(incidentId)
                .flatMap(e -> parcelRepo.findById(e.getParcelId())
                        .map(pe -> DeliveryPersistenceMapper.toDomain(e,
                                ParcelPersistenceMapper.toDomain(pe))));
    }
    @Override
    public Flux<Delivery> findByFreelancerOrgId(String freelancerOrgId) {
        return deliveryRepo.findByAssignedFreelancerOrgId(freelancerOrgId)
                .flatMap(entity -> parcelRepo.findById(entity.getParcelId())
                        .map(parcel -> DeliveryPersistenceMapper.toDomain(entity, ParcelPersistenceMapper.toDomain(parcel)))
                        .switchIfEmpty(Mono.just(DeliveryPersistenceMapper.toDomain(entity, null))));
    }

}