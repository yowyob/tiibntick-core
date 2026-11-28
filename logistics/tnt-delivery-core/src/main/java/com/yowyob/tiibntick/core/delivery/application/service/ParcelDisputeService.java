package com.yowyob.tiibntick.core.delivery.application.service;

import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.delivery.application.port.in.ParcelDisputeUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryThresholdsPort;
import com.yowyob.tiibntick.core.delivery.application.port.out.ParcelRepository;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryFailureReason;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DisputeParcelOutcome;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponsibleParty;
import com.yowyob.tiibntick.core.delivery.application.port.out.DisputeFilingWindowPort;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryPersonRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.RelayOwnershipPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/** {@link ParcelDisputeUseCase}: dispute effects on the parcel and its deliveries, events in the same transaction. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParcelDisputeService implements ParcelDisputeUseCase {

    private final ParcelRepository parcelRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryTransitionExecutor transitionExecutor;
    private final ReturnDeliveryService returnDeliveryService;
    private final DeliveryThresholdsPort thresholds;
    private final DisputeFilingWindowPort filingWindows;
    private final DeliveryPersonRepository deliveryPersons;
    private final org.springframework.beans.factory.ObjectProvider<RelayOwnershipPort> relayOwnerships;

    @Override
    public Mono<UUID> resolveParcelId(UUID tenantId, UUID parcelId, UUID deliveryId) {
        Mono<UUID> fromDelivery = deliveryId == null ? Mono.empty()
                : deliveryRepository.findById(tenantId, deliveryId)
                        .mapNotNull(d -> d.getParcel() != null ? d.getParcel().getId() : null);
        if (parcelId == null) {
            return fromDelivery;
        }
        return parcelRepository.findById(parcelId).map(Parcel::getId).switchIfEmpty(fromDelivery);
    }

    @Override
    public Mono<DisputeParties> disputeParties(UUID tenantId, UUID parcelId) {
        return parcelRepository.findById(parcelId).flatMap(parcel -> deliveryRepository
                .findByParcelId(parcelId, tenantId).collectList().flatMap(deliveries -> {
                    java.util.Set<UUID> senders = new java.util.LinkedHashSet<>();
                    java.util.Set<String> orgs = new java.util.LinkedHashSet<>();
                    java.util.Set<UUID> persons = new java.util.LinkedHashSet<>();
                    java.util.Set<UUID> hubs = new java.util.LinkedHashSet<>();
                    if (parcel.getCustody() != null && parcel.getCustody().hubId() != null) {
                        hubs.add(parcel.getCustody().hubId());
                    }
                    for (Delivery d : deliveries) {
                        if (d.getSenderId() != null) {
                            senders.add(d.getSenderId());
                        }
                        if (d.getAssignedFreelancerOrgId() != null) {
                            orgs.add(d.getAssignedFreelancerOrgId().toLowerCase());
                        }
                        if (d.getDeliveryPersonId() != null) {
                            persons.add(d.getDeliveryPersonId());
                        }
                        if (d.getDestinationHubId() != null) {
                            hubs.add(d.getDestinationHubId());
                        }
                    }
                    Mono<java.util.Set<UUID>> couriers = reactor.core.publisher.Flux.fromIterable(persons)
                            .flatMap(person -> deliveryPersons.findById(tenantId, person)
                                    .mapNotNull(dp -> dp.getActorId() != null ? dp.getActorId() : person)
                                    .defaultIfEmpty(person))
                            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
                    RelayOwnershipPort relays = relayOwnerships.getIfAvailable();
                    Mono<java.util.Set<UUID>> relayOrgs = relays == null ? Mono.just(java.util.Set.of())
                            : reactor.core.publisher.Flux.fromIterable(hubs)
                                    .flatMap(hub -> relays.findByHubId(hub)
                                            .mapNotNull(RelayOwnershipPort.RelayOwnership::ownerKernelOrganizationId)
                                            .onErrorResume(e -> Mono.empty()))
                                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
                    return Mono.zip(couriers, relayOrgs).map(t -> new DisputeParties(parcelId,
                            java.util.Set.copyOf(senders), java.util.Set.of(), t.getT1(), java.util.Set.copyOf(orgs),
                            t.getT2()));
                }));
    }

    @Override
    @Transactional
    public Mono<Parcel> openDispute(UUID tenantId, UUID parcelId, UUID disputeId, boolean freezesDelivery,
                                    TntEventActor actor, UUID causationId) {
        return loadParcel(parcelId).flatMap(parcel -> {
            Mono<Boolean> within = !parcel.getStatus().isClosed() ? Mono.just(true)
                    : filingWindows.filingWindow(tenantId).map(window -> withinWindow(parcel, window, Instant.now()));
            return within.flatMap(ok -> {
                parcel.openDispute(disputeId, freezesDelivery, ok, actor, causationId, tenantId);
                return parcelRepository.save(parcel).flatMap(saved -> transitionExecutor.publish(saved)
                        .thenReturn(saved));
            });
        });
    }

    /** Whether a closed parcel still accepts a dispute: closed for less than {@code window}. */
    static boolean withinWindow(Parcel parcel, java.time.Duration window, Instant now) {
        Instant closedAt = parcel.getStatusChangedAt() != null ? parcel.getStatusChangedAt() : parcel.getUpdatedAt();
        return closedAt == null || !now.isAfter(closedAt.plus(window));
    }

    @Override
    @Transactional
    public Mono<Parcel> openDispute(UUID tenantId, UUID parcelId, UUID disputeId, boolean freezesDelivery,
                                    boolean withinFilingWindow, TntEventActor actor, UUID causationId) {
        return loadParcel(parcelId).flatMap(parcel -> {
            parcel.openDispute(disputeId, freezesDelivery, withinFilingWindow, actor, causationId, tenantId);
            return parcelRepository.save(parcel).flatMap(saved -> transitionExecutor.publish(saved).thenReturn(saved));
        });
    }

    @Override
    @Transactional
    public Mono<Parcel> applyDisputeOutcome(UUID tenantId, UUID parcelId, DisputeParcelOutcome outcome,
                                            String trigger, String compensationAmount,
                                            ResponsibleParty responsibleParty, TntEventActor actor,
                                            UUID causationId) {
        return deliveryRepository.findByParcelId(parcelId, tenantId).collectList().flatMap(deliveries -> {
            // ordered created_at DESC: the first is the latest; the parcel instance is shared by all of them
            Delivery latest = deliveries.isEmpty() ? null : deliveries.getFirst();
            Mono<Parcel> parcelMono = latest != null && latest.getParcel() != null
                    ? Mono.just(latest.getParcel()) : loadParcel(parcelId);
            return parcelMono.flatMap(parcel -> {
                if (responsibleParty != null && outcome == DisputeParcelOutcome.RETURN_TO_SENDER) {
                    parcel.setReturnCostPayer(responsibleParty.returnCostPayer());
                }
                parcel.applyDisputeOutcome(outcome, trigger, compensationAmount, actor, causationId, tenantId);
                return switch (outcome) {
                    case LOST, DAMAGED -> endCurrentDelivery(latest, parcel, outcome, responsibleParty);
                    case RETURN_TO_SENDER -> latest == null ? saveParcel(parcel)
                            : saveParcel(parcel).then(returnDeliveryService.openReturnDraft(latest)
                                    .flatMap(draft -> transitionExecutor.publish(draft))).thenReturn(parcel);
                    case REDELIVERY -> redeliver(tenantId, latest, parcel);
                    case RESTORE_PREVIOUS -> saveParcel(parcel);
                };
            });
        });
    }

    /** D21 on the non-terminal delivery of a parcel declared lost or damaged. */
    private Mono<Parcel> endCurrentDelivery(Delivery latest, Parcel parcel, DisputeParcelOutcome outcome,
                                            ResponsibleParty responsible) {
        if (latest == null || latest.getStatus().isTerminal()) {
            return saveParcel(parcel);
        }
        latest.actingAs(TntEventActor.of(TntEventActor.Type.SUPPORT, null));
        latest.failForParcelLoss(outcome == DisputeParcelOutcome.LOST ? DeliveryFailureReason.PARCEL_LOST
                : DeliveryFailureReason.PARCEL_DAMAGED, responsible);
        return transitionExecutor.saveAndPublish(latest).thenReturn(parcel);
    }

    /** P17 then D1 of a REDELIVERY delivery to the same recipient (decision [B.7]). */
    private Mono<Parcel> redeliver(UUID tenantId, Delivery latest, Parcel parcel) {
        if (latest == null) {
            return saveParcel(parcel);
        }
        if (!latest.getStatus().isTerminal()) {
            return Mono.error(new DeliveryDomainException("Delivery " + latest.getId()
                    + " of parcel " + parcel.getId() + " is not final: no redelivery while it runs"));
        }
        return thresholds.forAgency(latest.getAgencyId())
                .map(DeliveryThresholdsPort.Thresholds::maxDeliveryAttempts)
                .defaultIfEmpty(Delivery.DEFAULT_MAX_ATTEMPTS)
                .flatMap(max -> {
                    Delivery redelivery = Delivery.create(tenantId != null ? tenantId : latest.getTenantId(), null,
                            latest.getSenderId(), parcel, latest.getPickupAddress(), latest.getDeliveryAddress(),
                            latest.getRecipient(), latest.getUrgency(), null, "Redelivery ordered by a dispute",
                            DeliveryKind.REDELIVERY, latest.getId(), max);
                    redelivery.setAgencyContext(latest.getAgencyId(), latest.getPlatform());
                    redelivery.setDestinationHub(latest.getDestinationHubId());
                    return transitionExecutor.saveAndPublish(redelivery).thenReturn(parcel);
                });
    }

    private Mono<Parcel> saveParcel(Parcel parcel) {
        return parcelRepository.save(parcel).flatMap(saved -> transitionExecutor.publish(saved).thenReturn(saved));
    }

    private Mono<Parcel> loadParcel(UUID parcelId) {
        return parcelRepository.findById(parcelId)
                .switchIfEmpty(Mono.error(new DeliveryDomainException("Parcel not found: " + parcelId)));
    }
}
