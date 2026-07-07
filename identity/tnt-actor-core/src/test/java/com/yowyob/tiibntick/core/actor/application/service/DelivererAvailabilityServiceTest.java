package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.AssignMissionCommand;
import com.yowyob.tiibntick.core.actor.application.command.ReleaseMissionCommand;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.domain.exception.ActorNotAvailableException;
import com.yowyob.tiibntick.core.actor.domain.exception.DelivererNotFoundException;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DelivererAvailabilityServiceTest {

    @Mock
    private IDelivererRepository delivererRepository;
    @Mock
    private IActorEventPublisher eventPublisher;

    private DelivererAvailabilityService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID  = UUID.randomUUID();
    private static final UUID AGENCY_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DelivererAvailabilityService(delivererRepository, eventPublisher);
    }

    private DelivererProfile activeAvailableDeliverer() {
        return DelivererProfile.create(TENANT_ID, ACTOR_ID, AGENCY_ID, BRANCH_ID, 50.0, DelivererType.PERMANENT)
                .activate();
    }

    @Test
    void assignMission_whenDelivererIsAvailable_shouldAssignAndPublishEvent() {
        UUID missionId = UUID.randomUUID();
        DelivererProfile available = activeAvailableDeliverer();

        when(delivererRepository.findByActorId(TENANT_ID, ACTOR_ID)).thenReturn(Mono.just(available));
        when(delivererRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishMissionAssigned(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.assignMission(new AssignMissionCommand(TENANT_ID, ACTOR_ID, missionId)))
                .assertNext(profile -> {
                    assert profile.hasActiveMission();
                    assert profile.missionActiveId().equals(missionId);
                })
                .verifyComplete();
    }

    @Test
    void assignMission_whenDelivererNotFound_shouldError() {
        when(delivererRepository.findByActorId(TENANT_ID, ACTOR_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.assignMission(new AssignMissionCommand(TENANT_ID, ACTOR_ID, UUID.randomUUID())))
                .expectError(DelivererNotFoundException.class)
                .verify();
    }

    @Test
    void assignMission_whenDelivererIsInactive_shouldError() {
        DelivererProfile inactive = DelivererProfile.create(
                TENANT_ID, ACTOR_ID, AGENCY_ID, BRANCH_ID, 50.0, DelivererType.PERMANENT);

        when(delivererRepository.findByActorId(TENANT_ID, ACTOR_ID)).thenReturn(Mono.just(inactive));

        StepVerifier.create(service.assignMission(new AssignMissionCommand(TENANT_ID, ACTOR_ID, UUID.randomUUID())))
                .expectError(ActorNotAvailableException.class)
                .verify();
    }

    @Test
    void releaseMission_shouldClearActiveMission() {
        UUID missionId = UUID.randomUUID();
        DelivererProfile onMission = activeAvailableDeliverer().assignMission(missionId);

        when(delivererRepository.findByActorId(TENANT_ID, ACTOR_ID)).thenReturn(Mono.just(onMission));
        when(delivererRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.releaseMission(new ReleaseMissionCommand(TENANT_ID, ACTOR_ID, missionId)))
                .assertNext(profile -> {
                    assert !profile.hasActiveMission();
                    assert profile.isAvailableForMission();
                })
                .verifyComplete();
    }
}
