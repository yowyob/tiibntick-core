package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.actor.application.port.in.ICreateClientProfileUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindClientUseCase;
import com.yowyob.tiibntick.core.actor.domain.model.ClientProfile;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** C-07: an unknown client id must resolve to a 404, not to a fake empty client. */
class ClientApplicationServiceGetByIdTest {

    private final IFindClientUseCase find = mock(IFindClientUseCase.class);
    private final TenantContextHolder tenant = mock(TenantContextHolder.class);
    private final ClientApplicationService service = new ClientApplicationService(
            mock(ICreateClientProfileUseCase.class), find, tenant, mock(PasswordHasherService.class));

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void returnsTheClientFoundByProfileId() {
        ClientProfile profile = ClientProfile.create(tenantId, UUID.randomUUID());
        when(tenant.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(find.findById(tenantId, profile.id())).thenReturn(Mono.just(profile));

        StepVerifier.create(service.getClientById(profile.id()))
                .assertNext(dto -> {
                    org.assertj.core.api.Assertions.assertThat(dto.getId()).isEqualTo(profile.id());
                    org.assertj.core.api.Assertions.assertThat(dto.getStatus()).isEqualTo("ACTIVE");
                })
                .verifyComplete();
    }

    @Test
    void failsWithResourceNotFoundWhenNeitherProfileIdNorActorIdMatch() {
        UUID unknown = UUID.randomUUID();
        when(tenant.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(find.findById(tenantId, unknown)).thenReturn(Mono.empty());
        when(find.findByActorId(tenantId, unknown)).thenReturn(Mono.empty());

        StepVerifier.create(service.getClientById(unknown))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }
}
