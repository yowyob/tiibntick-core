package com.yowyob.tiibntick.core.roles.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleAssignmentPort;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleProvisioningPort;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncAggregateType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOperation;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncStatus;
import com.yowyob.tiibntick.core.roles.domain.model.TntRoleDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KernelRoleSyncWorker}: one test per operation-type branch (happy
 * path, asserting the right Kernel port method is called with the right args and the outbox
 * entry lands in the right terminal state), plus the backoff/attempt-cap logic on failure.
 *
 * @author MANFOUO Braun
 */
class KernelRoleSyncWorkerTest {

    private RoleSyncOutboxRepository outboxRepository;
    private RoleRepository roleRepository;
    private UserRoleAssignmentRepository assignmentRepository;
    private ITntRoleProvisioningPort provisioningPort;
    private ITntRoleAssignmentPort assignmentPort;
    private KernelRoleSyncWorker worker;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID AGGREGATE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        outboxRepository = mock(RoleSyncOutboxRepository.class);
        roleRepository = mock(RoleRepository.class);
        assignmentRepository = mock(UserRoleAssignmentRepository.class);
        provisioningPort = mock(ITntRoleProvisioningPort.class);
        assignmentPort = mock(ITntRoleAssignmentPort.class);
        ObjectMapper objectMapper = new ObjectMapper();

        worker = new KernelRoleSyncWorker(outboxRepository, roleRepository, assignmentRepository,
                provisioningPort, assignmentPort, objectMapper, 50);

        // save() echoes back whatever it's given, like the real adapters do.
        when(outboxRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    private RoleSyncOutboxEntry entryFor(RoleSyncOperation operation, RoleSyncAggregateType aggregateType,
                                          String payload) {
        return RoleSyncOutboxEntry.pending(operation, aggregateType, AGGREGATE_ID, TENANT_ID, payload);
    }

    // ── PROVISION_ROLE ───────────────────────────────────────────────────────

    @Test
    void provisionRole_success_callsKernelAndMarksProvisioned() {
        UUID kernelRoleId = UUID.randomUUID();
        String payload = """
                {"tenantId":"%s","code":"AGENCY_MANAGER","name":"Agency Manager",
                 "scopeType":"TENANT","permissions":["agency:read","agency:write"]}
                """.formatted(TENANT_ID);
        RoleSyncOutboxEntry entry = entryFor(RoleSyncOperation.PROVISION_ROLE, RoleSyncAggregateType.ROLE, payload);

        when(outboxRepository.fetchPendingBatch(50)).thenReturn(reactor.core.publisher.Flux.just(entry));
        when(provisioningPort.provisionRole(eq(TENANT_ID), any())).thenReturn(Mono.empty());
        when(provisioningPort.findRoleId(TENANT_ID, "AGENCY_MANAGER")).thenReturn(Mono.just(kernelRoleId));
        when(roleRepository.markKernelRoleId(TENANT_ID, AGGREGATE_ID, kernelRoleId)).thenReturn(Mono.empty());

        StepVerifier.create(worker.poll())
                .expectNext(1)
                .verifyComplete();

        ArgumentCaptor<TntRoleDefinition> defCaptor = ArgumentCaptor.forClass(TntRoleDefinition.class);
        verify(provisioningPort).provisionRole(eq(TENANT_ID), defCaptor.capture());
        assertThat(defCaptor.getValue().code()).isEqualTo("AGENCY_MANAGER");
        assertThat(defCaptor.getValue().name()).isEqualTo("Agency Manager");
        assertThat(defCaptor.getValue().defaultPermissions()).containsExactlyInAnyOrder("agency:read", "agency:write");

        verify(roleRepository).markKernelRoleId(TENANT_ID, AGGREGATE_ID, kernelRoleId);

        ArgumentCaptor<RoleSyncOutboxEntry> savedCaptor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        verify(outboxRepository, times(2)).save(savedCaptor.capture());
        RoleSyncOutboxEntry finalState = savedCaptor.getAllValues().get(1);
        assertThat(finalState.status()).isEqualTo(RoleSyncStatus.PROVISIONED);
        assertThat(finalState.kernelRefId()).isEqualTo(kernelRoleId);
    }

    @Test
    void provisionRole_kernelDoesNotReturnRoleId_isTreatedAsFailureAndRetried() {
        String payload = """
                {"tenantId":"%s","code":"AGENCY_MANAGER","name":"Agency Manager",
                 "scopeType":"TENANT","permissions":[]}
                """.formatted(TENANT_ID);
        RoleSyncOutboxEntry entry = entryFor(RoleSyncOperation.PROVISION_ROLE, RoleSyncAggregateType.ROLE, payload);

        when(outboxRepository.fetchPendingBatch(50)).thenReturn(reactor.core.publisher.Flux.just(entry));
        when(provisioningPort.provisionRole(eq(TENANT_ID), any())).thenReturn(Mono.empty());
        when(provisioningPort.findRoleId(TENANT_ID, "AGENCY_MANAGER")).thenReturn(Mono.empty());

        StepVerifier.create(worker.poll())
                .expectNext(1)
                .verifyComplete();

        verify(roleRepository, never()).markKernelRoleId(any(), any(), any());
        ArgumentCaptor<RoleSyncOutboxEntry> savedCaptor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        verify(outboxRepository, times(2)).save(savedCaptor.capture());
        RoleSyncOutboxEntry finalState = savedCaptor.getAllValues().get(1);
        assertThat(finalState.status()).isEqualTo(RoleSyncStatus.RETRYING);
    }

    // ── DELETE_ROLE ───────────────────────────────────────────────────────────

    @Test
    void deleteRole_success_callsKernelAndMarksProvisioned() {
        UUID kernelRoleId = UUID.randomUUID();
        String payload = """
                {"tenantId":"%s","kernelRoleId":"%s"}
                """.formatted(TENANT_ID, kernelRoleId);
        RoleSyncOutboxEntry entry = entryFor(RoleSyncOperation.DELETE_ROLE, RoleSyncAggregateType.ROLE, payload);

        when(outboxRepository.fetchPendingBatch(50)).thenReturn(reactor.core.publisher.Flux.just(entry));
        when(provisioningPort.deleteRole(TENANT_ID, kernelRoleId)).thenReturn(Mono.empty());

        StepVerifier.create(worker.poll())
                .expectNext(1)
                .verifyComplete();

        verify(provisioningPort).deleteRole(TENANT_ID, kernelRoleId);

        ArgumentCaptor<RoleSyncOutboxEntry> savedCaptor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        verify(outboxRepository, times(2)).save(savedCaptor.capture());
        RoleSyncOutboxEntry finalState = savedCaptor.getAllValues().get(1);
        assertThat(finalState.status()).isEqualTo(RoleSyncStatus.PROVISIONED);
        assertThat(finalState.kernelRefId()).isEqualTo(kernelRoleId);
    }

    // ── ASSIGN_ROLE ───────────────────────────────────────────────────────────

    @Test
    void assignRole_success_callsKernelAndMarksProvisioned() {
        UUID userId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        UUID kernelAssignmentId = UUID.randomUUID();
        String payload = """
                {"userId":"%s","roleCode":"AGENCY_MANAGER","scopeType":"TENANT","scopeId":"%s"}
                """.formatted(userId, scopeId);
        RoleSyncOutboxEntry entry = entryFor(RoleSyncOperation.ASSIGN_ROLE, RoleSyncAggregateType.ASSIGNMENT, payload);

        when(outboxRepository.fetchPendingBatch(50)).thenReturn(reactor.core.publisher.Flux.just(entry));
        when(assignmentPort.assignRole(userId, "AGENCY_MANAGER", "TENANT", scopeId))
                .thenReturn(Mono.just(kernelAssignmentId));
        when(assignmentRepository.markKernelAssignmentId(TENANT_ID, AGGREGATE_ID, kernelAssignmentId))
                .thenReturn(Mono.empty());

        StepVerifier.create(worker.poll())
                .expectNext(1)
                .verifyComplete();

        verify(assignmentPort).assignRole(userId, "AGENCY_MANAGER", "TENANT", scopeId);
        verify(assignmentRepository).markKernelAssignmentId(TENANT_ID, AGGREGATE_ID, kernelAssignmentId);

        ArgumentCaptor<RoleSyncOutboxEntry> savedCaptor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        verify(outboxRepository, times(2)).save(savedCaptor.capture());
        RoleSyncOutboxEntry finalState = savedCaptor.getAllValues().get(1);
        assertThat(finalState.status()).isEqualTo(RoleSyncStatus.PROVISIONED);
        assertThat(finalState.kernelRefId()).isEqualTo(kernelAssignmentId);
    }

    // ── REVOKE_ASSIGNMENT ─────────────────────────────────────────────────────

    @Test
    void revokeAssignment_success_callsKernelAndMarksProvisioned() {
        UUID kernelAssignmentId = UUID.randomUUID();
        String payload = """
                {"kernelAssignmentId":"%s"}
                """.formatted(kernelAssignmentId);
        RoleSyncOutboxEntry entry = entryFor(RoleSyncOperation.REVOKE_ASSIGNMENT, RoleSyncAggregateType.ASSIGNMENT, payload);

        when(outboxRepository.fetchPendingBatch(50)).thenReturn(reactor.core.publisher.Flux.just(entry));
        when(assignmentPort.revokeAssignment(kernelAssignmentId)).thenReturn(Mono.empty());

        StepVerifier.create(worker.poll())
                .expectNext(1)
                .verifyComplete();

        verify(assignmentPort).revokeAssignment(kernelAssignmentId);

        ArgumentCaptor<RoleSyncOutboxEntry> savedCaptor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        verify(outboxRepository, times(2)).save(savedCaptor.capture());
        RoleSyncOutboxEntry finalState = savedCaptor.getAllValues().get(1);
        assertThat(finalState.status()).isEqualTo(RoleSyncStatus.PROVISIONED);
        assertThat(finalState.kernelRefId()).isEqualTo(kernelAssignmentId);
    }

    // ── Backoff / attempt-cap logic ──────────────────────────────────────────

    @Test
    void failure_belowAttemptCap_isMarkedRetryingWithExponentialBackoff() {
        UUID kernelAssignmentId = UUID.randomUUID();
        String payload = """
                {"kernelAssignmentId":"%s"}
                """.formatted(kernelAssignmentId);
        RoleSyncOutboxEntry entry = entryFor(RoleSyncOperation.REVOKE_ASSIGNMENT, RoleSyncAggregateType.ASSIGNMENT, payload);

        when(outboxRepository.fetchPendingBatch(50)).thenReturn(reactor.core.publisher.Flux.just(entry));
        when(assignmentPort.revokeAssignment(kernelAssignmentId))
                .thenReturn(Mono.error(new RuntimeException("Kernel unavailable")));

        LocalDateTime before = LocalDateTime.now();
        StepVerifier.create(worker.poll())
                .expectNext(1)
                .verifyComplete();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<RoleSyncOutboxEntry> savedCaptor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        verify(outboxRepository, times(2)).save(savedCaptor.capture());
        RoleSyncOutboxEntry finalState = savedCaptor.getAllValues().get(1);

        assertThat(finalState.status()).isEqualTo(RoleSyncStatus.RETRYING);
        assertThat(finalState.attemptCount()).isEqualTo(1); // asProcessing() incremented 0 -> 1
        assertThat(finalState.lastError()).isEqualTo("Kernel unavailable");
        // 2^1 = 2 seconds backoff
        assertThat(finalState.nextAttemptAt()).isBetween(before.plusSeconds(2), after.plusSeconds(2));
    }

    @Test
    void failure_backoffIsCappedAtFiveMinutes() {
        UUID kernelAssignmentId = UUID.randomUUID();
        String payload = """
                {"kernelAssignmentId":"%s"}
                """.formatted(kernelAssignmentId);
        // attemptCount = 8 going in; asProcessing() bumps it to 9 -> 2^9 = 512s > 300s cap
        RoleSyncOutboxEntry entry = buildEntryWithAttemptCount(
                RoleSyncOperation.REVOKE_ASSIGNMENT, RoleSyncAggregateType.ASSIGNMENT, payload, 8);

        when(outboxRepository.fetchPendingBatch(50)).thenReturn(reactor.core.publisher.Flux.just(entry));
        when(assignmentPort.revokeAssignment(kernelAssignmentId))
                .thenReturn(Mono.error(new RuntimeException("still down")));

        LocalDateTime before = LocalDateTime.now();
        StepVerifier.create(worker.poll())
                .expectNext(1)
                .verifyComplete();

        ArgumentCaptor<RoleSyncOutboxEntry> savedCaptor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        verify(outboxRepository, times(2)).save(savedCaptor.capture());
        RoleSyncOutboxEntry finalState = savedCaptor.getAllValues().get(1);

        assertThat(finalState.status()).isEqualTo(RoleSyncStatus.RETRYING);
        assertThat(finalState.attemptCount()).isEqualTo(9);
        // capped at 300s, not 2^9=512s
        assertThat(finalState.nextAttemptAt()).isBeforeOrEqualTo(before.plusSeconds(301));
    }

    @Test
    void failure_atAttemptCap_isMarkedDeadInsteadOfRetrying() {
        UUID kernelAssignmentId = UUID.randomUUID();
        String payload = """
                {"kernelAssignmentId":"%s"}
                """.formatted(kernelAssignmentId);
        // attemptCount = 9 going in; asProcessing() bumps it to 10 == MAX_ATTEMPTS
        RoleSyncOutboxEntry entry = buildEntryWithAttemptCount(
                RoleSyncOperation.REVOKE_ASSIGNMENT, RoleSyncAggregateType.ASSIGNMENT, payload, 9);

        when(outboxRepository.fetchPendingBatch(50)).thenReturn(reactor.core.publisher.Flux.just(entry));
        when(assignmentPort.revokeAssignment(kernelAssignmentId))
                .thenReturn(Mono.error(new RuntimeException("permanently broken")));

        StepVerifier.create(worker.poll())
                .expectNext(1)
                .verifyComplete();

        ArgumentCaptor<RoleSyncOutboxEntry> savedCaptor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        verify(outboxRepository, times(2)).save(savedCaptor.capture());
        RoleSyncOutboxEntry finalState = savedCaptor.getAllValues().get(1);

        assertThat(finalState.status()).isEqualTo(RoleSyncStatus.DEAD);
        assertThat(finalState.attemptCount()).isEqualTo(10);
        assertThat(finalState.lastError()).isEqualTo("permanently broken");
        assertThat(finalState.processedAt()).isNotNull();
    }

    @Test
    void malformedPayload_isHandledAsFailureRatherThanThrowing() {
        RoleSyncOutboxEntry entry = entryFor(
                RoleSyncOperation.REVOKE_ASSIGNMENT, RoleSyncAggregateType.ASSIGNMENT, "not-json");

        when(outboxRepository.fetchPendingBatch(50)).thenReturn(reactor.core.publisher.Flux.just(entry));

        StepVerifier.create(worker.poll())
                .expectNext(1)
                .verifyComplete();

        ArgumentCaptor<RoleSyncOutboxEntry> savedCaptor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        verify(outboxRepository, times(2)).save(savedCaptor.capture());
        RoleSyncOutboxEntry finalState = savedCaptor.getAllValues().get(1);
        assertThat(finalState.status()).isEqualTo(RoleSyncStatus.RETRYING);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Builds a pending entry, then fast-forwards it through {@code attemptCount} failed cycles. */
    private RoleSyncOutboxEntry buildEntryWithAttemptCount(RoleSyncOperation operation,
                                                            RoleSyncAggregateType aggregateType,
                                                            String payload,
                                                            int attemptCount) {
        RoleSyncOutboxEntry entry = RoleSyncOutboxEntry.pending(operation, aggregateType, AGGREGATE_ID, TENANT_ID, payload);
        for (int i = 0; i < attemptCount; i++) {
            entry = entry.asProcessing().asRetrying("prior failure", LocalDateTime.now());
        }
        return entry;
    }
}
