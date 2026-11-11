package com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryNotFoundException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Who may open a dispute on a delivery")
class CoreDisputeParticipationAdapterTest {

    private final DeliveryQueryUseCase query = mock(DeliveryQueryUseCase.class);
    private final CoreDisputeParticipationAdapter adapter = new CoreDisputeParticipationAdapter(query);
    private final UUID tenant = UUID.randomUUID();
    private final UUID mission = UUID.randomUUID();
    private final UUID sender = UUID.randomUUID();
    private final UUID person = UUID.randomUUID();
    private final UUID driverActor = UUID.randomUUID();

    private void delivery(String orgId, UUID deliveryPerson) {
        Delivery d = Delivery.builder().id(mission).tenantId(tenant).senderId(sender)
                .deliveryPersonId(deliveryPerson).assignedFreelancerOrgId(orgId).build();
        when(query.findDeliveryById(tenant, mission)).thenReturn(Mono.just(d));
        if (deliveryPerson != null) {
            when(query.resolveActorIdForDeliveryPerson(tenant, deliveryPerson)).thenReturn(Mono.just(driverActor));
        }
    }

    private Mono<Boolean> ask(UUID actor, String org) {
        return adapter.isParticipant(tenant.toString(), mission.toString(), actor.toString(), org);
    }

    @Test
    @DisplayName("the client who sent the parcel and the driver who carried it may")
    void clientAndDriver() {
        delivery(null, person);
        StepVerifier.create(ask(sender, null)).expectNext(true).verifyComplete();
        StepVerifier.create(ask(driverActor, null)).expectNext(true).verifyComplete();
    }

    @Test
    @DisplayName("a member of the organisation the delivery was assigned to may")
    void organisation() {
        UUID org = UUID.randomUUID();
        delivery(org.toString(), null);
        StepVerifier.create(ask(UUID.randomUUID(), org.toString())).expectNext(true).verifyComplete();
    }

    @Test
    @DisplayName("a stranger may not, whether or not a driver is assigned")
    void stranger() {
        delivery(null, person);
        StepVerifier.create(ask(UUID.randomUUID(), UUID.randomUUID().toString())).expectNext(false).verifyComplete();
        delivery(null, null);
        StepVerifier.create(ask(UUID.randomUUID(), null)).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("a mission that is not a core delivery is not judged here")
    void unknownMission() {
        when(query.findDeliveryById(tenant, mission)).thenReturn(Mono.error(new DeliveryNotFoundException(mission)));
        StepVerifier.create(ask(UUID.randomUUID(), null)).expectNext(true).verifyComplete();
        StepVerifier.create(adapter.isParticipant(tenant.toString(), "MISSION-LEGACY-42", UUID.randomUUID().toString(), null))
                .expectNext(true).verifyComplete();
        StepVerifier.create(adapter.isParticipant(tenant.toString(), null, UUID.randomUUID().toString(), null))
                .expectNext(true).verifyComplete();
    }
}
