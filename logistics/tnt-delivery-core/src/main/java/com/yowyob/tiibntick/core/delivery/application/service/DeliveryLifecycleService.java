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
import com.yowyob.tiibntick.core.delivery.domain.policy.DeliveryCostPolicy;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final DeliveryRepository deliveryRepository;
    private final DeliveryPersonRepository deliveryPersonRepository;
    private final EtaComputationPort etaComputationPort;
    private final DeliveryEventPublisher eventPublisher;
    private final DeliveryProofAnchorPort deliveryProofAnchorPort;

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
                                    });
                        }));
    }

    @Override
    @Transactional
    public Mono<Delivery> depositAtRelayPoint(DepositAtRelayPointCommand cmd) {
        log.info("Depositing delivery={} at relay={}", cmd.deliveryId(), cmd.relayPointId());
        return loadDelivery(cmd.tenantId(), cmd.deliveryId())
                .flatMap(delivery -> resolveDeliveryPersonId(cmd.tenantId(), cmd.deliveryPersonId())
                        .flatMap(dpId -> {
                            validateDeliveryPerson(delivery, dpId);
                            delivery.depositAtRelayPoint(cmd.relayPointId());
                            return saveAndPublish(delivery);
                        }));
    }

    @Override
    @Transactional
    public Mono<Delivery> resumeFromRelayPoint(ResumeFromRelayPointCommand cmd) {
        log.info("Resuming delivery={} from relay point", cmd.deliveryId());
        return loadDelivery(cmd.tenantId(), cmd.deliveryId())
                .flatMap(delivery -> resolveDeliveryPersonId(cmd.tenantId(), cmd.deliveryPersonId())
                        .flatMap(dpId -> {
                            validateDeliveryPerson(delivery, dpId);
                            var dest = delivery.getDeliveryAddress().coordinates();
                            var origin = delivery.getPickupAddress().coordinates();
                            double distKm = origin.haversineDistanceTo(dest);

                            return etaComputationPort.computeInitial(origin, dest, distKm)
                                    .flatMap(eta -> {
                                        delivery.resumeFromRelayPoint(eta);
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
                                        .then(deliveryRepository.save(delivery))
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

    @Override
    @Transactional
    @RequirePermission(resource = "mission", action = "complete")
    public Mono<Delivery> failDelivery(FailDeliveryCommand cmd) {
        log.warn("Failing delivery={} reason={}", cmd.deliveryId(), cmd.reason());
        return loadDelivery(cmd.tenantId(), cmd.deliveryId())
                .flatMap(delivery -> resolveDeliveryPersonId(cmd.tenantId(), cmd.deliveryPersonId())
                        .flatMap(dpId -> {
                            validateDeliveryPerson(delivery, dpId);
                            delivery.fail(cmd.reason());
                            return saveAndPublish(delivery)
                                    .flatMap(saved -> deliveryPersonRepository
                                            .findById(cmd.tenantId(), dpId)
                                            .flatMap(dp -> {
                                                dp.recordDeliveryFailed();
                                                return deliveryPersonRepository.save(dp);
                                            })
                                            .thenReturn(saved));
                        }));
    }

    @Override
    @Transactional
    @RequirePermission(resource = "mission", action = "assign")
    public Mono<Delivery> cancelDelivery(CancelDeliveryCommand cmd) {
        log.info("Cancelling delivery={} by={} reason={}", cmd.deliveryId(), cmd.requesterId(), cmd.reason());
        return loadDelivery(cmd.tenantId(), cmd.deliveryId())
                .flatMap(delivery -> {
                    delivery.cancel(cmd.reason());
                    return saveAndPublish(delivery);
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
                    return deliveryRepository.save(delivery);
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
        return deliveryRepository.save(delivery)
                .flatMap(saved -> eventPublisher.publishAll(saved.getDomainEvents())
                        .doOnSuccess(v -> saved.clearDomainEvents())
                        .thenReturn(saved));
    }
}
