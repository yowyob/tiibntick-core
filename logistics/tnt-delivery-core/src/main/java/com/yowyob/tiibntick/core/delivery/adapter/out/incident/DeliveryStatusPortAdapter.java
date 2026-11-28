package com.yowyob.tiibntick.core.delivery.adapter.out.incident;

import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryTransitionExecutor;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponsibleParty;
import org.springframework.transaction.annotation.Transactional;
import java.util.function.Consumer;
import com.yowyob.tiibntick.core.incident.port.outbound.IDeliveryStatusPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * tnt-delivery-core implementation of {@link IDeliveryStatusPort} (outbound port from tnt-incident-core).
 *
 * <p>This adapter is called by tnt-incident-core when:
 * <ul>
 *   <li>An incident is created for a delivery → {@link #pauseDelivery} is called to block
 *       the delivery while the incident is being resolved.</li>
 *   <li>An incident is resolved → {@link #resumeDelivery} is called to restore
 *       the delivery to its previous state with an optional replacement driver.</li>
 *   <li>Incident details need mission context → {@link #getDeliverySnapshot} provides
 *       the delivery/parcel data needed for incident management.</li>
 * </ul>
 *
 * <p>The term "mission" in tnt-incident-core maps directly to the "delivery" aggregate
 * in tnt-delivery-core. The deliveryId parameter equals the delivery UUID.
 *
 * @author MANFOUO Braun
 * @see IDeliveryStatusPort
 */
/**
 * Implements incident-core's {@link IDeliveryStatusPort}: each call applies one row of the delivery table
 * (D13–D20) through the aggregate and publishes its events (delivery and parcel) in the same transaction.
 *
 * <p>A refused transition (wrong state, frozen by a dispute) is logged and ignored, as before lot evt-l2: the
 * incident flow must not fail because the delivery already moved on.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryStatusPortAdapter implements IDeliveryStatusPort {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryTransitionExecutor transitionExecutor;
    private final com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase deliveryQuery;

    /** Row D13. */
    @Override
    @Transactional
    public Mono<Void> markIncidentReported(UUID deliveryId, UUID incidentId) {
        return apply(deliveryId, "markIncidentReported", d -> d.markIncidentReported(incidentId));
    }

    /** Row D15. */
    @Override
    @Transactional
    public Mono<Void> pauseDelivery(UUID deliveryId, UUID incidentId) {
        log.info("Pausing delivery {} due to incident {}", deliveryId, incidentId);
        return apply(deliveryId, "pauseDelivery", d -> d.pauseForIncident(incidentId));
    }

    /** Rows D14/D16/D18, trigger INCIDENT_RESOLVED. */
    @Override
    @Transactional
    public Mono<Void> resumeDelivery(UUID deliveryId, UUID newDriverId, UUID newVehicleId) {
        return resumeDelivery(deliveryId, TRIGGER_INCIDENT_RESOLVED, newDriverId, newVehicleId);
    }

    /** Rows D14/D16/D18 with the cause of the resume. */
    @Override
    @Transactional
    public Mono<Void> resumeDelivery(UUID deliveryId, String trigger, UUID newDriverId, UUID newVehicleId) {
        log.info("Resuming delivery {} ({}) — new driver={}, new vehicle={}",
                deliveryId, trigger, newDriverId, newVehicleId);
        return apply(deliveryId, "resumeDelivery", d -> d.resumeAfterIncident(trigger, newDriverId, newVehicleId));
    }

    /** Row D18 with the hand-over point (QO-11 / D-153); (0, 0) or an invalid point is unknown. */
    @Override
    @Transactional
    public Mono<Void> resumeAfterHandover(UUID deliveryId, UUID newDriverId, UUID newVehicleId,
                                          Double handoverLatitude, Double handoverLongitude) {
        com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates point =
                handoverPoint(handoverLatitude, handoverLongitude);
        log.info("Resuming delivery {} after a hand-over — new driver={}, new vehicle={}, point known={}",
                deliveryId, newDriverId, newVehicleId, point != null);
        return apply(deliveryId, "resumeAfterHandover",
                d -> d.resumeAfterIncident(TRIGGER_INCIDENT_RESOLVED, newDriverId, newVehicleId, point));
    }

    static com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates handoverPoint(Double lat,
                                                                                                  Double lng) {
        if (lat == null || lng == null || (lat == 0.0 && lng == 0.0) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
            return null;
        }
        return new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates(lat, lng);
    }

    /** Row D17. */
    @Override
    @Transactional
    public Mono<Void> startHandover(UUID deliveryId) {
        return apply(deliveryId, "startHandover", d -> d.startHandover());
    }

    /** Row D19. */
    @Override
    @Transactional
    public Mono<Void> abortHandover(UUID deliveryId) {
        return apply(deliveryId, "abortHandover", d -> d.abortHandover());
    }

    /** Row D20. */
    @Override
    @Transactional
    public Mono<Void> failDelivery(UUID deliveryId, UUID incidentId, String responsibleParty) {
        ResponsibleParty party = parseParty(responsibleParty);
        return apply(deliveryId, "failDelivery", d -> d.failAfterIncident(party));
    }

    private Mono<Void> apply(UUID deliveryId, String operation, Consumer<Delivery> transition) {
        return deliveryRepository.findByIdNoTenant(deliveryId)
                .switchIfEmpty(Mono.fromRunnable(() -> log.warn("{}: delivery {} not found", operation, deliveryId)))
                .flatMap(delivery -> {
                    try {
                        transition.accept(delivery);
                    } catch (DeliveryDomainException e) {
                        log.warn("{} refused on delivery {} (status={}): {}", operation, deliveryId,
                                delivery.getStatus(), e.getMessage());
                        return Mono.empty();
                    }
                    return transitionExecutor.saveAndPublish(delivery);
                })
                .then();
    }

    private static ResponsibleParty parseParty(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ResponsibleParty.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown responsible party '{}', recorded as none", value);
            return ResponsibleParty.NONE;
        }
    }

    /**
     * Returns a snapshot of the delivery state for incident management.
     *
     * <p>Provides tnt-incident-core with the data it needs to:
     * <ul>
     *   <li>Display delivery details on the incident management UI</li>
     *   <li>Compute SLA impact (scheduled vs actual delivery times)</li>
     *   <li>Identify affected parcels for blockchain proof creation</li>
     * </ul>
     *
     * @param deliveryId the delivery UUID
     * @return a {@link DeliverySnapshot} with the delivery's current state
     */
    @Override
    public Mono<IDeliveryStatusPort.DeliverySnapshot> getDeliverySnapshot(UUID deliveryId) {
        return deliveryRepository.findByIdNoTenant(deliveryId)
                .map(this::toSnapshot)
                .switchIfEmpty(Mono.fromCallable(() -> {
                    log.warn("getDeliverySnapshot: delivery {} not found — returning empty snapshot", deliveryId);
                    return new IDeliveryStatusPort.DeliverySnapshot(deliveryId, null, null, null, "NOT_FOUND", null, java.util.List.of());
                }));
    }

    @Override
    public Mono<Boolean> isAssignedCourier(UUID deliveryId, UUID actorId) {
        if (actorId == null) {
            return Mono.just(false);
        }
        return deliveryRepository.findByIdNoTenant(deliveryId)
                .filter(d -> !d.getStatus().isTerminal() && d.getDeliveryPersonId() != null)
                .flatMap(d -> actorId.equals(d.getDeliveryPersonId()) ? Mono.just(true)
                        : deliveryQuery.resolveActorIdForDeliveryPerson(d.getTenantId(), d.getDeliveryPersonId())
                                .map(actorId::equals))
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Boolean> courierHoldsParcel(UUID deliveryId) {
        return deliveryRepository.findByIdNoTenant(deliveryId)
                .map(d -> d.getPreviousStatusBeforePause() != null
                        && com.yowyob.tiibntick.core.delivery.domain.statemachine.DeliveryStateMachine.HANDOVER_SOURCES
                        .contains(d.getPreviousStatusBeforePause()))
                .defaultIfEmpty(false);
    }

    // ── Private helpers ────────────────────────────────────────────────

    /**
     * Maps a {@link Delivery} to a {@link IDeliveryStatusPort.DeliverySnapshot} for tnt-incident-core.
     */
    private IDeliveryStatusPort.DeliverySnapshot toSnapshot(Delivery delivery) {
        return new IDeliveryStatusPort.DeliverySnapshot(
                delivery.getId(),
                delivery.getDeliveryPersonId(),
                delivery.getSelectedVehicleId() != null ? UUID.fromString(delivery.getSelectedVehicleId()) : null,
                delivery.getAgencyId(),
                delivery.getStatus().name(),
                delivery.getEstimatedDeliveryTime() != null ? delivery.getEstimatedDeliveryTime() : null,
                delivery.getParcel() != null ? java.util.List.of(delivery.getParcel().getId()) : java.util.List.of()
        );
    }
}
