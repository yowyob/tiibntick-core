package com.yowyob.tiibntick.core.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.AnnouncementLookupUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.CreateDirectDeliveryUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryAnnouncementUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDirectDeliveryCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.RespondToAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.SelectAnnouncementResponseCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.UpdateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.out.*;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.exception.AnnouncementNotFoundException;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryKind;
import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

/**
 * Application service orchestrating the delivery announcement lifecycle.
 *
 * <p>No domain logic lives here — it delegates to aggregates and domain policies.
 * Persistence + event publishing happen after domain mutations (Unit of Work pattern).
 * Methods combining a save with an event publish are {@code @Transactional} so the
 * outbox envelope/entry (Chantier C · Audit n°3 · P5) commits atomically with the
 * aggregate write.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryAnnouncementService
        implements DeliveryAnnouncementUseCase, CreateDirectDeliveryUseCase, AnnouncementLookupUseCase {

    private final DeliveryAnnouncementRepository announcementRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryPersonRepository deliveryPersonRepository;
    private final DeliveryCostComputationPort costComputationPort;
    private final EtaComputationPort etaComputationPort;
    private final DeliveryTransitionExecutor transitionExecutor;
    private final DeliveryThresholdsPort thresholds;

    @Override
    @Transactional
    @RequirePermission(resource = "announcement", action = "create")
    public Mono<DeliveryAnnouncement> publishAnnouncement(CreateDeliveryAnnouncementCommand cmd) {
        log.info("Publishing delivery announcement for client={} tenant={}", cmd.clientId(), cmd.tenantId());

        Parcel parcel = cmd.existingParcel() != null
                ? cmd.existingParcel()
                : Parcel.create(cmd.packageSpec(), cmd.tenantId(), cmd.clientId(),
                        TntEventActor.of(TntEventActor.Type.SENDER, cmd.clientId()));

        DeliveryAnnouncement announcement = DeliveryAnnouncement.createDraft(
                cmd.tenantId(),
                cmd.clientId(),
                cmd.title(),
                cmd.description(),
                cmd.offeredAmount(),
                cmd.currency(),
                cmd.pricingMode(),
                parcel,
                cmd.pickupAddress(),
                cmd.deliveryAddress(),
                cmd.recipient(),
                cmd.urgency(),
                cmd.sender(),
                cmd.details());

        // One transition per save (A1 then A2): aggregateVersion strictly increases (spec §3.3).
        return announcementRepository.save(announcement)
                .flatMap(draft -> {
                    draft.publish();
                    return announcementRepository.save(draft);
                })
                .flatMap(saved -> transitionExecutor.publish(saved, saved.getParcel()).thenReturn(saved))
                .doOnSuccess(a -> log.info("Announcement {} published", a.getId()));
    }

    @Override
    @Transactional
    @RequirePermission(resource = "announcement", action = "respond")
    public Mono<DeliveryAnnouncement> respondToAnnouncement(RespondToAnnouncementCommand cmd) {
        log.info("Delivery person={} responding to announcement={}", cmd.deliveryPersonId(), cmd.announcementId());

        return announcementRepository.findById(cmd.tenantId(), cmd.announcementId())
                .switchIfEmpty(Mono.error(new AnnouncementNotFoundException(cmd.announcementId())))
                .flatMap(announcement -> courierOf(cmd.tenantId(), cmd.deliveryPersonId())
                        .filter(DeliveryPerson::isAvailable)
                        .switchIfEmpty(Mono.error(new DeliveryDomainException(
                            "Delivery person is not available: " + cmd.deliveryPersonId())))
                        .flatMap(dp -> {
                            AnnouncementResponse response = AnnouncementResponse.create(
                                    cmd.announcementId(),
                                    cmd.deliveryPersonId(),
                                    cmd.estimatedArrivalTime(),
                                    cmd.note(),
                                    cmd.proposedPrice(),
                                    cmd.proposedCurrency() != null
                                            ? cmd.proposedCurrency()
                                            : announcement.getCurrency());
                            announcement.addResponse(response);
                            return announcementRepository.save(announcement)
                                    .flatMap(saved -> transitionExecutor.publish(saved).thenReturn(saved));
                        }));
    }

    @Override
    @Transactional
    @RequirePermission(resource = "announcement", action = "elect")
    public Mono<DeliveryAnnouncement> selectResponse(SelectAnnouncementResponseCommand cmd) {
        log.info("Client={} selecting response={} on announcement={}", cmd.clientId(), cmd.responseId(), cmd.announcementId());

        return announcementRepository.findById(cmd.tenantId(), cmd.announcementId())
                .switchIfEmpty(Mono.error(new AnnouncementNotFoundException(cmd.announcementId())))
                .flatMap(announcement -> {
                    AnnouncementResponse selected = announcement.selectResponse(cmd.responseId());
                    // D1 and D2 are two saves of the delivery (one transition per save, spec §3.3).
                    return createDeliveryFromSelection(cmd.tenantId(), announcement, selected, cmd.deliveryId())
                            .flatMap(delivery -> {
                                announcement.linkDelivery(delivery.getId());
                                return announcementRepository.save(announcement)
                                        .then(deliveryRepository.save(delivery))
                                        .flatMap(created -> {
                                            created.assignDeliveryPerson(selected.getDeliveryPersonId(),
                                                    created.getEstimatedCost(), created.getEstimatedDistanceKm(),
                                                    created.getEstimatedDeliveryTime());
                                            return transitionExecutor.saveAndPublish(created);
                                        })
                                        .then(transitionExecutor.publish(announcement))
                                        .thenReturn(announcement);
                            });
                });
    }

    @Override
    @Transactional
    @RequirePermission(resource = "delivery", action = "create-direct")
    public Mono<Delivery> createDirect(CreateDirectDeliveryCommand cmd) {
        log.info("Creating direct delivery for tenant={} sender={} agency={}",
                cmd.tenantId(), cmd.senderId(), cmd.agencyId());

        Parcel parcel = Parcel.create(cmd.packageSpecification(), cmd.tenantId(), cmd.senderId(),
                TntEventActor.of(TntEventActor.Type.SENDER, cmd.senderId()));
        Delivery delivery = Delivery.create(
                cmd.tenantId(),
                null,
                cmd.senderId(),
                parcel,
                cmd.pickupAddress(),
                cmd.deliveryAddress(),
                cmd.recipient(),
                cmd.urgency(),
                cmd.scheduledPickupTime(),
                cmd.notes());

        if (cmd.agencyId() != null) {
            delivery.setAgencyContext(cmd.agencyId(), "AGENCY");
        }

        return transitionExecutor.saveAndPublish(delivery)
                .doOnSuccess(d -> log.info("Direct delivery {} created, trackingCode={}",
                        d.getId(), d.getTrackingCode()));
    }

    @Override
    @Transactional
    public Mono<Void> cancelAnnouncement(UUID tenantId, UUID announcementId, UUID clientId) {
        return announcementRepository.findById(tenantId, announcementId)
                .switchIfEmpty(Mono.error(new AnnouncementNotFoundException(announcementId)))
                .flatMap(announcement -> {
                    if (!announcement.getClientId().equals(clientId)) {
                        return Mono.error(new DeliveryDomainException(
                            "Only the client who created the announcement can cancel it"));
                    }
                    announcement.cancel();
                    return announcementRepository.save(announcement)
                            .flatMap(saved -> transitionExecutor.publish(saved));
                });
    }

    @Override
    @Transactional
    @RequirePermission(resource = "announcement", action = "update")
    public Mono<DeliveryAnnouncement> updateAnnouncement(UpdateDeliveryAnnouncementCommand cmd) {
        return announcementRepository.findById(cmd.tenantId(), cmd.announcementId())
                .switchIfEmpty(Mono.error(new AnnouncementNotFoundException(cmd.announcementId())))
                .flatMap(announcement -> {
                    if (!announcement.getClientId().equals(cmd.clientId())) {
                        return Mono.error(new DeliveryDomainException(
                            "Only the client who created the announcement can update it"));
                    }
                    announcement.updateTerms(cmd.offeredAmount(), cmd.currency());
                    announcement.updateDetails(cmd.sender(), cmd.details());
                    return announcementRepository.save(announcement)
                            .flatMap(saved -> transitionExecutor.publish(saved).thenReturn(saved));
                });
    }

    @Override
    public Mono<DeliveryAnnouncement> findAnnouncementByOrigin(UUID tenantId, String originType, UUID originId) {
        return announcementRepository.findByOrigin(tenantId, originType, originId);
    }

    @Override
    public Flux<DeliveryAnnouncement> findAnnouncementsByOrigins(UUID tenantId, String originType,
                                                                 Collection<UUID> originIds) {
        if (originIds == null || originIds.isEmpty()) {
            return Flux.empty();
        }
        return announcementRepository.findByOrigins(tenantId, originType, originIds);
    }

    @Override
    public Flux<DeliveryAnnouncement> findAnnouncementsRespondedBy(UUID tenantId,
                                                                   Collection<UUID> deliveryPersonIds) {
        if (deliveryPersonIds == null || deliveryPersonIds.isEmpty()) {
            return Flux.empty();
        }
        return announcementRepository.findRespondedBy(tenantId, deliveryPersonIds);
    }

    // ── Private helpers ───────────────────────────────────────────────

    private Mono<Delivery> createDeliveryFromSelection(UUID tenantId,
                                                       DeliveryAnnouncement announcement,
                                                       AnnouncementResponse selected,
                                                       UUID deliveryId) {
        GeoCoordinates pickup = resolveCoords(announcement.getPickupAddress());
        GeoCoordinates dropoff = resolveCoords(announcement.getDeliveryAddress());
        double routeKm = Math.max(1.0, pickup.haversineDistanceTo(dropoff));

        return courierOf(tenantId, selected.getDeliveryPersonId())
                // D2 guard: the courier is APPROVED
                .filter(DeliveryPerson::isAvailable)
                .switchIfEmpty(Mono.error(new DeliveryDomainException(
                        "Delivery person is not approved: " + selected.getDeliveryPersonId())))
                .flatMap(dp -> costComputationPort.compute(
                        pickup,
                        dropoff,
                        dp.getLogisticsType(),
                        announcement.getUrgency())
                .flatMap(cost -> etaComputationPort.computeInitial(
                        dp.getCurrentLocation() != null ? dp.getCurrentLocation() : pickup,
                        dropoff,
                        routeKm)
                .zipWith(thresholds.forAgency(null).map(DeliveryThresholdsPort.Thresholds::maxDeliveryAttempts)
                        .defaultIfEmpty(Delivery.DEFAULT_MAX_ATTEMPTS))
                .map(etaAndMax -> {
                    var eta = etaAndMax.getT1();
                    Parcel parcel = announcement.getParcel();
                    boolean isReturn = announcement.getKind() == AnnouncementKind.RETURN;
                    UUID parent = isReturn && announcement.getDetails() != null
                            ? announcement.getDetails().originRefId() : null;
                    Delivery delivery = Delivery.create(
                            deliveryId,
                            tenantId,
                            announcement.getId(),
                            announcement.getClientId(),
                            parcel,
                            announcement.getPickupAddress(),
                            announcement.getDeliveryAddress(),
                            announcement.getRecipient(),
                            announcement.getUrgency(),
                            selected.getEstimatedArrivalTime(),
                            null,
                            isReturn && parent != null ? DeliveryKind.RETURN : DeliveryKind.OUTBOUND,
                            isReturn ? parent : null,
                            etaAndMax.getT2());
                    if (announcement.getDetails() != null) {
                        delivery.setDestinationHub(announcement.getDetails().destinationHubId());
                    }
                    delivery.recordSelectionEstimate(cost, routeKm, eta.estimatedArrival());
                    return delivery;
                })));
    }

    /**
     * The courier of a response: in the announcement's tenant first, else by its globally unique id
     * in any tenant — the Go marketplace lists and invites couriers across tenants, so a courier
     * of another tenant must be able to answer and be elected (lot C-56).
     */
    private Mono<DeliveryPerson> courierOf(UUID tenantId, UUID deliveryPersonId) {
        return deliveryPersonRepository.findById(tenantId, deliveryPersonId)
                .switchIfEmpty(Mono.defer(() -> deliveryPersonRepository.findByIdInAnyTenant(deliveryPersonId)));
    }

    /** Fallback to Yaoundé centre when an address has no GPS (common for informal create payloads). */
    private static GeoCoordinates resolveCoords(
            com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress address) {
        if (address != null && address.coordinates() != null) {
            return address.coordinates();
        }
        return new GeoCoordinates(3.8480, 11.5021);
    }
}
