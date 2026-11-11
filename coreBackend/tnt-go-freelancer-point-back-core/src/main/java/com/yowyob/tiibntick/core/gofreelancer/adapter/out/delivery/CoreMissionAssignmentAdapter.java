package com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryNotFoundException;
import com.yowyob.tiibntick.core.realtime.application.port.out.IMissionAssignmentPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Answers realtime's "is this user the deliverer of this mission?" from tnt-delivery-core, the
 * authority on who carries a delivery. A mission id that is not a core delivery is not ours to judge
 * and is accepted; a delivery without an assigned person has nobody to feed it and is refused.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoreMissionAssignmentAdapter implements IMissionAssignmentPort {

    private final DeliveryQueryUseCase deliveryQuery;

    @Override
    public Mono<Boolean> isMissionAssignedTo(UUID tenantId, UUID missionId, UUID userId) {
        return deliveryQuery.findDeliveryById(tenantId, missionId)
                .flatMap(delivery -> {
                    UUID person = delivery.getDeliveryPersonId();
                    if (person == null) {
                        return Mono.just(false);
                    }
                    if (person.equals(userId)) {
                        return Mono.just(true);
                    }
                    return deliveryQuery.resolveActorIdForDeliveryPerson(tenantId, person)
                            .map(userId::equals)
                            .onErrorResume(e -> {
                                log.warn("Cannot resolve the actor of delivery person {} (mission {}): {}",
                                        person, missionId, e.getMessage());
                                return Mono.just(false);
                            });
                })
                .onErrorResume(DeliveryNotFoundException.class, notOurs -> Mono.just(true));
    }
}
