package com.yowyob.tiibntick.core.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.yowyob.tiibntick.core.delivery.application.port.out.*;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryNotFoundException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RelayDeposit;
import com.yowyob.tiibntick.core.delivery.domain.policy.DeliveryCostPolicy;
import com.yowyob.tiibntick.core.geo.application.port.in.ICheckHubAcceptsDepositsUseCase;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import com.yowyob.tiibntick.core.route.application.port.in.IUpdateEtaUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Application service orchestrating delivery state machine transitions.
 *
 * <p>Each method: load aggregate → apply domain operation → persist → publish events.
 *
 * <p>Use-case methods that persist the aggregate and then publish its domain events are
 * {@code @Transactional} so that the delivery row and the outbox envelope/entry written by
 * {@link DeliveryEventPublisher} (Chantier C · Audit n°3 · P5) commit atomically — a business
 * save can no longer succeed while its event is silently lost.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryLifecycleService implements DeliveryLifecycleUseCase {

    /** Average speed (km/h) assumed when seeding tnt-route-core's Kalman filter at transit start. */
    private static final double INITIAL_KALMAN_SPEED_KMH = 25.0;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryPersonRepository deliveryPersonRepository;
    private final EtaComputationPort etaComputationPort;
    private final DeliveryProofAnchorPort deliveryProofAnchorPort;
    private final IUpdateEtaUseCase updateEtaUseCase;
    private final DeliveryThresholdsPort thresholds;
    private final DeliveryTransitionExecutor transitionExecutor;

    /**
     * Approval of the hub in geo-core (D-140): a hub that is not APPROVED refuses every new deposit, checked
     * <b>before</b> row D7 so that a refused deposit changes nothing. Optional: a hub unknown to geo-core (Agency hub,
     * D-143) is not governed, and the service still builds without geo-core in tests.
     */
    private ICheckHubAcceptsDepositsUseCase hubDepositGuard;

    @Autowired(required = false)
    public void setHubDepositGuard(ICheckHubAcceptsDepositsUseCase hubDepositGuard) {
        this.hubDepositGuard = hubDepositGuard;
    }

    @Override
    @Transactional
    @RequirePermission(resource = "delivery", action = "confirm")
    public Mono<Delivery> confirmPickup(ConfirmPickupCommand cmd) {
        log.info("Confirming pickup delivery={} by person={}", cmd.deliveryId(), cmd.deliveryPersonId());
        return loadDelivery(cmd.tenantId(), cmd.deliveryId())
                .flatMap(delivery -> resolveDeliveryPersonId(cmd.tenantId(), cmd.deliveryPersonId())
                        .flatMap(dpId -> {
                            validateDeliveryPerson(delivery, dpId);
                            delivery.confirmPickup();
                            return saveAndPublish(delivery);
                        }));
    }

    @Override
    @Transactional
    @RequirePermission(resource = "mission", action = "start")
    public Mono<Delivery> startTransit(StartTransitCommand cmd) {
        log.info("Starting transit delivery={}", cmd.deliveryId());
        return loadDelivery(cmd.tenantId(), cmd.deliveryId())
                .flatMap(delivery -> resolveDeliveryPersonId(cmd.tenantId(), cmd.deliveryPersonId())
                        .flatMap(dpId -> {
                            validateDeliveryPerson(delivery, dpId);
                            var origin = cmd.currentPosition() != null
                                    ? cmd.currentPosition()
                                    : delivery.getPickupAddress().coordinates();
                            var dest = delivery.getDeliveryAddress().coordinates();
                            double distKm = origin.haversineDistanceTo(dest);

                            return etaComputationPort.computeInitial(origin, dest, distKm)
                                    .flatMap(eta -> {
                                        delivery.startTransit(eta);
                                        return saveAndPublish(delivery);
                                    })
                                    .flatMap(saved -> bootstrapKalmanState(saved.getId(), distKm)
                                            .thenReturn(saved));
                        }));
    }

    /**
     * Seeds tnt-route-core's Kalman filter state for this mission so that subsequent GPS
     * pings (routed through tnt-realtime-core's {@code GpsPingProcessor}) can refine the
     * live ETA. Best-effort: a failure here must never block the transit-start transaction —
     * it only degrades the live-tracking broadcast, not delivery business correctness.
     */
    private Mono<Void> bootstrapKalmanState(java.util.UUID deliveryId, double totalDistanceKm) {
        return updateEtaUseCase.computeInitialEta(deliveryId.toString(), totalDistanceKm, INITIAL_KALMAN_SPEED_KMH)
                .then()
                .onErrorResume(ex -> {
                    log.warn("Failed to seed Kalman ETA state for delivery={}: {}", deliveryId, ex.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Row D7. The storage deadline is computed from the thresholds (relay owner / independent relay / platform) and
     * the client request (QO-30: effective = min(request, relay storage)).
     */
    @Override
    @Transactional
    public Mono<Delivery> depositAtRelayHub(DepositAtRelayHubCommand cmd) {
        log.info("Depositing delivery={} at relay hub={}", cmd.deliveryId(), cmd.relayHubId());
        Instant depositedAt = Instant.now();
        Mono<Void> approved = hubDepositGuard == null ? Mono.empty()
                : hubDepositGuard.requireAcceptsDeposits(cmd.relayHubId());
        return approved.then(Mono.defer(() -> loadDelivery(cmd.tenantId(), cmd.deliveryId())))
                .flatMap(delivery -> resolveDeliveryPersonId(cmd.tenantId(), cmd.deliveryPersonId())
                        .flatMap(dpId -> {
                            validateDeliveryPerson(delivery, dpId);
                            boolean destination = delivery.getDestinationHubId() == null
                                    || delivery.getDestinationHubId().equals(cmd.relayHubId());
                            Mono<RelayDeposit> deposit = destination
                                    ? thresholds.relayStorageDeadline(cmd.relayHubId(), depositedAt,
                                                    cmd.requestedStorageDays())
                                            .map(d -> new RelayDeposit(cmd.relayHubId(), cmd.hubPackageEntryId(),
                                                    depositedAt, true, cmd.requestedStorageDays(),
                                                    d.effectiveStorageDays(), d.expiresAt(), d.reminderAt()))
                                    : Mono.just(new RelayDeposit(cmd.relayHubId(), cmd.hubPackageEntryId(),
                                            depositedAt, false, cmd.requestedStorageDays(), null, null, null));
                            return deposit.flatMap(d -> {
                                delivery.depositAtRelayHub(d);
                                return saveAndPublish(delivery);
                            });
                        }));
    }

    /** Row D6. */
    @Override
    @Transactional
    public Mono<Delivery> resumeFromRelayHub(ResumeFromRelayHubCommand cmd) {
        log.info("Resuming delivery={} from relay hub", cmd.deliveryId());
        return loadDelivery(cmd.tenantId(), cmd.deliveryId())
                .flatMap(delivery -> resolveDeliveryPersonId(cmd.tenantId(), cmd.deliveryPersonId())
                        .flatMap(dpId -> {
                            validateDeliveryPerson(delivery, dpId);
                            var dest = delivery.getDeliveryAddress().coordinates();
                            var origin = delivery.getPickupAddress().coordinates();
                            double distKm = origin.haversineDistanceTo(dest);
                            return etaComputationPort.computeInitial(origin, dest, distKm)
                                    .flatMap(eta -> {
                                        delivery.resumeFromRelayHub(eta);
                                        return saveAndPublish(delivery);
                                    });
                        }));
    }

    /** Row D8: the attempt maximum is read from the thresholds at the time of the transition. */
    @Override
    @Transactional
    @RequirePermission(resource = "mission", action = "start")
    public Mono<Delivery> startFinalLeg(StartFinalLegCommand cmd) {
        log.info("Starting final leg delivery={}", cmd.deliveryId());
        return loadDelivery(cmd.tenantId(), cmd.deliveryId())
                .flatMap(delivery -> resolveDeliveryPersonId(cmd.tenantId(), cmd.deliveryPersonId())
                        .flatMap(dpId -> {
                            validateDeliveryPerson(delivery, dpId);
                            return maxAttempts(delivery).flatMap(max -> {
                                delivery.startFinalLeg(delivery.getCurrentEta(), max);
                                return saveAndPublish(delivery);
                            });
                        }));
    }

    @Override
    public Mono<EtaEstimate> updateLocation(UpdateDeliveryLocationCommand cmd) {
        return loadDelivery(cmd.tenantId(), cmd.deliveryId())
                .filter(d -> d.getStatus().isActive())
                .switchIfEmpty(Mono.error(new DeliveryDomainException(
                    "Cannot update location for non-active delivery: " + cmd.deliveryId())))
                .flatMap(delivery -> resolveDeliveryPersonId(cmd.tenantId(), cmd.deliveryPersonId())
                        .flatMap(dpId -> {
                            validateDeliveryPerson(delivery, dpId);
                            var dest = delivery.getDeliveryAddress().coordinates();
                            EtaEstimate previousEta = delivery.getCurrentEta();

                            Mono<EtaEstimate> etaMono = previousEta != null
                                    ? etaComputationPort.refineWithKalman(cmd.currentPosition(), dest, previousEta)
                                    : etaComputationPort.computeInitial(cmd.currentPosition(), dest,
                                        cmd.currentPosition().haversineDistanceTo(dest));

                            return etaMono.flatMap(eta -> {
                                delivery.updateEta(eta);
                                return deliveryPersonRepository
                                        .findById(cmd.tenantId(), dpId)
                                        .flatMap(dp -> {
                                            dp.updateLocation(cmd.currentPosition());
                                            return deliveryPersonRepository.save(dp);
                                        })
                                        .then(saveAndPublish(delivery))
                                        .thenReturn(eta);
                            });
                        }));
    }

    @Override
    @Transactional
    @RequirePermission(resource = "mission", action = "complete")
    public Mono<Delivery> completeDelivery(CompleteDeliveryCommand cmd) {
        log.info("Completing delivery={}", cmd.deliveryId());
        return loadDelivery(cmd.tenantId(), cmd.deliveryId())
                .flatMap(delivery -> resolveDeliveryPersonId(cmd.tenantId(), cmd.deliveryPersonId())
                        .flatMap(dpId -> {
                            validateDeliveryPerson(delivery, dpId);
                            var finalCost = delivery.getEstimatedCost() != null
                                    ? delivery.getEstimatedCost()
                                    : DeliveryCostPolicy.computeSimple(
                                        delivery.getEstimatedDistanceKm(), 30,
                                        null, delivery.getUrgency());

                            delivery.complete(finalCost);

                            return saveAndPublish(delivery)
                                    .flatMap(saved -> anchorProofIfAvailable(cmd, saved).thenReturn(saved))
                                    .flatMap(saved -> deliveryPersonRepository
                                            .findById(cmd.tenantId(), dpId)
                                            .flatMap(dp -> {
                                                dp.recordDeliveryCompleted();
                                                return deliveryPersonRepository.save(dp);
                                            })
                                            .thenReturn(saved));
                        }));
    }

    /**
     * Anchors the delivery proof on the blockchain via tnt-trust-core, only when the
     * caller supplied real proof data (photo hash + GPS) — see {@link CompleteDeliveryCommand}.
     * Best-effort: a trust-anchoring failure is logged and swallowed, never fails delivery completion.
     */
    private Mono<Void> anchorProofIfAvailable(CompleteDeliveryCommand cmd, Delivery delivery) {
        if (cmd.photoHash() == null || cmd.gpsLat() == null || cmd.gpsLng() == null) {
            return Mono.empty();
        }
        final var payload = new DeliveryProofAnchorPayload(
                cmd.tenantId(), delivery.getId(), delivery.getParcel().getId(), cmd.deliveryPersonId(),
                cmd.photoHash(), cmd.signatureHash(), cmd.gpsLat(), cmd.gpsLng(), Instant.now());
        return deliveryProofAnchorPort.anchor(payload)
                .onErrorResume(e -> {
                    log.warn("Trust anchoring failed for delivery={}, continuing anyway",
                            cmd.deliveryId(), e);
                    return Mono.empty();
                });
    }

    /**
     * Rows D9/D10: a failed hand-over attempt. The delivery fails at once on a refusal by the recipient, or when the
     * maximum (read now from the thresholds) is reached; the parcel then goes back (QO-4: return draft).
     */
    @Override
    @Transactional
    @RequirePermission(resource = "mission", action = "complete")
    public Mono<Delivery> recordFailedAttempt(RecordFailedAttemptCommand cmd) {
        log.warn("Failed attempt delivery={} reason={}", cmd.deliveryId(), cmd.reason());
        return loadDelivery(cmd.tenantId(), cmd.deliveryId())
                .flatMap(delivery -> resolveDeliveryPersonId(cmd.tenantId(), cmd.deliveryPersonId())
                        .flatMap(dpId -> {
                            validateDeliveryPerson(delivery, dpId);
                            return maxAttempts(delivery).flatMap(max -> {
                                delivery.recordFailedAttempt(cmd.reason(), max);
                                boolean failed = delivery.getStatus().isTerminal();
                                return saveAndPublish(delivery)
                                        .flatMap(saved -> !failed ? Mono.just(saved) : deliveryPersonRepository
                                                .findById(cmd.tenantId(), dpId)
                                                .flatMap(dp -> {
                                                    dp.recordDeliveryFailed();
                                                    return deliveryPersonRepository.save(dp);
                                                })
                                                .thenReturn(saved));
                            });
                        }));
    }

    private Mono<Integer> maxAttempts(Delivery delivery) {
        return thresholds.forAgency(delivery.getAgencyId())
                .map(DeliveryThresholdsPort.Thresholds::maxDeliveryAttempts)
                .defaultIfEmpty(delivery.getMaxAttempts());
    }

    @Override
    @Transactional
    @RequirePermission(resource = "delivery", action = "cancel")
    public Mono<Delivery> cancelDelivery(CancelDeliveryCommand cmd) {
        log.info("Cancelling delivery={} by={} reason={}", cmd.deliveryId(), cmd.requesterId(), cmd.reason());
        return loadDelivery(cmd.tenantId(), cmd.deliveryId())
                .flatMap(delivery -> {
                    delivery.cancel(cmd.reason());
                    return saveAndPublish(delivery);
                });
    }

    @Override
    @Transactional
    public Mono<Delivery> recordAgreedTerms(RecordAgreedTermsCommand cmd) {
        log.info("Recording agreed terms on delivery={} tenant={}", cmd.deliveryId(), cmd.tenantId());
        return loadDelivery(cmd.tenantId(), cmd.deliveryId())
                .flatMap(delivery -> {
                    delivery.recordAgreedTerms(cmd.amount(), cmd.currency(),
                            cmd.pickupWindowStart(), cmd.pickupWindowEnd(),
                            cmd.deliveryWindowStart(), cmd.deliveryWindowEnd());
                    // D-147, D-151: the delivery to a relay is now priced -> its return guarantee is frozen
                    return transitionExecutor.holdReturnGuarantee(delivery).flatMap(deliveryRepository::save);
                });
    }

    // ── : FreelancerOrg integration ──────────────────────────────────

    @Override
    @Transactional
    public Mono<Delivery> assignToFreelancerOrg(AssignFreelancerOrgCommand cmd) {
        log.info("Assigning FreelancerOrg={} (role={}) to delivery={} tenant={}",
                cmd.freelancerOrgId(), cmd.freelancerRole(), cmd.deliveryId(), cmd.tenantId());
        return loadDelivery(cmd.tenantId(), cmd.deliveryId())
                .flatMap(delivery -> {
                    delivery.assignToFreelancerOrg(cmd.freelancerOrgId(), cmd.freelancerRole());
                    return saveAndPublish(delivery);
                });
    }

    @Override
    public Mono<Delivery> recordFreelancerVehicleAssigned(UUID deliveryId,
            String vehicleId, List<String> equipmentIds) {
        log.info("Recording FreelancerVehicle={} assigned to delivery={}", vehicleId, deliveryId);
        // Load delivery without tenant (cross-module event: no tenant scope available)
        return deliveryRepository.findByIdNoTenant(deliveryId)
                .switchIfEmpty(Mono.error(new com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryNotFoundException(deliveryId)))
                .flatMap(delivery -> {
                    delivery.recordFreelancerVehicleAssigned(vehicleId, equipmentIds);
                    return saveAndPublish(delivery);
                });
    }

    // ── Private helpers ───────────────────────────────────────────────

    private Mono<Delivery> loadDelivery(java.util.UUID tenantId, java.util.UUID deliveryId) {
        return deliveryRepository.findById(tenantId, deliveryId)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(deliveryId)));
    }

    /** Resolves an actor ID to the delivery person entity ID; falls back to the input if no match. */
    private Mono<UUID> resolveDeliveryPersonId(UUID tenantId, UUID actorOrEntityId) {
        return deliveryPersonRepository.findByActorId(tenantId, actorOrEntityId)
                .map(dp -> dp.getId())
                .switchIfEmpty(Mono.just(actorOrEntityId));
    }

    private void validateDeliveryPerson(Delivery delivery, java.util.UUID deliveryPersonId) {
        if (delivery.getDeliveryPersonId() != null
                && !delivery.getDeliveryPersonId().equals(deliveryPersonId)) {
            throw new DeliveryDomainException(
                "Delivery person " + deliveryPersonId + " is not assigned to delivery " + delivery.getId());
        }
    }

    private Mono<Delivery> saveAndPublish(Delivery delivery) {
        return transitionExecutor.saveAndPublish(delivery);
    }
}
