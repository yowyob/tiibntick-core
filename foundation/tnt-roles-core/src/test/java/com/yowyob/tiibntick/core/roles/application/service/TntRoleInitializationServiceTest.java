package com.yowyob.tiibntick.core.roles.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.domain.model.Role;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TntRoleInitializationServiceTest {

    private static final UUID SYSTEM_TENANT_ID = UUID.randomUUID();

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RoleSyncOutboxRepository outboxRepository;
    @Mock
    private TransactionalOperator transactionalOperator;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TntRoleDefinitionRegistry registry = new TntRoleDefinitionRegistry();

    private TntRoleInitializationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new TntRoleInitializationService(
                registry, roleRepository, outboxRepository, transactionalOperator, objectMapper, SYSTEM_TENANT_ID);
        lenient().when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void provisionForTenant_noRolesSeededYet_shouldSaveAndEnqueueAllNineDefinitions() {
        when(roleRepository.existsByCode(eq(SYSTEM_TENANT_ID), anyString())).thenReturn(Mono.just(false));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(outboxRepository.save(any(RoleSyncOutboxEntry.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.provisionForTenant(SYSTEM_TENANT_ID))
                .verifyComplete();

        verify(roleRepository, times(registry.size())).save(any(Role.class));
        verify(outboxRepository, times(registry.size())).save(any(RoleSyncOutboxEntry.class));
    }

    @Test
    void provisionForTenant_calledAgainAfterAllRolesAlreadySeeded_shouldBeIdempotentAndNeverReenqueue() {
        when(roleRepository.existsByCode(eq(SYSTEM_TENANT_ID), anyString())).thenReturn(Mono.just(true));

        StepVerifier.create(service.provisionForTenant(SYSTEM_TENANT_ID))
                .verifyComplete();

        verify(roleRepository, never()).save(any(Role.class));
        verify(outboxRepository, never()).save(any(RoleSyncOutboxEntry.class));
    }

    @Test
    void provisionForTenant_partiallySeeded_shouldOnlyProvisionMissingDefinitions() {
        // Every code already exists except AGENCY_MANAGER.
        when(roleRepository.existsByCode(eq(SYSTEM_TENANT_ID), anyString())).thenReturn(Mono.just(true));
        when(roleRepository.existsByCode(SYSTEM_TENANT_ID, "AGENCY_MANAGER")).thenReturn(Mono.just(false));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(outboxRepository.save(any(RoleSyncOutboxEntry.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.provisionForTenant(SYSTEM_TENANT_ID))
                .verifyComplete();

        verify(roleRepository, times(1)).save(any(Role.class));
        verify(outboxRepository, times(1)).save(any(RoleSyncOutboxEntry.class));
    }
}
