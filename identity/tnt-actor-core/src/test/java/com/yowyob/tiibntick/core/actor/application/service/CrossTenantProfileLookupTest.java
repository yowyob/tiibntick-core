package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.CreateClientProfileCommand;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IClientProfileRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelActorPort;
import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.actor.domain.model.ClientProfile;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.roles.application.port.in.AssignTntRoleUseCase;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A TiiBnTick profile belongs to the person, not to the space they signed in through: the token carries neither
 * the TiiBnTick roles nor necessarily the tenant the profile was created under.
 */
@ExtendWith(MockitoExtension.class)
class CrossTenantProfileLookupTest {

    @Mock private IClientProfileRepository clients;
    @Mock private IFreelancerRepository freelancers;
    @Mock private IDelivererRepository deliverers;
    @Mock private IRelayOperatorRepository relays;
    @Mock private IKernelActorPort kernel;
    @Mock private IActorEventPublisher events;
    @Mock private AssignTntRoleUseCase roles;

    private final UUID signedInTenant = UUID.randomUUID();
    private final UUID otherTenant = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void noRoleSideEffects() {
        org.mockito.Mockito.lenient().when(roles.assignRole(any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("findMine returns the client profile created under another tenant")
    void clientFoundAcrossTenants() {
        ClientProfile elsewhere = ClientProfile.create(otherTenant, actorId);
        when(clients.findByActorId(signedInTenant, actorId)).thenReturn(Mono.empty());
        when(clients.findFirstByActorId(actorId)).thenReturn(Mono.just(elsewhere));

        StepVerifier.create(new ClientProfileQueryService(clients).findMine(signedInTenant, actorId))
                .assertNext(p -> assertThat(p.tenantId()).isEqualTo(otherTenant))
                .verifyComplete();
    }

    @Test
    @DisplayName("findMine prefers the profile of the current tenant")
    void currentTenantWins() {
        ClientProfile here = ClientProfile.create(signedInTenant, actorId);
        when(clients.findByActorId(signedInTenant, actorId)).thenReturn(Mono.just(here));

        StepVerifier.create(new ClientProfileQueryService(clients).findMine(signedInTenant, actorId))
                .assertNext(p -> assertThat(p.tenantId()).isEqualTo(signedInTenant))
                .verifyComplete();
        verify(clients, never()).findFirstByActorId(any());
    }

    @Test
    @DisplayName("creating a client profile again from another tenant returns the existing one, no twin")
    void clientCreationIsIdempotentAcrossTenants() {
        ClientProfile elsewhere = ClientProfile.create(otherTenant, actorId);
        when(clients.existsByActorId(signedInTenant, actorId)).thenReturn(Mono.just(false));
        when(clients.findFirstByActorId(actorId)).thenReturn(Mono.just(elsewhere));

        ClientProfileService service = new ClientProfileService(clients, kernel, events, roles);
        StepVerifier.create(service.createClientProfile(new CreateClientProfileCommand(signedInTenant, actorId)))
                .assertNext(p -> assertThat(p.tenantId()).isEqualTo(otherTenant))
                .verifyComplete();
        verify(clients, never()).save(any());
    }

    @Test
    @DisplayName("the account's profile list gathers every kind, even from another tenant, with no role involved")
    void allProfilesListed() {
        ClientProfile client = ClientProfile.create(otherTenant, actorId);
        FreelancerProfile freelancer = mock(FreelancerProfile.class);
        UUID freelancerId = UUID.randomUUID();
        when(freelancer.id()).thenReturn(freelancerId);
        when(freelancer.tenantId()).thenReturn(otherTenant);

        when(clients.findByActorId(signedInTenant, actorId)).thenReturn(Mono.empty());
        when(clients.findFirstByActorId(actorId)).thenReturn(Mono.just(client));
        when(freelancers.findByActorId(signedInTenant, actorId)).thenReturn(Mono.empty());
        when(freelancers.findFirstByActorId(actorId)).thenReturn(Mono.just(freelancer));
        when(deliverers.findByActorId(signedInTenant, actorId)).thenReturn(Mono.empty());
        when(deliverers.findFirstByActorId(actorId)).thenReturn(Mono.empty());
        when(relays.findByActorId(signedInTenant, actorId)).thenReturn(Mono.empty());
        when(relays.findFirstByActorId(actorId)).thenReturn(Mono.empty());

        StepVerifier.create(new MyProfilesService(clients, freelancers, deliverers, relays)
                        .findAll(signedInTenant, actorId).collectList())
                .assertNext(list -> {
                    assertThat(list).extracting(r -> r.kind()).containsExactly("CLIENT", "FREELANCER");
                    assertThat(list.get(1).profileId()).isEqualTo(freelancerId);
                    assertThat(list).allSatisfy(r -> assertThat(r.tenantId()).isEqualTo(otherTenant));
                })
                .verifyComplete();
    }
}
