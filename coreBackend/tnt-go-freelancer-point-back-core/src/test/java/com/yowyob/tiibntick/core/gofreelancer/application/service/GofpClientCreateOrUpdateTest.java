package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpClientRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves that a second {@code createOrUpdate} on the same {@code coreClientId}
 * issues an UPDATE (isNew=false) instead of a duplicate INSERT (isNew=true → 409).
 */
@ExtendWith(MockitoExtension.class)
class GofpClientCreateOrUpdateTest {

    @Mock private GofpClientRepository clientRepository;
    @Mock private GofpUserRepository userRepository;
    @Mock private com.yowyob.tiibntick.core.actor.application.port.out.IClientProfileRepository clientProfiles;
    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private com.yowyob.tiibntick.core.tp.application.service.LoyaltyService loyaltyService;
    @Mock private com.yowyob.tiibntick.core.actor.application.port.in.ICreateClientProfileUseCase createClientProfile;

    private GofpClientService service;

    @BeforeEach
    void setUp() {
        service = new GofpClientService(clientRepository, userRepository, loyaltyService, clientProfiles, tenantContextHolder,
                createClientProfile);
    }

    @Test
    void createOrUpdate_whenRecordExists_savesWithIsNewFalse() {
        UUID coreClientId = UUID.randomUUID();
        UUID existingPk = UUID.randomUUID();

        GofpClient existing = new GofpClient();
        existing.setId(existingPk);
        existing.setCoreClientId(coreClientId);
        existing.markNotNew();

        GofpClient incoming = new GofpClient();
        incoming.setCoreClientId(coreClientId);
        assertThat(incoming.isNew()).as("incoming entity from @RequestBody is new").isTrue();

        when(clientRepository.findByCoreClientId(coreClientId))
                .thenReturn(Mono.just(existing));
        when(clientRepository.save(any(GofpClient.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        // coreUserId not set → hydrate() short-circuits, no userRepository call needed

        StepVerifier.create(service.createOrUpdate(incoming))
                .expectNextMatches(c -> existingPk.equals(c.getId()))
                .verifyComplete();

        ArgumentCaptor<GofpClient> captor = ArgumentCaptor.forClass(GofpClient.class);
        verify(clientRepository).save(captor.capture());
        assertThat(captor.getValue().isNew())
                .as("entity passed to save() must have isNew=false (UPDATE, not INSERT)")
                .isFalse();
        assertThat(captor.getValue().getId()).isEqualTo(existingPk);
    }

    private static final UUID TENANT = UUID.randomUUID();

    private GofpClient newClient(UUID coreClientId, UUID actor) {
        GofpClient c = new GofpClient();
        c.setId(UUID.randomUUID()); // ignored: the id is the client profile id
        c.setCoreClientId(coreClientId);
        c.setCoreUserId(actor);
        return c;
    }

    /** New row whose coreClientId is a client profile id: the row takes that id (lot L3, C1). */
    @Test
    void createOrUpdate_newRecord_takesTheClientProfileId() {
        UUID actor = UUID.randomUUID();
        var profile = com.yowyob.tiibntick.core.actor.domain.model.ClientProfile.create(TENANT, actor);
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(TENANT));
        when(clientRepository.findByCoreClientId(profile.id())).thenReturn(Mono.empty());
        when(clientProfiles.findById(TENANT, profile.id())).thenReturn(Mono.just(profile));
        when(clientRepository.save(any(GofpClient.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        org.mockito.Mockito.lenient().when(clientProfiles.findByActorId(TENANT, actor)).thenReturn(Mono.just(profile));
        org.mockito.Mockito.lenient().when(userRepository.findByCoreUserId(actor)).thenReturn(Mono.empty());
        org.mockito.Mockito.lenient().when(loyaltyService.getByThirdPartyId(TENANT, actor)).thenReturn(Mono.empty());

        GofpClient saved = service.createOrUpdate(newClient(profile.id(), actor)).block();

        assertThat(saved.getId()).isEqualTo(profile.id());
        org.mockito.Mockito.verifyNoInteractions(createClientProfile);
    }

    /** coreClientId unknown to actor-core: the actor's client profile is found or created by the core. */
    @Test
    void createOrUpdate_newRecord_withoutProfile_usesTheProfileFoundOrCreatedByTheCore() {
        UUID actor = UUID.randomUUID();
        UUID goId = UUID.randomUUID();
        var profile = com.yowyob.tiibntick.core.actor.domain.model.ClientProfile.create(TENANT, actor);
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(TENANT));
        when(clientRepository.findByCoreClientId(goId)).thenReturn(Mono.empty());
        when(clientProfiles.findById(TENANT, goId)).thenReturn(Mono.empty());
        when(createClientProfile.createClientProfile(any())).thenReturn(Mono.just(profile));
        when(clientRepository.save(any(GofpClient.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        org.mockito.Mockito.lenient().when(clientProfiles.findByActorId(TENANT, actor)).thenReturn(Mono.just(profile));
        org.mockito.Mockito.lenient().when(userRepository.findByCoreUserId(actor)).thenReturn(Mono.empty());
        org.mockito.Mockito.lenient().when(loyaltyService.getByThirdPartyId(TENANT, actor)).thenReturn(Mono.empty());

        GofpClient saved = service.createOrUpdate(newClient(goId, actor)).block();

        assertThat(saved.getId()).isEqualTo(profile.id());
        var cmd = ArgumentCaptor.forClass(com.yowyob.tiibntick.core.actor.application.command.CreateClientProfileCommand.class);
        verify(createClientProfile).createClientProfile(cmd.capture());
        assertThat(cmd.getValue().actorId()).isEqualTo(actor);
        assertThat(cmd.getValue().tenantId()).isEqualTo(TENANT);
    }

    @Test
    void createOrUpdate_newRecord_withoutProfileNorActor_isRejected() {
        UUID goId = UUID.randomUUID();
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(TENANT));
        when(clientRepository.findByCoreClientId(goId)).thenReturn(Mono.empty());
        when(clientProfiles.findById(TENANT, goId)).thenReturn(Mono.empty());

        StepVerifier.create(service.createOrUpdate(newClient(goId, null)))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(clientRepository, org.mockito.Mockito.never()).save(any());
    }
}
