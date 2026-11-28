package com.yowyob.tiibntick.core.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Relay storage watch (spec §5.2): the reminder {@code DeliveryAlertRaised(RELAY_STORAGE_EXPIRING)} once, then at the
 * end of storage the delivery fails {@code NOT_COLLECTED_AT_RELAY} (row D12) and the parcel waits for its return
 * (P11, QO-4 return draft for the sender, paid by the return guarantee, D-147, D-151). Each delivery is handled in its own transaction
 * ({@link DeliveryTransitionExecutor#saveAndPublish} is transactional).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelayStorageService {

    static final int BATCH = 200;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryTransitionExecutor transitionExecutor;

    /** @return the number of reminders recorded */
    public Mono<Long> sendDueReminders(Instant now) {
        return each(deliveryRepository.findRelayStorageReminderDue(now, BATCH), "reminder",
                d -> d.raiseStorageReminder(now));
    }

    /** @return the number of deliveries failed for storage expiry (D12) */
    public Mono<Long> expireStorage(Instant now) {
        return each(deliveryRepository.findRelayStorageExpired(now, BATCH), "expiry", Delivery::expireRelayStorage);
    }

    private Mono<Long> each(Flux<Delivery> due, String what, Consumer<Delivery> change) {
        return due.concatMap(delivery -> {
                    try {
                        change.accept(delivery);
                    } catch (DeliveryDomainException e) {
                        log.warn("Relay storage {} skipped for delivery {}: {}", what, delivery.getId(),
                                e.getMessage());
                        return Mono.empty();
                    }
                    if (delivery.getDomainEvents().isEmpty()) {
                        return Mono.empty();
                    }
                    return transitionExecutor.saveAndPublish(delivery)
                            .onErrorResume(e -> {
                                log.error("Relay storage {} failed for delivery {}", what, delivery.getId(), e);
                                return Mono.empty();
                            });
                })
                .count();
    }
}
