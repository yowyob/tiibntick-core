package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.port.out.IKernelActorPort;
import com.yowyob.tiibntick.core.actor.domain.model.KernelActorDto;
import com.yowyob.tiibntick.core.actor.domain.model.KernelBusinessActorDto;
import com.yowyob.tiibntick.core.actor.domain.model.UpdateActorIdentityCommand;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActorIdentityResolutionServiceTest {

    private final IKernelActorPort port = mock(IKernelActorPort.class);
    private final ActorIdentityResolutionService service = new ActorIdentityResolutionService(port);

    @Test
    void resolveCurrent_mapsBusinessActorNameAndContactPhone() {
        UUID actorId = UUID.randomUUID();
        when(port.findCurrent()).thenReturn(Mono.just(
                new KernelBusinessActorDto(UUID.randomUUID(), actorId, "Jean Mballa", "699000000", "INDIVIDUAL", true)));

        StepVerifier.create(service.resolveCurrent())
                .assertNext(i -> {
                    org.assertj.core.api.Assertions.assertThat(i.actorId()).isEqualTo(actorId);
                    org.assertj.core.api.Assertions.assertThat(i.displayName()).isEqualTo("Jean Mballa");
                    org.assertj.core.api.Assertions.assertThat(i.phoneNumber()).isEqualTo("699000000");
                })
                .verifyComplete();
    }

    @Test
    void resolveCurrent_isEmptyWhenKernelHasNoActor() {
        when(port.findCurrent()).thenReturn(Mono.empty());
        StepVerifier.create(service.resolveCurrent()).verifyComplete();
    }

    @Test
    void updateCurrent_returnsFirstAndLastNameFromKernelResponse() {
        UUID id = UUID.randomUUID();
        var cmd = new UpdateActorIdentityCommand("Jean", "Mballa", "699000000");
        when(port.updateCurrentIdentity(cmd)).thenReturn(Mono.just(
                new KernelActorDto(id, null, null, "Jean", "Mballa", null, "Jean Mballa", "699000000", "j@x.cm", "INDIVIDUAL")));

        StepVerifier.create(service.updateCurrent(cmd))
                .assertNext(i -> {
                    org.assertj.core.api.Assertions.assertThat(i.firstName()).isEqualTo("Jean");
                    org.assertj.core.api.Assertions.assertThat(i.lastName()).isEqualTo("Mballa");
                    org.assertj.core.api.Assertions.assertThat(i.displayName()).isEqualTo("Jean Mballa");
                })
                .verifyComplete();
    }

    @Test
    void updateCurrent_propagatesKernelErrors() {
        var cmd = new UpdateActorIdentityCommand("Jean", null, null);
        when(port.updateCurrentIdentity(cmd)).thenReturn(Mono.error(new IllegalStateException("kernel down")));
        StepVerifier.create(service.updateCurrent(cmd)).expectError(IllegalStateException.class).verify();
    }
}
