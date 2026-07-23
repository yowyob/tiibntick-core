package com.yowyob.tiibntick.core.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryAnnouncementUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.RespondToAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.SelectAnnouncementResponseCommand;
import com.yowyob.tiibntick.core.delivery.application.port.out.*;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.exception.AnnouncementNotFoundException;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

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
public class DeliveryAnnouncementService implements DeliveryAnnouncementUseCase {

    private final DeliveryAnnouncementRepository announcementRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryPersonRepository deliveryPersonRepository;
    private final DeliveryCostComputationPort costComputationPort;
    private final EtaComputationPort etaComputationPort;
    private final DeliveryEventPublisher eventPublisher;

    @Override
    @Transactional
    @RequirePermission(resource = "announcement", action = "create")
    public Mono<DeliveryAnnouncement> publishAnnouncement(CreateDeliveryAnnouncementCommand cmd) {
        log.info("Publishing delivery announcement for client={} tenant={}", cmd.clientId(), cmd.tenantId());

        Parcel parcel = Parcel.create(cmd.packageSpec());

        DeliveryAnnouncement announcement = DeliveryAnnouncement.createDraft(
                cmd.tenantId(),
                cmd.clientId(),
                cmd.title(),
                cmd.description(),
                cmd.offeredAmount(),
                cmd.currency(),
                parcel,
                cmd.pickupAddress(),
                cmd.deliveryAddress(),
                cmd.recipient(),
                cmd.urgency());

        announcement.publish();

        return announcementRepository.save(announcement)
                .flatMap(saved -> eventPublisher.publishAll(saved.getDomainEvents())
                        .doOnSuccess(v -> saved.clearDomainEvents())
                        .thenReturn(saved))
                .doOnSuccess(a -> log.info("Announcement {} published", a.getId()));
    }

    @Override
    @RequirePermission(resource = "announcement", action = "respond")
    public Mono<DeliveryAnnouncement> respondToAnnouncement(RespondToAnnouncementCommand cmd) {
        log.info("Delivery person={} responding to announcement={}", cmd.deliveryPersonId(), cmd.announcementId());

        return announcementRepository.findById(cmd.tenantId(), cmd.announcementId())
                .switchIfEmpty(Mono.error(new AnnouncementNotFoundException(cmd.announcementId())))
                .flatMap(announcement -> deliveryPersonRepository
                        .findById(cmd.tenantId(), cmd.deliveryPersonId())
                        .filter(DeliveryPerson::isAvailable)
                        .switchIfEmpty(Mono.error(new DeliveryDomainException(
                            "Delivery person is not available: " + cmd.deliveryPersonId())))
                        .flatMap(dp -> {
                            AnnouncementResponse response = AnnouncementResponse.create(
                                    cmd.announcementId(),
                                    cmd.deliveryPersonId(),
                                    cmd.estimatedArrivalTime(),
                                    cmd.note());
                            announcement.addResponse(response);
                            return announcementRepository.save(announcement);
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
                    return createDeliveryFromSelection(cmd.tenantId(), announcement, selected)
                            .flatMap(delivery -> {
                                announcement.linkDelivery(delivery.getId());
                                return announcementRepository.save(announcement)
                                        .flatMap(saved -> deliveryRepository.save(delivery)
                                                .flatMap(savedDelivery -> {
                                                    var allEvents = new java.util.ArrayList<>(saved.getDomainEvents());
                                                    allEvents.addAll(savedDelivery.getDomainEvents());
                                                    return eventPublisher.publishAll(allEvents)
                                                            .doOnSuccess(v -> {
                                                                saved.clearDomainEvents();
                                                                savedDelivery.clearDomainEvents();
                                                            })
                                                            .thenReturn(saved);
                                                }));
                            });
                });
    }

    @Override
    public Mono<Void> cancelAnnouncement(UUID tenantId, UUID announcementId, UUID clientId) {
        return announcementRepository.findById(tenantId, announcementId)
                .switchIfEmpty(Mono.error(new AnnouncementNotFoundException(announcementId)))
                .flatMap(announcement -> {
                    if (!announcement.getClientId().equals(clientId)) {
                        return Mono.error(new DeliveryDomainException(
                            "Only the client who created the announcement can cancel it"));
                    }
                    announcement.cancel();
                    return announcementRepository.save(announcement).then();
                });
    }

    // ── Private helpers ───────────────────────────────────────────────

    private Mono<Delivery> createDeliveryFromSelection(UUID tenantId,
                                                       DeliveryAnnouncement announcement,
                                                       AnnouncementResponse selected) {
        return deliveryPersonRepository.findById(tenantId, selected.getDeliveryPersonId())
                .flatMap(dp -> costComputationPort.compute(
                        announcement.getPickupAddress().coordinates(),
                        announcement.getDeliveryAddress().coordinates(),
                        dp.getLogisticsType(),
                        announcement.getUrgency())
                .flatMap(cost -> etaComputationPort.computeInitial(
                        dp.getCurrentLocation() != null
                                ? dp.getCurrentLocation()
                                : announcement.getPickupAddress().coordinates(),
                        announcement.getDeliveryAddress().coordinates(),
                        announcement.getPickupAddress().coordinates()
                                .haversineDistanceTo(announcement.getDeliveryAddress().coordinates()))
                .map(eta -> {
                    Parcel parcel = announcement.getParcel();
                    Delivery delivery = Delivery.create(
                            tenantId,
                            announcement.getId(),
                            announcement.getClientId(),
                            parcel,
                            announcement.getPickupAddress(),
                            announcement.getDeliveryAddress(),
                            announcement.getRecipient(),
                            announcement.getUrgency(),
                            selected.getEstimatedArrivalTime(),
                            null);

                    delivery.assignDeliveryPerson(
                            selected.getDeliveryPersonId(),
                            cost,
                            announcement.getPickupAddress().coordinates()
                                    .haversineDistanceTo(announcement.getDeliveryAddress().coordinates()),
                            eta.estimatedArrival());
                    return delivery;
                })));
    }
}
