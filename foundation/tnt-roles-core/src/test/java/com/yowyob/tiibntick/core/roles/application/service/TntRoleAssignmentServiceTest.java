package com.yowyob.tiibntick.core.roles.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.application.port.in.TntRoleAssignmentResult;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import com.yowyob.tiibntick.core.roles.domain.model.Role;
import com.yowyob.tiibntick.core.roles.domain.model.RoleScopeType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncAggregateType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOperation;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TntRoleAssignmentServiceTest {

    private static final UUID SYSTEM_TENANT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID TARGET_USER_ID = UUID.randomUUID();
    private static final UUID SCOPE_ID = UUID.randomUUID();

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleAssignmentRepository assignmentRepository;
    @Mock
    private RoleSyncOutboxRepository outboxRepository;
    @Mock
    private TransactionalOperator transactionalOperator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TntRoleAssignmentService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new TntRoleAssignmentService(
                roleRepository, assignmentRepository, outboxRepository, transactionalOperator, objectMapper, SYSTEM_TENANT_ID);
        lenient().when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Role canonicalRole(String code, RoleScopeType scopeType) {
        return new Role(UUID.randomUUID(), SYSTEM_TENANT_ID, code, code, scopeType, Set.of("some:permission"));
    }

    @Test
    void assignRole_unknownRoleCode_shouldError() {
        StepVerifier.create(service.assignRole(TENANT_ID, TARGET_USER_ID, "NOT_A_ROLE", SCOPE_ID))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(TntRoleException.class);
                    assertThat(((TntRoleException) err).getCode()).isEqualTo("ROLE_UNKNOWN");
                })
                .verify();
    }

    @Test
    void assignRole_agencyScopedRoleWithoutScopeId_shouldError() {
        StepVerifier.create(service.assignRole(TENANT_ID, TARGET_USER_ID, "AGENCY_MANAGER", null))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(TntRoleException.class);
                    assertThat(((TntRoleException) err).getCode()).isEqualTo("ROLE_MISSING_SCOPE_ID");
                })
                .verify();
    }

    @Test
    void assignRole_agencyScopedRoleWithoutTenantId_shouldError() {
        StepVerifier.create(service.assignRole(null, TARGET_USER_ID, "AGENCY_MANAGER", SCOPE_ID))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(TntRoleException.class);
                    assertThat(((TntRoleException) err).getCode()).isEqualTo("ROLE_MISSING_TENANT_ID");
                })
                .verify();
    }

    @Test
    void assignRole_canonicalRoleNotYetSeeded_shouldError() {
        when(roleRepository.findByTenantId(SYSTEM_TENANT_ID)).thenReturn(Flux.empty());

        StepVerifier.create(service.assignRole(TENANT_ID, TARGET_USER_ID, "AGENCY_MANAGER", SCOPE_ID))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(TntRoleException.class);
                    assertThat(((TntRoleException) err).getCode()).isEqualTo("ROLE_NOT_SEEDED");
                })
                .verify();
    }

    @Test
    void assignRole_success_shouldSaveLocalAssignmentAndEnqueueCorrectOutboxPayload() throws Exception {
        Role localRole = canonicalRole("AGENCY_MANAGER", RoleScopeType.AGENCY);
        when(roleRepository.findByTenantId(SYSTEM_TENANT_ID)).thenReturn(Flux.just(localRole));

        ArgumentCaptor<UserRoleAssignment> assignmentCaptor = ArgumentCaptor.forClass(UserRoleAssignment.class);
        when(assignmentRepository.save(assignmentCaptor.capture()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        ArgumentCaptor<RoleSyncOutboxEntry> outboxCaptor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        when(outboxRepository.save(outboxCaptor.capture()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.assignRole(TENANT_ID, TARGET_USER_ID, "AGENCY_MANAGER", SCOPE_ID))
                .assertNext(result -> {
                    assertThat(result.targetUserId()).isEqualTo(TARGET_USER_ID);
                    assertThat(result.roleCode()).isEqualTo("AGENCY_MANAGER");
                    assertThat(result.scopeType()).isEqualTo("AGENCY");
                    assertThat(result.scopeId()).isEqualTo(SCOPE_ID);
                    assertThat(result.assignmentId()).isEqualTo(assignmentCaptor.getValue().id());
                })
                .verifyComplete();

        UserRoleAssignment savedAssignment = assignmentCaptor.getValue();
        assertThat(savedAssignment.tenantId()).isEqualTo(TENANT_ID);
        assertThat(savedAssignment.userId()).isEqualTo(TARGET_USER_ID);
        assertThat(savedAssignment.roleId()).isEqualTo(localRole.id());
        assertThat(savedAssignment.scopeType()).isEqualTo(RoleScopeType.AGENCY);
        assertThat(savedAssignment.scopeId()).isEqualTo(SCOPE_ID);

        RoleSyncOutboxEntry outboxEntry = outboxCaptor.getValue();
        assertThat(outboxEntry.operation()).isEqualTo(RoleSyncOperation.ASSIGN_ROLE);
        assertThat(outboxEntry.aggregateType()).isEqualTo(RoleSyncAggregateType.ASSIGNMENT);
        assertThat(outboxEntry.aggregateId()).isEqualTo(savedAssignment.id());
        assertThat(outboxEntry.tenantId()).isEqualTo(TENANT_ID);

        JsonNode payload = objectMapper.readTree(outboxEntry.payload());
        assertThat(payload.get("userId").asText()).isEqualTo(TARGET_USER_ID.toString());
        assertThat(payload.get("roleCode").asText()).isEqualTo("AGENCY_MANAGER");
        assertThat(payload.get("scopeType").asText()).isEqualTo("AGENCY");
        assertThat(payload.get("scopeId").asText()).isEqualTo(SCOPE_ID.toString());

        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    void assignRole_systemScopedRole_shouldOverrideScopeIdAndTenantIdWithSystemTenant() {
        Role localRole = canonicalRole("TNT_ADMIN", RoleScopeType.SYSTEM);
        when(roleRepository.findByTenantId(SYSTEM_TENANT_ID)).thenReturn(Flux.just(localRole));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(outboxRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // caller supplies an unrelated tenantId/scopeId — both must be overridden by systemTenantId
        StepVerifier.create(service.assignRole(TENANT_ID, TARGET_USER_ID, "TNT_ADMIN", SCOPE_ID))
                .assertNext(result -> {
                    TntRoleAssignmentResult r = result;
                    assertThat(r.scopeType()).isEqualTo("SYSTEM");
                    assertThat(r.scopeId()).isEqualTo(SYSTEM_TENANT_ID);
                })
                .verifyComplete();
    }
}
