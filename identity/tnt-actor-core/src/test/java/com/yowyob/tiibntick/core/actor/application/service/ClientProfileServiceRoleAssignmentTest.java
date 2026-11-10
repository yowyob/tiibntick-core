package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.CreateClientProfileCommand;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IClientProfileRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelActorPort;
import com.yowyob.tiibntick.core.actor.domain.model.ClientProfile;
import com.yowyob.tiibntick.core.roles.application.port.in.AssignTntRoleUseCase;
import com.yowyob.tiibntick.core.roles.application.port.in.TntRoleAssignmentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The CLIENT role must be granted when a client profile is created — best-effort, like FREELANCER. */
@ExtendWith(MockitoExtension.class)
class ClientProfileServiceRoleAssignmentTest {

    @Mock private IClientProfileRepository repository;
    @Mock private IKernelActorPort kernelActorPort;
    @Mock private IActorEventPublisher eventPublisher;
    @Mock private AssignTntRoleUseCase assignRoleUseCase;

    private ClientProfileService service;
    private UUID tenantId;
    private UUID actorId;
    private ClientProfile profile;

    @BeforeEach
    void setUp() {
        service = new ClientProfileService(repository, kernelActorPort, eventPublisher, assignRoleUseCase);
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        profile = ClientProfile.create(tenantId, actorId);
    }

    @Test
    @DisplayName("a new client profile triggers the CLIENT role grant")
    void newProfileGrantsClientRole() {
        when(repository.existsByActorId(tenantId, actorId)).thenReturn(Mono.just(false));
        when(kernelActorPort.exists(actorId)).thenReturn(Mono.just(true));
        when(repository.save(any())).thenReturn(Mono.just(profile));
        when(eventPublisher.publishActorStatusChanged(any())).thenReturn(Mono.empty());
        when(assignRoleUseCase.assignRole(tenantId, actorId, "CLIENT", tenantId))
                .thenReturn(Mono.just(new TntRoleAssignmentResult(UUID.randomUUID(), actorId, "CLIENT", "TENANT", tenantId)));

        StepVerifier.create(service.createClientProfile(new CreateClientProfileCommand(tenantId, actorId)))
                .assertNext(p -> assertThat(p.actorId()).isEqualTo(actorId))
                .verifyComplete();

        verify(assignRoleUseCase).assignRole(tenantId, actorId, "CLIENT", tenantId);
    }

    @Test
    @DisplayName("an existing profile is granted too, so accounts created before the fix get their role")
    void existingProfileIsGrantedToo() {
        when(repository.existsByActorId(tenantId, actorId)).thenReturn(Mono.just(true));
        when(repository.findByActorId(tenantId, actorId)).thenReturn(Mono.just(profile));
        when(assignRoleUseCase.assignRole(tenantId, actorId, "CLIENT", tenantId))
                .thenReturn(Mono.just(new TntRoleAssignmentResult(UUID.randomUUID(), actorId, "CLIENT", "TENANT", tenantId)));

        StepVerifier.create(service.createClientProfile(new CreateClientProfileCommand(tenantId, actorId)))
                .expectNextCount(1).verifyComplete();

        verify(assignRoleUseCase, times(1)).assignRole(tenantId, actorId, "CLIENT", tenantId);
    }

    @Test
    @DisplayName("a failing grant never blocks profile creation")
    void grantFailureStillReturnsTheProfile() {
        when(repository.existsByActorId(tenantId, actorId)).thenReturn(Mono.just(true));
        when(repository.findByActorId(tenantId, actorId)).thenReturn(Mono.just(profile));
        when(assignRoleUseCase.assignRole(tenantId, actorId, "CLIENT", tenantId))
                .thenReturn(Mono.error(new IllegalStateException("kernel down")));

        StepVerifier.create(service.createClientProfile(new CreateClientProfileCommand(tenantId, actorId)))
                .assertNext(p -> assertThat(p.actorId()).isEqualTo(actorId))
                .verifyComplete();
    }
}
