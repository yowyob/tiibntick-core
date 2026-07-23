package com.yowyob.tiibntick.core.roles.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleAssignmentPort;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleProvisioningPort;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.domain.model.Role;
import com.yowyob.tiibntick.core.roles.domain.model.RoleScopeType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncAggregateType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOperation;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncStatus;
import com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
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

/**
 * Unit tests for {@link KernelRoleReconciliationJob}: verifies the job only ever checks
 * outbox-known ids (never a broad Kernel listing), skips {@code DELETE_ROLE}/
 * {@code REVOKE_ASSIGNMENT} entries, and re-enqueues a fresh {@code PENDING} entry only when
 * the Kernel-side counterpart is confirmed missing.
 *
 * @author MANFOUO Braun
 */
class KernelRoleReconciliationJobTest {

    private RoleSyncOutboxRepository outboxRepository;
    private RoleRepository roleRepository;
    private UserRoleAssignmentRepository assignmentRepository;
    private ITntRoleProvisioningPort provisioningPort;
    private ITntRoleAssignmentPort assignmentPort;
    private KernelRoleReconciliationJob job;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        outboxRepository = mock(RoleSyncOutboxRepository.class);
        roleRepository = mock(RoleRepository.class);
        assignmentRepository = mock(UserRoleAssignmentRepository.class);
        provisioningPort = mock(ITntRoleProvisioningPort.class);
        assignmentPort = mock(ITntRoleAssignmentPort.class);
        ObjectMapper objectMapper = new ObjectMapper();

        job = new KernelRoleReconciliationJob(outboxRepository, roleRepository, assignmentRepository,
                provisioningPort, assignmentPort, objectMapper);

        when(outboxRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void roleStillExistsInKernel_doesNotReenqueue() {
        UUID roleId = UUID.randomUUID();
        UUID kernelRoleId = UUID.randomUUID();
        RoleSyncOutboxEntry provisioned = RoleSyncOutboxEntry.pending(
                        RoleSyncOperation.PROVISION_ROLE, RoleSyncAggregateType.ROLE, roleId, TENANT_ID, "{}")
                .asProcessing()
                .asProvisioned(kernelRoleId);

        when(outboxRepository.findByStatus(RoleSyncStatus.PROVISIONED)).thenReturn(Flux.just(provisioned));
        when(provisioningPort.roleExistsById(TENANT_ID, kernelRoleId)).thenReturn(Mono.just(true));

        StepVerifier.create(job.reconcile())
                .expectNext(0)
                .verifyComplete();

        verify(roleRepository, never()).findById(any(), any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void roleMissingInKernel_reenqueuesFreshProvisionRoleEntry() {
        UUID roleId = UUID.randomUUID();
        UUID kernelRoleId = UUID.randomUUID();
        RoleSyncOutboxEntry provisioned = RoleSyncOutboxEntry.pending(
                        RoleSyncOperation.PROVISION_ROLE, RoleSyncAggregateType.ROLE, roleId, TENANT_ID, "{}")
                .asProcessing()
                .asProvisioned(kernelRoleId);
        Role role = new Role(roleId, TENANT_ID, "AGENCY_MANAGER", "Agency Manager",
                RoleScopeType.TENANT, Set.of("agency:read"));

        when(outboxRepository.findByStatus(RoleSyncStatus.PROVISIONED)).thenReturn(Flux.just(provisioned));
        when(provisioningPort.roleExistsById(TENANT_ID, kernelRoleId)).thenReturn(Mono.just(false));
        when(roleRepository.findById(TENANT_ID, roleId)).thenReturn(Mono.just(role));

        StepVerifier.create(job.reconcile())
                .expectNext(1)
                .verifyComplete();

        ArgumentCaptor<RoleSyncOutboxEntry> captor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        verify(outboxRepository).save(captor.capture());
        RoleSyncOutboxEntry fresh = captor.getValue();
        assertThat(fresh.status()).isEqualTo(RoleSyncStatus.PENDING);
        assertThat(fresh.operation()).isEqualTo(RoleSyncOperation.PROVISION_ROLE);
        assertThat(fresh.aggregateType()).isEqualTo(RoleSyncAggregateType.ROLE);
        assertThat(fresh.aggregateId()).isEqualTo(roleId);
        assertThat(fresh.tenantId()).isEqualTo(TENANT_ID);
        assertThat(fresh.payload()).contains("AGENCY_MANAGER").contains("agency:read").contains("TENANT");
    }

    @Test
    void roleMissingLocallyToo_doesNotReenqueue() {
        UUID roleId = UUID.randomUUID();
        UUID kernelRoleId = UUID.randomUUID();
        RoleSyncOutboxEntry provisioned = RoleSyncOutboxEntry.pending(
                        RoleSyncOperation.PROVISION_ROLE, RoleSyncAggregateType.ROLE, roleId, TENANT_ID, "{}")
                .asProcessing()
                .asProvisioned(kernelRoleId);

        when(outboxRepository.findByStatus(RoleSyncStatus.PROVISIONED)).thenReturn(Flux.just(provisioned));
        when(provisioningPort.roleExistsById(TENANT_ID, kernelRoleId)).thenReturn(Mono.just(false));
        when(roleRepository.findById(TENANT_ID, roleId)).thenReturn(Mono.empty());

        StepVerifier.create(job.reconcile())
                .expectNext(0)
                .verifyComplete();

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void assignmentStillExistsInKernel_doesNotReenqueue() {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID kernelAssignmentId = UUID.randomUUID();
        RoleSyncOutboxEntry provisioned = RoleSyncOutboxEntry.pending(
                        RoleSyncOperation.ASSIGN_ROLE, RoleSyncAggregateType.ASSIGNMENT, assignmentId, TENANT_ID, "{}")
                .asProcessing()
                .asProvisioned(kernelAssignmentId);
        UserRoleAssignment assignment = new UserRoleAssignment(
                assignmentId, TENANT_ID, userId, roleId, RoleScopeType.TENANT, TENANT_ID);

        when(outboxRepository.findByStatus(RoleSyncStatus.PROVISIONED)).thenReturn(Flux.just(provisioned));
        when(assignmentRepository.findById(TENANT_ID, assignmentId)).thenReturn(Mono.just(assignment));
        when(assignmentPort.assignmentExists(userId, kernelAssignmentId)).thenReturn(Mono.just(true));

        StepVerifier.create(job.reconcile())
                .expectNext(0)
                .verifyComplete();

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void assignmentMissingInKernel_reenqueuesFreshAssignRoleEntry() {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        UUID kernelAssignmentId = UUID.randomUUID();
        RoleSyncOutboxEntry provisioned = RoleSyncOutboxEntry.pending(
                        RoleSyncOperation.ASSIGN_ROLE, RoleSyncAggregateType.ASSIGNMENT, assignmentId, TENANT_ID, "{}")
                .asProcessing()
                .asProvisioned(kernelAssignmentId);
        UserRoleAssignment assignment = new UserRoleAssignment(
                assignmentId, TENANT_ID, userId, roleId, RoleScopeType.TENANT, scopeId);
        Role role = new Role(roleId, TENANT_ID, "AGENCY_MANAGER", "Agency Manager",
                RoleScopeType.TENANT, Set.of("agency:read"));

        when(outboxRepository.findByStatus(RoleSyncStatus.PROVISIONED)).thenReturn(Flux.just(provisioned));
        when(assignmentRepository.findById(TENANT_ID, assignmentId)).thenReturn(Mono.just(assignment));
        when(assignmentPort.assignmentExists(userId, kernelAssignmentId)).thenReturn(Mono.just(false));
        when(roleRepository.findById(TENANT_ID, roleId)).thenReturn(Mono.just(role));

        StepVerifier.create(job.reconcile())
                .expectNext(1)
                .verifyComplete();

        ArgumentCaptor<RoleSyncOutboxEntry> captor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        verify(outboxRepository).save(captor.capture());
        RoleSyncOutboxEntry fresh = captor.getValue();
        assertThat(fresh.status()).isEqualTo(RoleSyncStatus.PENDING);
        assertThat(fresh.operation()).isEqualTo(RoleSyncOperation.ASSIGN_ROLE);
        assertThat(fresh.aggregateType()).isEqualTo(RoleSyncAggregateType.ASSIGNMENT);
        assertThat(fresh.aggregateId()).isEqualTo(assignmentId);
        assertThat(fresh.payload()).contains(userId.toString()).contains("AGENCY_MANAGER").contains(scopeId.toString());
    }

    @Test
    void deleteRoleAndRevokeAssignmentEntries_areNeverReconciled() {
        RoleSyncOutboxEntry deleteEntry = RoleSyncOutboxEntry.pending(
                        RoleSyncOperation.DELETE_ROLE, RoleSyncAggregateType.ROLE,
                        UUID.randomUUID(), TENANT_ID, "{}")
                .asProcessing()
                .asProvisioned(UUID.randomUUID());
        RoleSyncOutboxEntry revokeEntry = RoleSyncOutboxEntry.pending(
                        RoleSyncOperation.REVOKE_ASSIGNMENT, RoleSyncAggregateType.ASSIGNMENT,
                        UUID.randomUUID(), TENANT_ID, "{}")
                .asProcessing()
                .asProvisioned(UUID.randomUUID());

        when(outboxRepository.findByStatus(RoleSyncStatus.PROVISIONED))
                .thenReturn(Flux.just(deleteEntry, revokeEntry));

        StepVerifier.create(job.reconcile())
                .expectNext(0)
                .verifyComplete();

        verify(provisioningPort, never()).roleExistsById(any(), any());
        verify(assignmentPort, never()).assignmentExists(any(), any());
    }
}
