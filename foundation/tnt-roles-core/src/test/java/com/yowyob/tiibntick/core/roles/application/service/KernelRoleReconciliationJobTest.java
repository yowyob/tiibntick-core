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
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private static final int BUCKETS = 24;
    private static final int MAX_ROWS = 200;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(RoleSyncOutboxRepository.class);
        roleRepository = mock(RoleRepository.class);
        assignmentRepository = mock(UserRoleAssignmentRepository.class);
        provisioningPort = mock(ITntRoleProvisioningPort.class);
        assignmentPort = mock(ITntRoleAssignmentPort.class);
        ObjectMapper objectMapper = new ObjectMapper();

        job = new KernelRoleReconciliationJob(outboxRepository, roleRepository, assignmentRepository,
                provisioningPort, assignmentPort, objectMapper, BUCKETS, MAX_ROWS);

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

        when(outboxRepository.fetchReconciliationBatch(anyInt(), anyInt(), anyInt())).thenReturn(Flux.just(provisioned));
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
                RoleScopeType.TENANT, Set.of("agency:read"), false);

        when(outboxRepository.fetchReconciliationBatch(anyInt(), anyInt(), anyInt())).thenReturn(Flux.just(provisioned));
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

        when(outboxRepository.fetchReconciliationBatch(anyInt(), anyInt(), anyInt())).thenReturn(Flux.just(provisioned));
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

        when(outboxRepository.fetchReconciliationBatch(anyInt(), anyInt(), anyInt())).thenReturn(Flux.just(provisioned));
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
                RoleScopeType.TENANT, Set.of("agency:read"), false);

        when(outboxRepository.fetchReconciliationBatch(anyInt(), anyInt(), anyInt())).thenReturn(Flux.just(provisioned));
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

        when(outboxRepository.fetchReconciliationBatch(anyInt(), anyInt(), anyInt()))
                .thenReturn(Flux.just(deleteEntry, revokeEntry));

        StepVerifier.create(job.reconcile())
                .expectNext(0)
                .verifyComplete();

        verify(provisioningPort, never()).roleExistsById(any(), any());
        verify(assignmentPort, never()).assignmentExists(any(), any());
    }

    // ── Bornes : le defaut qui a tue 401 733 entrees ──────────────────────────

    @Test
    void oneSliceIsRead_neverTheWholeProvisionedSet() {
        when(outboxRepository.fetchReconciliationBatch(anyInt(), anyInt(), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(job.reconcile())
                .expectNext(0)
                .verifyComplete();

        verify(outboxRepository).fetchReconciliationBatch(BUCKETS, job.currentBucketIndex(), MAX_ROWS);
        verify(outboxRepository, never()).findByStatus(any());
    }

    @Test
    void kernelCallsRunOneAtATime_notTwoHundredAndFiftySixAtOnce() {
        // The regression that mattered most: flatMap's default concurrency is 256, so this job
        // fired up to 256 simultaneous Kernel calls. Against kernelServiceAccountWebClient's
        // 20-call sliding window that opens the breaker on the very first cycle, which then
        // starved KernelRoleSyncWorker and is how the DEAD rows accumulated.
        when(outboxRepository.fetchReconciliationBatch(anyInt(), anyInt(), anyInt()))
                .thenReturn(Flux.fromIterable(provisionedRoleRows(20)));

        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        when(provisioningPort.roleExistsById(any(), any())).thenAnswer(invocation ->
                Mono.fromCallable(() -> peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max))
                        // A real call takes time; without it every Mono would complete before
                        // the next subscription and overlap would be unobservable either way.
                        .delayElement(Duration.ofMillis(5))
                        // The release has to happen HERE, on the value, not in doFinally:
                        // doFinally runs after the terminal signal has already reached
                        // concatMap, which by then has subscribed to the next inner publisher.
                        // Measured from doFinally this reads a peak of 2 even when the calls
                        // are strictly sequential — an artefact of the probe, not concurrency.
                        .map(ignored -> {
                            inFlight.decrementAndGet();
                            return true;
                        }));

        StepVerifier.create(job.reconcile())
                .expectNext(0)
                .verifyComplete();

        assertThat(peak.get()).isEqualTo(1);
    }

    @Test
    void circuitRejection_abandonsTheRestOfTheCycle() {
        when(outboxRepository.fetchReconciliationBatch(anyInt(), anyInt(), anyInt()))
                .thenReturn(Flux.fromIterable(provisionedRoleRows(5)));
        when(provisioningPort.roleExistsById(any(), any())).thenReturn(Mono.error(circuitRejection()));

        StepVerifier.create(job.reconcile())
                .expectNext(0)
                .verifyComplete();

        // One rejection is enough: racing through the other four would hold the breaker open
        // and starve the outbox worker, which shares the same CircuitBreaker instance.
        verify(provisioningPort, times(1)).roleExistsById(any(), any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void aGenuineFailureDoesNotAbandonTheCycle() {
        // Over-correction guard. Only a breaker rejection stops the cycle; an ordinary Kernel
        // error concerns one row and the remaining rows must still be spot-checked.
        when(outboxRepository.fetchReconciliationBatch(anyInt(), anyInt(), anyInt()))
                .thenReturn(Flux.fromIterable(provisionedRoleRows(5)));
        when(provisioningPort.roleExistsById(any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("503 from the Kernel")));

        StepVerifier.create(job.reconcile())
                .expectNext(0)
                .verifyComplete();

        verify(provisioningPort, times(5)).roleExistsById(any(), any());
    }

    @Test
    void invalidBounds_areRejectedAtConstruction() {
        assertThatThrownBy(() -> newJobWith(0, MAX_ROWS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucketCount");
        assertThatThrownBy(() -> newJobWith(BUCKETS, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRowsPerCycle");
    }

    @Test
    void bucketIndexIsDerivedFromElapsedHours_notTheHourOfDay() {
        // hour-of-day % bucketCount only ever yields 0..23, so with the 168 buckets the
        // defaults use, 144 of the slices would have been unreachable and the rows in them
        // never checked. Pin the derivation so nobody simplifies it back.
        int buckets = 168;
        int expected = (int) Math.floorMod(Instant.now().getEpochSecond() / 3600L, (long) buckets);

        assertThat(newJobWith(buckets, MAX_ROWS).currentBucketIndex()).isEqualTo(expected);
    }

    @Test
    void bucketIndexStaysInsideTheBucketCount() {
        assertThat(job.currentBucketIndex()).isBetween(0, BUCKETS - 1);
        assertThat(newJobWith(7, MAX_ROWS).currentBucketIndex()).isBetween(0, 6);
        assertThat(newJobWith(1, MAX_ROWS).currentBucketIndex()).isZero();
    }

    // ── Outillage ─────────────────────────────────────────────────────────────

    private KernelRoleReconciliationJob newJobWith(int buckets, int maxRows) {
        return new KernelRoleReconciliationJob(outboxRepository, roleRepository, assignmentRepository,
                provisioningPort, assignmentPort, new ObjectMapper(), buckets, maxRows);
    }

    private List<RoleSyncOutboxEntry> provisionedRoleRows(int count) {
        List<RoleSyncOutboxEntry> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(RoleSyncOutboxEntry.pending(RoleSyncOperation.PROVISION_ROLE,
                            RoleSyncAggregateType.ROLE, UUID.randomUUID(), TENANT_ID, "{}")
                    .asProcessing()
                    .asProvisioned(UUID.randomUUID()));
        }
        return rows;
    }

    /**
     * A real CallNotPermittedException from a real open breaker — the type the WebClient filter
     * chain actually throws. A look-alike test class would make these tests pass while
     * production stayed broken.
     */
    private static CallNotPermittedException circuitRejection() {
        CircuitBreaker breaker = CircuitBreaker.ofDefaults("kernelServiceAccountWebClient");
        breaker.transitionToOpenState();
        return CallNotPermittedException.createCallNotPermittedException(breaker);
    }
}
