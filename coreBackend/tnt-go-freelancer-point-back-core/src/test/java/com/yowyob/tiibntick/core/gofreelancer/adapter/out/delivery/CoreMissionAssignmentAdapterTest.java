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

@DisplayName("Who may feed a mission's GPS track")
class CoreMissionAssignmentAdapterTest {

    private final DeliveryQueryUseCase query = mock(DeliveryQueryUseCase.class);
    private final CoreMissionAssignmentAdapter adapter = new CoreMissionAssignmentAdapter(query);
    private final UUID tenant = UUID.randomUUID();
    private final UUID mission = UUID.randomUUID();
    private final UUID person = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private void deliveryWith(UUID deliveryPersonId) {
        Delivery d = Delivery.builder().id(mission).tenantId(tenant).deliveryPersonId(deliveryPersonId).build();
        when(query.findDeliveryById(tenant, mission)).thenReturn(Mono.just(d));
    }

    @Test
    @DisplayName("the actor behind the assigned delivery person is allowed")
    void assignedActor() {
        deliveryWith(person);
        when(query.resolveActorIdForDeliveryPerson(tenant, person)).thenReturn(Mono.just(actor));
        StepVerifier.create(adapter.isMissionAssignedTo(tenant, mission, actor)).expectNext(true).verifyComplete();
    }

    @Test
    @DisplayName("somebody else is refused")
    void otherUser() {
        deliveryWith(person);
        when(query.resolveActorIdForDeliveryPerson(tenant, person)).thenReturn(Mono.just(actor));
        StepVerifier.create(adapter.isMissionAssignedTo(tenant, mission, UUID.randomUUID()))
                .expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("a delivery nobody carries, or whose person has no actor, is refused")
    void unassignedOrUnresolvable() {
        deliveryWith(null);
        StepVerifier.create(adapter.isMissionAssignedTo(tenant, mission, actor)).expectNext(false).verifyComplete();
        deliveryWith(person);
        when(query.resolveActorIdForDeliveryPerson(tenant, person))
                .thenReturn(Mono.error(new IllegalStateException("no actor")));
        StepVerifier.create(adapter.isMissionAssignedTo(tenant, mission, actor)).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("a mission id that is not a core delivery is not judged here")
    void notACoreDelivery() {
        when(query.findDeliveryById(tenant, mission)).thenReturn(Mono.error(new DeliveryNotFoundException(mission)));
        StepVerifier.create(adapter.isMissionAssignedTo(tenant, mission, actor)).expectNext(true).verifyComplete();
    }
}
