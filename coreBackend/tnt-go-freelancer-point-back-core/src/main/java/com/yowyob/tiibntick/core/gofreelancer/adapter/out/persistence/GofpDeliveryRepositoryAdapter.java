package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort.CoreDeliveryState;
import com.yowyob.tiibntick.core.gofreelancer.application.service.CoreDeliveryAssembler;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Outbound adapter of the gofp delivery extension rows.
 *
 * <p>The table holds only what the product adds to a delivery (OTP hashes, handover codes, receive
 * code, notes, links). The delivery's own facts &mdash; status, assigned delivery person, real instants,
 * agreed price and windows &mdash; are owned by {@code tnt-delivery-core}; this adapter reads them there:
 * every row it returns is filled from the core, and every query that filters on those facts is answered
 * by the core and then matched with the extension rows by id. Nothing about them is ever written to the
 * gofp table.
 */
@Component
@RequiredArgsConstructor
public class GofpDeliveryRepositoryAdapter implements DeliveryRepository {

    private static final java.util.Set<DeliveryStatus> OCCUPYING_TRUNK =
            java.util.EnumSet.of(DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT);

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.DeliveryRepository r2dbcRepository;
    private final IDeliveryLifecyclePort core;
    private final TenantContextHolder tenantContextHolder;

    @Override
    public Mono<Delivery> save(Delivery delivery) {
        // Only the extension columns are mapped: the core facts carried by the row are left as they were.
        return r2dbcRepository.save(delivery).map(saved -> carryCoreFacts(delivery, saved));
    }

    @Override
    public Mono<Delivery> findById(UUID id) {
        return r2dbcRepository.findById(id).flatMap(this::hydrated);
    }

    @Override
    public Mono<Delivery> findByAnnouncementId(UUID announcementId) {
        return r2dbcRepository.findByAnnouncementId(announcementId).flatMap(this::hydrated);
    }

    @Override
    public Mono<Delivery> findByDeliveryNeedId(UUID deliveryNeedId) {
        return r2dbcRepository.findByDeliveryNeedId(deliveryNeedId).flatMap(this::hydrated);
    }

    @Override
    /**
     * The delivery carrying this parcel: first the core's own parcel link ({@code tnt_deliveries.parcel_id},
     * read through delivery-core), which covers every delivery whatever parcel the core created for it;
     * then, for deposits re-keyed by the migration on the parcel the need registered (which the core may
     * have copied into a parcel of its own before lot L1), the delivery of the need holding that parcel.
     */
    public Mono<Delivery> findByParcelId(UUID parcelId) {
        if (parcelId == null) {
            return Mono.empty();
        }
        return core.findStatesByParcelAnyTenant(parcelId)
                .concatMap(state -> r2dbcRepository.findById(state.deliveryId())
                        .map(row -> CoreDeliveryAssembler.hydrate(row, state)))
                .next()
                .switchIfEmpty(Mono.defer(() -> r2dbcRepository.findByParcelId(parcelId).flatMap(this::hydrated)));
    }

    @Override
    public Flux<Delivery> findAllByFreelancerId(UUID freelancerId) {
        return tenant().flatMapMany(tenantId -> extensionsOf(core.findByDeliveryPerson(tenantId, freelancerId)));
    }

    @Override
    public Flux<Delivery> findAllByStatus(DeliveryStatus status) {
        return tenant().flatMapMany(tenantId -> extensionsOf(core.findByStatus(tenantId, status)));
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }

    @Override
    public Flux<Delivery> findActiveByFreelancerId(UUID freelancerId) {
        return tenant().flatMapMany(tenantId -> extensionsOf(core.findByDeliveryPerson(tenantId, freelancerId)
                .filter(s -> OCCUPYING_TRUNK.contains(s.statusValue()))));
    }

    @Override
    public Mono<Long> countUnfinishedByFreelancerIds(Collection<UUID> freelancerIds) {
        if (freelancerIds == null || freelancerIds.isEmpty()) {
            return Mono.just(0L);
        }
        return tenant().flatMap(tenantId -> core.countUnfinished(tenantId, freelancerIds)).defaultIfEmpty(0L);
    }

    @Override
    public Mono<Long> countDeliveredByFreelancerIds(Collection<UUID> freelancerIds) {
        if (freelancerIds == null || freelancerIds.isEmpty()) {
            return Mono.just(0L);
        }
        return tenant().flatMap(tenantId -> core.countDelivered(tenantId, freelancerIds)).defaultIfEmpty(0L);
    }

    /**
     * Volume (m³) occupied in a courier's trunk by the parcels of the deliveries he carries (PICKED_UP or
     * IN_TRANSIT), from the core's parcel dimensions. The core stores centimetres, so the product of the
     * three dimensions is divided by one million; deliveries unknown to gofp are not counted.
     */
    @Override
    public Mono<Double> sumOccupiedVolumeM3ByFreelancerId(UUID freelancerId) {
        return tenant().flatMapMany(tenantId -> core.findByDeliveryPerson(tenantId, freelancerId)
                        .filter(s -> OCCUPYING_TRUNK.contains(s.statusValue()))
                        .collectList()
                        .flatMapMany(states -> {
                            Map<UUID, CoreDeliveryState> byId = states.stream()
                                    .collect(Collectors.toMap(CoreDeliveryState::deliveryId, Function.identity(), (a, b) -> a));
                            return r2dbcRepository.findAllById(byId.keySet()).map(row -> byId.get(row.getId()));
                        }))
                .map(s -> s.parcelVolumeM3() != null ? s.parcelVolumeM3() : 0.0)
                .reduce(0.0, Double::sum);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private Mono<UUID> tenant() {
        return tenantContextHolder.currentTenantId().defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT);
    }

    /** The extension row filled from the core, or the bare row when the core does not know the delivery. */
    private Mono<Delivery> hydrated(Delivery row) {
        return core.findStateAnyTenant(row.getId())
                .map(state -> CoreDeliveryAssembler.hydrate(row, state))
                .defaultIfEmpty(row);
    }

    /** The extension rows of the deliveries the core listed, each filled from its core state; core order kept. */
    private Flux<Delivery> extensionsOf(Flux<CoreDeliveryState> states) {
        return states.collectList().flatMapMany(list -> {
            if (list.isEmpty()) {
                return Flux.empty();
            }
            Map<UUID, Delivery> rows = new java.util.HashMap<>();
            return r2dbcRepository.findAllById(list.stream().map(CoreDeliveryState::deliveryId).toList())
                    .doOnNext(row -> rows.put(row.getId(), row))
                    .thenMany(Flux.defer(() -> Flux.fromIterable(list)
                            .filter(s -> rows.containsKey(s.deliveryId()))
                            .map(s -> CoreDeliveryAssembler.hydrate(rows.get(s.deliveryId()), s))));
        });
    }

    /** {@code saved} is the persisted row; it keeps the core facts the caller's instance carried. */
    private static Delivery carryCoreFacts(Delivery caller, Delivery saved) {
        if (caller == saved) {
            return saved;
        }
        return saved.readThrough(caller.getStatus(), caller.getFreelancerId(),
                caller.getTarif() != null ? java.math.BigDecimal.valueOf(caller.getTarif()) : null,
                caller.getPickupMinTime(), caller.getPickupMaxTime(),
                caller.getDeliveryMinTime(), caller.getDeliveryMaxTime(),
                caller.getActualPickupTime(), caller.getActualDeliveryTime());
    }
}
