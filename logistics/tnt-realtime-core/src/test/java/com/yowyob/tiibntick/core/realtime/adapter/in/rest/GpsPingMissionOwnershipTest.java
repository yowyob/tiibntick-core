package com.yowyob.tiibntick.core.realtime.adapter.in.rest;

import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.realtime.application.port.in.IProcessGpsPingUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.out.IMissionAssignmentPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("A deliverer can only feed the mission they carry")
class GpsPingMissionOwnershipTest {

    private final IProcessGpsPingUseCase process = mock(IProcessGpsPingUseCase.class);
    private final IMissionAssignmentPort assignment = mock(IMissionAssignmentPort.class);
    private final GpsPingRestController controller = new GpsPingRestController(process, assignment);
    private final UUID user = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();
    private final UUID mission = UUID.randomUUID();

    private TntUserIdentity me() {
        return new TntUserIdentity(user, tenant, UUID.randomUUID(), null, null, Set.of(), true);
    }

    private static GpsPingRestController.GpsPingRestRequest ping(String missionId) {
        return new GpsPingRestController.GpsPingRestRequest(null, missionId, 3.8, 11.5, null, null, null);
    }

    @Test
    @DisplayName("a ping for a mission the caller carries is processed")
    void ownMission() {
        when(assignment.isMissionAssignedTo(tenant, mission, user)).thenReturn(Mono.just(true));
        when(process.processGpsPing(any())).thenReturn(Mono.empty());
        StepVerifier.create(controller.ping(me(), ping(mission.toString())))
                .expectNextMatches(r -> r.getStatusCode() == HttpStatus.NO_CONTENT).verifyComplete();
        verify(process).processGpsPing(any());
    }

    @Test
    @DisplayName("a ping into somebody else's mission is refused with 403 and never processed")
    void foreignMission() {
        when(assignment.isMissionAssignedTo(tenant, mission, user)).thenReturn(Mono.just(false));
        StepVerifier.create(controller.ping(me(), ping(mission.toString())))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN)).verify();
        verify(process, never()).processGpsPing(any());
    }

    @Test
    @DisplayName("a malformed mission id is a 400")
    void malformedMission() {
        StepVerifier.create(controller.ping(me(), ping("mission-1")))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST)).verify();
    }

    @Test
    @DisplayName("a position without a mission (free-roaming livreur) needs no assignment")
    void noMission() {
        when(process.processGpsPing(any())).thenReturn(Mono.empty());
        StepVerifier.create(controller.ping(me(), ping(null)))
                .expectNextMatches(r -> r.getStatusCode() == HttpStatus.NO_CONTENT).verifyComplete();
        verify(assignment, never()).isMissionAssignedTo(any(), any(), any());
    }
}
