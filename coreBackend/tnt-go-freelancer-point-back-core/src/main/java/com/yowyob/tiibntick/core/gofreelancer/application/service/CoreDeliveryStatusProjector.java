package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort.CoreDeliveryState;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Keeps the gofp {@link Delivery} row in step with transitions that happen <em>inside</em>
 * tnt-delivery-core without going through gofp: an incident pausing a mission, an SLA timeout, an
 * agency or dispute action, a relay hand-over. Those reach gofp only as
 * {@code tnt.delivery.mission.status.changed} events.
 *
 * <p>Idempotent: replaying an event, or receiving the event of a transition gofp itself just
 * projected, changes nothing and writes nothing. A delivery unknown to gofp (an agency delivery) is
 * ignored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoreDeliveryStatusProjector {

    private final DeliveryRepository deliveryRepository;

    /**
     * @return {@code true} when the gofp row was updated
     */
    public Mono<Boolean> project(UUID deliveryId, String newStatus, Instant occurredAt) {
        return deliveryRepository.findById(deliveryId)
                .flatMap(delivery -> {
                    CoreDeliveryState state = new CoreDeliveryState(
                            deliveryId, newStatus, null, null,
                            "PICKED_UP".equals(newStatus) ? occurredAt : null,
                            "DELIVERED".equals(newStatus) ? occurredAt : null);
                    // Terminal rows are final: a late/replayed event must not resurrect them.
                    if (isTerminal(delivery) && !newStatus.equals(delivery.getStatus().name())) {
                        log.debug("Ignoring {} for terminal gofp delivery {} ({})", newStatus, deliveryId, delivery.getStatus());
                        return Mono.just(false);
                    }
                    if (!CoreDeliveryProjection.apply(delivery, state)) {
                        return Mono.just(false);
                    }
                    log.info("Projected core status {} onto gofp delivery {}", newStatus, deliveryId);
                    return deliveryRepository.save(delivery).thenReturn(true);
                })
                .defaultIfEmpty(false);
    }

    private static boolean isTerminal(Delivery d) {
        return switch (d.getStatus()) {
            case DELIVERED, FAILED, CANCELLED -> true;
            default -> false;
        };
    }
}
