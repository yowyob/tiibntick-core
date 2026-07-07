package com.yowyob.tiibntick.core.delivery.adapter.out.incident;

import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryEventPublisher;
import com.yowyob.tiibntick.core.incident.port.outbound.IMissionStatusPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * tnt-delivery-core implementation of {@link IMissionStatusPort} (outbound port from tnt-incident-core).
 *
 * <p>This adapter is called by tnt-incident-core when:
 * <ul>
 *   <li>An incident is created for a mission → {@link #pauseMission} is called to block
 *       the delivery while the incident is being resolved.</li>
 *   <li>An incident is resolved → {@link #resumeMission} is called to restore
 *       the delivery to its previous state with an optional replacement driver.</li>
 *   <li>Incident details need mission context → {@link #getMissionSnapshot} provides
 *       the delivery/parcel data needed for incident management.</li>
 * </ul>
 *
 * <p>The term "mission" in tnt-incident-core maps directly to the "delivery" aggregate
 * in tnt-delivery-core. The missionId parameter equals the delivery UUID.
 *
 * @author MANFOUO Braun
 * @see IMissionStatusPort
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionStatusPortAdapter implements IMissionStatusPort {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventPublisher eventPublisher;

    /**
     * Pauses a delivery/mission due to an active incident.
     *
     * <p>Sets the delivery status to {@code PAUSED_BY_INCIDENT} and saves the previous
     * status in {@code previousStatusBeforePause} for restoration on resolution.
     * A {@code DeliveryPausedByIncidentEvent} is published after persistence.
     *
     * @param missionId  the delivery UUID (= mission ID in incident terminology)
     * @param incidentId the UUID of the blocking incident
     * @return empty Mono on success
     */
    @Override
    public Mono<Void> pauseMission(UUID missionId, UUID incidentId) {
        log.info("Pausing delivery/mission {} due to incident {}", missionId, incidentId);
        return deliveryRepository.findByIdNoTenant(missionId)
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("pauseMission: delivery {} not found — incident {} may reference a non-existent mission",
                                missionId, incidentId)))
                .flatMap(delivery -> {
                    try {
                        delivery.pauseForIncident(incidentId);
                        return deliveryRepository.save(delivery)
                                .flatMap(saved -> eventPublisher.publishAll(saved.getDomainEvents())
                                        .doOnSuccess(v -> saved.clearDomainEvents())
                                        .then());
                    } catch (Exception e) {
                        log.warn("Cannot pause delivery {} (already terminal or already paused): {}",
                                missionId, e.getMessage());
                        return Mono.empty();
                    }
                });
    }

    /**
     * Resumes a paused delivery/mission after incident resolution.
     *
     * <p>Restores the delivery to its previous status (saved before pause) and optionally
     * reassigns a new driver and vehicle if a replacement was found during incident resolution.
     *
     * @param missionId      the delivery UUID
     * @param newDriverId    replacement driver UUID (null if the original driver resumes)
     * @param newVehicleId   replacement vehicle UUID (null if unchanged)
     * @return empty Mono on success
     */
    @Override
    public Mono<Void> resumeMission(UUID missionId, UUID newDriverId, UUID newVehicleId) {
        log.info("Resuming delivery/mission {} — new driver={}, new vehicle={}",
                missionId, newDriverId, newVehicleId);
        return deliveryRepository.findByIdNoTenant(missionId)
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("resumeMission: delivery {} not found", missionId)))
                .flatMap(delivery -> {
                    try {
                        delivery.resumeFromIncident(newDriverId, newVehicleId);
                        return deliveryRepository.save(delivery)
                                .flatMap(saved -> eventPublisher.publishAll(saved.getDomainEvents())
                                        .doOnSuccess(v -> saved.clearDomainEvents())
                                        .then());
                    } catch (Exception e) {
                        log.warn("Cannot resume delivery {} from incident pause: {}",
                                missionId, e.getMessage());
                        return Mono.empty();
                    }
                });
    }

    /**
     * Returns a snapshot of the delivery/mission state for incident management.
     *
     * <p>Provides tnt-incident-core with the data it needs to:
     * <ul>
     *   <li>Display delivery details on the incident management UI</li>
     *   <li>Compute SLA impact (scheduled vs actual delivery times)</li>
     *   <li>Identify affected parcels for blockchain proof creation</li>
     * </ul>
     *
     * @param missionId the delivery UUID
     * @return a {@link MissionSnapshot} with the delivery's current state
     */
    @Override
    public Mono<IMissionStatusPort.MissionSnapshot> getMissionSnapshot(UUID missionId) {
        return deliveryRepository.findByIdNoTenant(missionId)
                .map(this::toSnapshot)
                .switchIfEmpty(Mono.fromCallable(() -> {
                    log.warn("getMissionSnapshot: delivery {} not found — returning empty snapshot", missionId);
                    return new IMissionStatusPort.MissionSnapshot(missionId, null, null, null, "NOT_FOUND", null, java.util.List.of());
                }));
    }

    // ── Private helpers ────────────────────────────────────────────────

    /**
     * Maps a {@link Delivery} to a {@link IMissionStatusPort.MissionSnapshot} for tnt-incident-core.
     */
    private IMissionStatusPort.MissionSnapshot toSnapshot(Delivery delivery) {
        return new IMissionStatusPort.MissionSnapshot(
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
