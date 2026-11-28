package com.yowyob.tiibntick.core.delivery.adapter.out.persistence;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.DeliveryEntity;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import java.util.Arrays;
import java.util.List;

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
    private final ParcelWriter parcelWriter;

    /** Derived from the transition table (no status list written by hand, decision [C bis]). */
    static final List<String> ACTIVE_STATUSES = Arrays.stream(DeliveryStatus.values())
            .filter(DeliveryStatus::isActive).map(Enum::name).toList();
    static final List<String> TERMINAL_STATUSES = Arrays.stream(DeliveryStatus.values())
            .filter(DeliveryStatus::isTerminal).map(Enum::name).toList();

    /**
     * Saves the parcel (if it changed) then the delivery, copies the written versions back onto the domain objects and
     * returns the <b>same</b> aggregate: its recorded events stay available for publication in the same transaction
     * (lot evt-l2 — the remapped copy returned before lost them).
     */
    @Override
    public Mono<Delivery> save(Delivery delivery) {
        // Lazy: the entity is built at subscription, after any save chained before it (versions up to date).
        return Mono.defer(() -> doSave(delivery));
    }

    private Mono<Delivery> doSave(Delivery delivery) {
        var deliveryEntity = DeliveryPersistenceMapper.toEntity(delivery, delivery.getParcel());
        Mono<Boolean> isNew = delivery.getVersion() == null
                ? deliveryRepo.existsById(deliveryEntity.getId()).map(exists -> !exists)
                : Mono.just(false);
        return parcelWriter.saveIfPending(delivery.getParcel())
                .then(isNew)
                .flatMap(newRow -> {
                    deliveryEntity.setNew(newRow);
                    return deliveryRepo.save(deliveryEntity);
                })
                .map(saved -> {
                    delivery.markPersisted(saved.getVersion());
                    return delivery;
                });
    }

    @Override
    public Mono<Delivery> findById(UUID tenantId, UUID deliveryId) {
        return deliveryRepo.findByTenantIdAndId(tenantId, deliveryId).flatMap(this::hydrate);
    }

    @Override
    public Mono<Delivery> findByTrackingCode(String trackingCode) {
        return parcelRepo.findByTrackingCode(trackingCode)
                .flatMap(pe -> {
                    Parcel parcel = ParcelPersistenceMapper.toDomain(pe);
                    Flux<DeliveryEntity> all = deliveryRepo.findByParcelIdNoTenant(pe.getId());
                    Mono<DeliveryEntity> shown = parcel.getCurrentDeliveryId() != null
                            ? all.filter(e -> e.getId().equals(parcel.getCurrentDeliveryId())).next()
                            : all.next(); // ordered created_at DESC: the latest
                    return shown.switchIfEmpty(all.next())
                            .map(e -> DeliveryPersistenceMapper.toDomain(e, parcel));
                })
                // codes printed before D-136 were carried by the delivery
                .switchIfEmpty(Mono.defer(() -> deliveryRepo.findByTrackingCode(trackingCode).flatMap(this::hydrate)));
    }

    @Override
    public Flux<Delivery> findHistoryByTrackingCode(String trackingCode) {
        return parcelRepo.findByTrackingCode(trackingCode)
                .switchIfEmpty(Mono.defer(() -> deliveryRepo.findByTrackingCode(trackingCode)
                        .flatMap(e -> parcelRepo.findById(e.getParcelId()))))
                .flatMapMany(pe -> {
                    Parcel parcel = ParcelPersistenceMapper.toDomain(pe);
                    return deliveryRepo.findByParcelIdNoTenant(pe.getId())
                            .map(e -> DeliveryPersistenceMapper.toDomain(e, parcel))
                            .collectList()
                            .flatMapMany(list -> Flux.fromIterable(list.reversed()));
                });
    }

    @Override
    public Flux<Delivery> findRelayStorageExpired(java.time.Instant now, int limit) {
        return deliveryRepo.findRelayStorageExpired(now, limit).concatMap(this::hydrate);
    }

    @Override
    public Flux<Delivery> findRelayStorageReminderDue(java.time.Instant now, int limit) {
        return deliveryRepo.findRelayStorageReminderDue(now, limit).concatMap(this::hydrate);
    }

    @Override
    public Flux<Delivery> findWatchCandidates(int limit) {
        return deliveryRepo.findWatchCandidates(limit).concatMap(this::hydrate);
    }

    @Override
    public Flux<Delivery> findAtRelayHub(UUID hubId) {
        return deliveryRepo.findAtRelayHub(hubId).concatMap(this::hydrate);
    }

    private Mono<Delivery> hydrate(DeliveryEntity e) {
        return parcelRepo.findById(e.getParcelId())
                .map(pe -> DeliveryPersistenceMapper.toDomain(e, ParcelPersistenceMapper.toDomain(pe)));
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
        return deliveryRepo.findActiveByTenantIdAndDeliveryPersonId(tenantId, deliveryPersonId, ACTIVE_STATUSES)
                .flatMap(e -> parcelRepo.findById(e.getParcelId())
                        .map(pe -> DeliveryPersistenceMapper.toDomain(e,
                                ParcelPersistenceMapper.toDomain(pe))));
    }

    @Override
    public Mono<Long> countByDeliveryPersonId(UUID tenantId, UUID deliveryPersonId) {
        return deliveryRepo.countByTenantIdAndDeliveryPersonId(tenantId, deliveryPersonId);
    }

    @Override
    public Mono<Long> countNonTerminalByDeliveryPersonId(UUID tenantId, UUID deliveryPersonId) {
        return deliveryRepo.countNonTerminalByTenantIdAndDeliveryPersonId(tenantId, deliveryPersonId, TERMINAL_STATUSES);
    }

    @Override
    public Mono<Long> countByDeliveryPersonIdAndStatus(UUID tenantId, UUID deliveryPersonId,
                                                       com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus status) {
        return deliveryRepo.countByTenantIdAndDeliveryPersonIdAndStatus(tenantId, deliveryPersonId, status.name());
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
    public reactor.core.publisher.Flux<Delivery> findByParcelId(UUID parcelId, UUID tenantId) {
        return deliveryRepo.findByParcelIdNoTenant(parcelId)
                .filter(e -> tenantId == null || e.getTenantId() == null || tenantId.equals(e.getTenantId()))
                .concatMap(e -> parcelRepo.findById(e.getParcelId())
                        .map(pe -> DeliveryPersistenceMapper.toDomain(e, ParcelPersistenceMapper.toDomain(pe))));
    }

    @Override
    public reactor.core.publisher.Flux<Delivery> findByParcelIdNoTenant(UUID parcelId) {
        return deliveryRepo.findByParcelIdNoTenant(parcelId)
                .concatMap(e -> parcelRepo.findById(e.getParcelId())
                        .map(pe -> DeliveryPersistenceMapper.toDomain(e, ParcelPersistenceMapper.toDomain(pe))));
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