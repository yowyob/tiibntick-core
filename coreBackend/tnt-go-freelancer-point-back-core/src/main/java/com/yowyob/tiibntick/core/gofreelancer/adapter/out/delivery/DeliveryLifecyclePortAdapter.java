package com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CancelDeliveryCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CompleteDeliveryCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.ConfirmPickupCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.DepositAtRelayPointCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.FailDeliveryCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.ResumeFromRelayPointCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.StartTransitCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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
    public Mono<CoreDeliveryState> findState(UUID tenantId, UUID deliveryId) {
        return query.findDeliveryById(tenantId, deliveryId).map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Mono<CoreDeliveryState> confirmPickup(UUID tenantId, UUID deliveryId, UUID deliveryPersonId) {
        return lifecycle.confirmPickup(new ConfirmPickupCommand(tenantId, deliveryId, deliveryPersonId))
                .map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Mono<CoreDeliveryState> startOrResumeTransit(UUID tenantId, UUID deliveryId, UUID deliveryPersonId) {
        return query.findDeliveryById(tenantId, deliveryId)
                .flatMap(current -> current.getStatus() == DeliveryStatus.AT_RELAY_POINT
                        ? lifecycle.resumeFromRelayPoint(
                                new ResumeFromRelayPointCommand(tenantId, deliveryId, deliveryPersonId))
                        : lifecycle.startTransit(
                                new StartTransitCommand(tenantId, deliveryId, deliveryPersonId, null)))
                .map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Mono<CoreDeliveryState> depositAtRelayPoint(UUID tenantId, UUID deliveryId, UUID deliveryPersonId,
                                                       UUID relayPointId) {
        return lifecycle.depositAtRelayPoint(
                        new DepositAtRelayPointCommand(tenantId, deliveryId, deliveryPersonId, relayPointId))
                .map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Mono<CoreDeliveryState> complete(UUID tenantId, UUID deliveryId, UUID deliveryPersonId) {
        return lifecycle.completeDelivery(new CompleteDeliveryCommand(
                        tenantId, deliveryId, deliveryPersonId, null, null, null, null, null))
                .map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Mono<CoreDeliveryState> fail(UUID tenantId, UUID deliveryId, UUID deliveryPersonId, String reason) {
        return lifecycle.failDelivery(new FailDeliveryCommand(tenantId, deliveryId, deliveryPersonId, reason))
                .map(DeliveryLifecyclePortAdapter::toState);
    }

    @Override
    public Mono<CoreDeliveryState> cancel(UUID tenantId, UUID deliveryId, UUID requesterId, String reason) {
        return lifecycle.cancelDelivery(new CancelDeliveryCommand(tenantId, deliveryId, requesterId, reason))
                .map(DeliveryLifecyclePortAdapter::toState);
    }

    private static CoreDeliveryState toState(Delivery d) {
        return new CoreDeliveryState(d.getId(), d.getStatus().name(), d.getTrackingCode(),
                d.getDeliveryPersonId(), d.getActualPickupTime(), d.getActualDeliveryTime());
    }
}
