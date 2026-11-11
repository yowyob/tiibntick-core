package com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryNotFoundException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeParticipationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Answers dispute-core's "did this actor take part in this mission?" from tnt-delivery-core: the sender
 * (client), the assigned deliverer and the organisation the delivery was assigned to are participants.
 * A mission id that is not a core delivery is not ours to judge and is accepted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoreDisputeParticipationAdapter implements IDisputeParticipationPort {

    private final DeliveryQueryUseCase deliveryQuery;

    @Override
    public Mono<Boolean> isParticipant(String tenantId, String missionId, String actorId, String organizationId) {
        UUID tenant = parse(tenantId);
        UUID mission = parse(missionId);
        UUID actor = parse(actorId);
        if (tenant == null || mission == null) {
            return Mono.just(true); // not a core delivery identifier: not judged here
        }
        if (actor == null) {
            return Mono.just(false);
        }
        return deliveryQuery.findDeliveryById(tenant, mission)
                .flatMap(delivery -> participates(tenant, delivery, actor, organizationId))
                .onErrorResume(DeliveryNotFoundException.class, notOurs -> Mono.just(true));
    }

    private Mono<Boolean> participates(UUID tenant, Delivery delivery, UUID actor, String organizationId) {
        if (actor.equals(delivery.getSenderId())) {
            return Mono.just(true);
        }
        if (organizationId != null && organizationId.equalsIgnoreCase(delivery.getAssignedFreelancerOrgId())) {
            return Mono.just(true);
        }
        UUID person = delivery.getDeliveryPersonId();
        if (person == null) {
            return Mono.just(false);
        }
        if (person.equals(actor)) {
            return Mono.just(true);
        }
        return deliveryQuery.resolveActorIdForDeliveryPerson(tenant, person)
                .map(actor::equals)
                .onErrorResume(e -> {
                    log.warn("Cannot resolve the actor of delivery person {}: {}", person, e.getMessage());
                    return Mono.just(false);
                });
    }

    private static UUID parse(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
