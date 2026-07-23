package com.yowyob.tiibntick.core.roles.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import com.yowyob.tiibntick.core.roles.domain.model.Role;
import com.yowyob.tiibntick.core.roles.domain.model.RoleScopeType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncAggregateType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOperation;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TntRoleManagementServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RoleSyncOutboxRepository outboxRepository;
    @Mock
    private TransactionalOperator transactionalOperator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TntRoleManagementService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new TntRoleManagementService(roleRepository, outboxRepository, transactionalOperator, objectMapper);
        lenient().when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ── createRole ───────────────────────────────────────────────

    @Test
    void createRole_codeCollidesWithCanonicalRole_shouldErrorAndNeverSave() {
        StepVerifier.create(service.createRole(TENANT_ID, "AGENCY_MANAGER", "My Custom Manager", RoleScopeType.AGENCY, Set.of("mission:read")))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(TntRoleException.class);
                    assertThat(((TntRoleException) err).getCode()).isEqualTo("ROLE_CODE_RESERVED");
                })
                .verify();

        verifyNoInteractions(roleRepository, outboxRepository);
    }

    @Test
    void createRole_customCode_shouldSaveRoleAndEnqueueProvisionRoleOutboxEntry() throws Exception {
        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        when(roleRepository.save(roleCaptor.capture())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        ArgumentCaptor<RoleSyncOutboxEntry> outboxCaptor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        when(outboxRepository.save(outboxCaptor.capture())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.createRole(TENANT_ID, "CUSTOM_DISPATCHER", "Custom Dispatcher", RoleScopeType.TENANT, Set.of("mission:read", "mission:assign")))
                .assertNext(role -> {
                    assertThat(role.code()).isEqualTo("CUSTOM_DISPATCHER");
                    assertThat(role.tenantId()).isEqualTo(TENANT_ID);
                    assertThat(role.permissions()).containsExactlyInAnyOrder("mission:read", "mission:assign");
                })
                .verifyComplete();

        Role savedRole = roleCaptor.getValue();
        RoleSyncOutboxEntry outboxEntry = outboxCaptor.getValue();
        assertThat(outboxEntry.operation()).isEqualTo(RoleSyncOperation.PROVISION_ROLE);
        assertThat(outboxEntry.aggregateType()).isEqualTo(RoleSyncAggregateType.ROLE);
        assertThat(outboxEntry.aggregateId()).isEqualTo(savedRole.id());
        assertThat(outboxEntry.tenantId()).isEqualTo(TENANT_ID);

        JsonNode payload = objectMapper.readTree(outboxEntry.payload());
        assertThat(payload.get("tenantId").asText()).isEqualTo(TENANT_ID.toString());
        assertThat(payload.get("code").asText()).isEqualTo("CUSTOM_DISPATCHER");
        assertThat(payload.get("scopeType").asText()).isEqualTo("TENANT");

        verify(transactionalOperator).transactional(any(Mono.class));
    }

    // ── updateRole ───────────────────────────────────────────────

    @Test
    void updateRole_roleNotFound_shouldError() {
        UUID roleId = UUID.randomUUID();
        when(roleRepository.findById(TENANT_ID, roleId)).thenReturn(Mono.empty());

        StepVerifier.create(service.updateRole(TENANT_ID, roleId, "New name", Set.of("mission:read")))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(TntRoleException.class);
                    assertThat(((TntRoleException) err).getCode()).isEqualTo("ROLE_NOT_FOUND");
                })
                .verify();
    }

    @Test
    void updateRole_systemRole_shouldErrorAndNeverSave() {
        UUID roleId = UUID.randomUUID();
        Role systemRole = new Role(roleId, TENANT_ID, "AGENCY_MANAGER", "Agency Manager", RoleScopeType.AGENCY, Set.of("mission:read"));
        when(roleRepository.findById(TENANT_ID, roleId)).thenReturn(Mono.just(systemRole));

        StepVerifier.create(service.updateRole(TENANT_ID, roleId, "New name", Set.of("mission:read")))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(TntRoleException.class);
                    assertThat(((TntRoleException) err).getCode()).isEqualTo("ROLE_SYSTEM_NOT_EDITABLE");
                })
                .verify();

        verify(roleRepository, never()).save(any());
    }

    @Test
    void updateRole_customRole_shouldSaveLocallyAndNeverTouchOutbox() {
        UUID roleId = UUID.randomUUID();
        Role existing = new Role(roleId, TENANT_ID, "CUSTOM_DISPATCHER", "Old name", RoleScopeType.TENANT, Set.of("mission:read"));
        when(roleRepository.findById(TENANT_ID, roleId)).thenReturn(Mono.just(existing));
        when(roleRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.updateRole(TENANT_ID, roleId, "New name", Set.of("mission:read", "mission:assign")))
                .assertNext(updated -> {
                    assertThat(updated.name()).isEqualTo("New name");
                    assertThat(updated.permissions()).containsExactlyInAnyOrder("mission:read", "mission:assign");
                    assertThat(updated.code()).isEqualTo("CUSTOM_DISPATCHER");
                    assertThat(updated.id()).isEqualTo(roleId);
                })
                .verifyComplete();

        verifyNoInteractions(outboxRepository);
    }

    // ── deleteRole ───────────────────────────────────────────────

    @Test
    void deleteRole_roleNotFound_shouldError() {
        UUID roleId = UUID.randomUUID();
        when(roleRepository.findById(TENANT_ID, roleId)).thenReturn(Mono.empty());

        StepVerifier.create(service.deleteRole(TENANT_ID, roleId))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(TntRoleException.class);
                    assertThat(((TntRoleException) err).getCode()).isEqualTo("ROLE_NOT_FOUND");
                })
                .verify();
    }

    @Test
    void deleteRole_systemRole_shouldErrorAndNeverDelete() {
        UUID roleId = UUID.randomUUID();
        Role systemRole = new Role(roleId, TENANT_ID, "AGENCY_MANAGER", "Agency Manager", RoleScopeType.AGENCY, Set.of());
        when(roleRepository.findById(TENANT_ID, roleId)).thenReturn(Mono.just(systemRole));

        StepVerifier.create(service.deleteRole(TENANT_ID, roleId))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(TntRoleException.class);
                    assertThat(((TntRoleException) err).getCode()).isEqualTo("ROLE_SYSTEM_NOT_EDITABLE");
                })
                .verify();

        verify(roleRepository, never()).deleteById(any(), any());
    }

    @Test
    void deleteRole_neverSyncedToKernel_shouldDeleteLocallyAndNeverTouchOutbox() {
        UUID roleId = UUID.randomUUID();
        Role existing = new Role(roleId, TENANT_ID, "CUSTOM_DISPATCHER", "Custom Dispatcher", RoleScopeType.TENANT, Set.of());
        when(roleRepository.findById(TENANT_ID, roleId)).thenReturn(Mono.just(existing));
        when(roleRepository.findKernelRoleId(TENANT_ID, roleId)).thenReturn(Mono.empty());
        when(roleRepository.deleteById(TENANT_ID, roleId)).thenReturn(Mono.empty());

        StepVerifier.create(service.deleteRole(TENANT_ID, roleId))
                .verifyComplete();

        verify(roleRepository).deleteById(TENANT_ID, roleId);
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void deleteRole_alreadySyncedToKernel_shouldEnqueueDeleteRoleOutboxEntryThenDelete() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID kernelRoleId = UUID.randomUUID();
        Role existing = new Role(roleId, TENANT_ID, "CUSTOM_DISPATCHER", "Custom Dispatcher", RoleScopeType.TENANT, Set.of());
        when(roleRepository.findById(TENANT_ID, roleId)).thenReturn(Mono.just(existing));
        when(roleRepository.findKernelRoleId(TENANT_ID, roleId)).thenReturn(Mono.just(kernelRoleId));

        ArgumentCaptor<RoleSyncOutboxEntry> outboxCaptor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        when(outboxRepository.save(outboxCaptor.capture())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(roleRepository.deleteById(TENANT_ID, roleId)).thenReturn(Mono.empty());

        StepVerifier.create(service.deleteRole(TENANT_ID, roleId))
                .verifyComplete();

        verify(roleRepository).deleteById(TENANT_ID, roleId);

        RoleSyncOutboxEntry outboxEntry = outboxCaptor.getValue();
        assertThat(outboxEntry.operation()).isEqualTo(RoleSyncOperation.DELETE_ROLE);
        assertThat(outboxEntry.aggregateType()).isEqualTo(RoleSyncAggregateType.ROLE);
        assertThat(outboxEntry.aggregateId()).isEqualTo(roleId);
        assertThat(outboxEntry.tenantId()).isEqualTo(TENANT_ID);

        JsonNode payload = objectMapper.readTree(outboxEntry.payload());
        assertThat(payload.get("tenantId").asText()).isEqualTo(TENANT_ID.toString());
        assertThat(payload.get("kernelRoleId").asText()).isEqualTo(kernelRoleId.toString());
    }

    // ── listRoles ────────────────────────────────────────────────

    @Test
    void listRoles_shouldDelegateToRoleRepository() {
        Role role = new Role(UUID.randomUUID(), TENANT_ID, "CUSTOM_DISPATCHER", "Custom Dispatcher", RoleScopeType.TENANT, Set.of());
        when(roleRepository.findByTenantId(TENANT_ID)).thenReturn(Flux.just(role));

        StepVerifier.create(service.listRoles(TENANT_ID))
                .expectNext(role)
                .verifyComplete();
    }
}
