package com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CancelDeliveryCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CompleteDeliveryCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.ConfirmPickupCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.DepositAtRelayHubCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.StartFinalLegCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.RecordFailedAttemptCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryFailureReason;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.RecordAgreedTermsCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.ResumeFromRelayHubCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.StartTransitCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Drives {@code tnt-delivery-core}'s lifecycle use cases on behalf of gofp. The core validates each
 * transition against its state machine (an illegal one surfaces as an
 * {@code InvalidDeliveryStateTransitionException}) and publishes the resulting mission events.
 */
@Component
@RequiredArgsConstructor
public class DeliveryLifecyclePortAdapter implements IDeliveryLifecyclePort {

    private final DeliveryLifecycleUseCase lifecycle;
    private final DeliveryQueryUseCase query;

    @Override
    public Mono<UUID> tenantOf(UUID deliveryId) {
        return query.findDeliveryTenantId(deliveryId);
    }

    @Override
    public Mono<CoreDeliveryState> findState(UUID tenantId, UUID deliveryId) {
        return query.findDeliveryById(tenantId, deliveryId).map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Mono<CoreDeliveryState> findStateAnyTenant(UUID deliveryId) {
        return query.findDeliveryByIdAnyTenant(deliveryId).map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Flux<CoreDeliveryState> findStatesByParcelAnyTenant(UUID parcelId) {
        return query.findDeliveriesByParcelIdAnyTenant(parcelId).map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Flux<CoreDeliveryState> findByDeliveryPerson(UUID tenantId, UUID deliveryPersonId) {
        return query.findDeliveriesByDeliveryPerson(tenantId, deliveryPersonId)
                .map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Flux<CoreDeliveryState> findByStatus(UUID tenantId, DeliveryStatus status) {
        return query.findDeliveriesByStatus(tenantId, status).map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Mono<Long> countUnfinished(UUID tenantId, Collection<UUID> deliveryPersonIds) {
        return Flux.fromIterable(deliveryPersonIds)
                .distinct()
                .flatMap(id -> query.countNonTerminalDeliveriesByDeliveryPerson(tenantId, id))
                .reduce(0L, Long::sum);
    }

    @Override
    public Mono<Long> countDelivered(UUID tenantId, Collection<UUID> deliveryPersonIds) {
        return Flux.fromIterable(deliveryPersonIds)
                .distinct()
                .flatMap(id -> query.countDeliveriesByDeliveryPersonAndStatus(tenantId, id, DeliveryStatus.DELIVERED))
                .reduce(0L, Long::sum);
    }

    @Override
    public Mono<CoreDeliveryState> recordAgreedTerms(UUID tenantId, UUID deliveryId, BigDecimal amount,
                                                     String currency, Instant pickupWindowStart,
                                                     Instant pickupWindowEnd, Instant deliveryWindowStart,
                                                     Instant deliveryWindowEnd) {
        return lifecycle.recordAgreedTerms(new RecordAgreedTermsCommand(tenantId, deliveryId, amount, currency,
                        pickupWindowStart, pickupWindowEnd, deliveryWindowStart, deliveryWindowEnd))
                .map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Mono<CoreDeliveryState> confirmPickup(UUID tenantId, UUID deliveryId, UUID deliveryPersonId) {
        return lifecycle.confirmPickup(new ConfirmPickupCommand(tenantId, deliveryId, deliveryPersonId))
                .map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Mono<CoreDeliveryState> startOrResumeTransit(UUID tenantId, UUID deliveryId, UUID deliveryPersonId) {
        return query.findDeliveryById(tenantId, deliveryId)
                .flatMap(current -> current.getStatus() == DeliveryStatus.AT_RELAY_HUB
                        ? lifecycle.resumeFromRelayHub(
                                new ResumeFromRelayHubCommand(tenantId, deliveryId, deliveryPersonId))
                        : lifecycle.startTransit(
                                new StartTransitCommand(tenantId, deliveryId, deliveryPersonId, null)))
                .map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Mono<CoreDeliveryState> depositAtRelayHub(UUID tenantId, UUID deliveryId, UUID deliveryPersonId,
                                                     UUID relayHubId, Integer requestedStorageDays) {
        return lifecycle.depositAtRelayHub(new DepositAtRelayHubCommand(tenantId, deliveryId, deliveryPersonId,
                        relayHubId, null, requestedStorageDays))
                .map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Mono<CoreDeliveryState> startFinalLeg(UUID tenantId, UUID deliveryId, UUID deliveryPersonId) {
        return lifecycle.startFinalLeg(new StartFinalLegCommand(tenantId, deliveryId, deliveryPersonId))
                .map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Mono<CoreDeliveryState> complete(UUID tenantId, UUID deliveryId, UUID deliveryPersonId) {
        return lifecycle.completeDelivery(new CompleteDeliveryCommand(
                        tenantId, deliveryId, deliveryPersonId, null, null, null, null, null))
                .map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Mono<CoreDeliveryState> recordFailedAttempt(UUID tenantId, UUID deliveryId, UUID deliveryPersonId,
                                                       DeliveryFailureReason reason) {
        return lifecycle.recordFailedAttempt(
                        new RecordFailedAttemptCommand(tenantId, deliveryId, deliveryPersonId, reason))
                .map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Mono<CoreDeliveryState> cancel(UUID tenantId, UUID deliveryId, UUID requesterId, String reason) {
        return lifecycle.cancelDelivery(new CancelDeliveryCommand(tenantId, deliveryId, requesterId, reason))
                .map(DeliveryLifecyclePortAdapter::toState);
    }

    private static CoreDeliveryState toState(Delivery d) {
        var parcel = d.getParcel();
        var spec = parcel != null ? parcel.getSpecification() : null;
        Double volumeM3 = spec != null
                ? spec.widthCm() * spec.heightCm() * spec.lengthCm() / 1_000_000.0 : null;
        return new CoreDeliveryState(d.getId(), d.getStatus().name(), d.getTrackingCode(),
                d.getDeliveryPersonId(), d.getActualPickupTime(), d.getActualDeliveryTime(),
                d.getAgreedAmount(), d.getAgreedCurrency(),
                d.getPickupWindowStart(), d.getPickupWindowEnd(),
                d.getDeliveryWindowStart(), d.getDeliveryWindowEnd(),
                d.getAnnouncementId(), parcel != null ? parcel.getId() : null, volumeM3,
                d.getRelayStorageDays(), d.getRelayStorageExpiresAt(), d.isAtDestinationHub());
    }
}
