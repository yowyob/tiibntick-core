package com.yowyob.tiibntick.core.roles.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * A claim that never completes must come back to the queue.
 *
 * <p>The poller marks a row PROCESSING before dispatching it, and
 * {@code fetchPendingBatch} only ever returns PENDING and RETRYING rows. So a process that
 * stops mid-attempt — restart, OOM kill, lost instance — leaves the row in a state nothing
 * ever looks at again: never retried, never declared dead, never counted as late. 274 such
 * rows were counted on 27 November 2026, the oldest predating the Kernel outage.
 */
class LostClaimRecoveryTest {

    private final RoleSyncOutboxRepository outboxRepository = mock(RoleSyncOutboxRepository.class);

    /** The claim instant must be recorded, otherwise an old claim cannot be told from a fresh one. */
    @Test
    void claimingAnEntryStampsWhenItWasClaimed() {
        LocalDateTime before = LocalDateTime.now();
        RoleSyncOutboxEntry pending = RoleSyncOutboxEntry.pending(RoleSyncOperation.PROVISION_ROLE,
                RoleSyncAggregateType.ROLE, UUID.randomUUID(), UUID.randomUUID(), "{}");

        RoleSyncOutboxEntry claimed = pending.asProcessing();

        assertThat(claimed.status()).isEqualTo(RoleSyncStatus.PROCESSING);
        assertThat(claimed.attemptCount()).isEqualTo(pending.attemptCount() + 1);
        assertThat(claimed.nextAttemptAt()).isNotNull().isAfterOrEqualTo(before);
    }

    @Test
    void theSweepRequeuesLostClaimsWithTheConfiguredTimeout() {
        when(outboxRepository.requeueStaleClaims(any())).thenReturn(Mono.just(274L));

        Long requeued = worker(Duration.ofMinutes(15)).recoverStaleClaims().block();

        assertThat(requeued).isEqualTo(274L);
        verify(outboxRepository).requeueStaleClaims(Duration.ofMinutes(15));
    }

    /** The sweep must not touch the Kernel: a lost claim is lost whatever the Kernel is doing. */
    @Test
    void theSweepNeverCallsTheKernel() {
        ITntRoleProvisioningPort provisioningPort = mock(ITntRoleProvisioningPort.class);
        ITntRoleAssignmentPort assignmentPort = mock(ITntRoleAssignmentPort.class);
        when(outboxRepository.requeueStaleClaims(any())).thenReturn(Mono.just(3L));

        new KernelRoleSyncWorker(outboxRepository, mock(RoleRepository.class),
                mock(UserRoleAssignmentRepository.class), provisioningPort, assignmentPort,
                new ObjectMapper(), 50, Duration.ofMinutes(15))
                .recoverStaleClaims().block();

        verify(provisioningPort, never()).provisionRole(any(), any());
        verify(assignmentPort, never()).assignRoleAsSolution(any(), any(), any(), any());
        verify(outboxRepository, never()).save(any());
    }

    /** Nothing to requeue must stay silent — no save, no Kernel call, no error. */
    @Test
    void anEmptySweepChangesNothing() {
        when(outboxRepository.requeueStaleClaims(any())).thenReturn(Mono.just(0L));

        assertThat(worker(Duration.ofMinutes(15)).recoverStaleClaims().block()).isZero();
        verify(outboxRepository, never()).save(any());
    }

    private KernelRoleSyncWorker worker(Duration staleClaimTimeout) {
        return new KernelRoleSyncWorker(outboxRepository, mock(RoleRepository.class),
                mock(UserRoleAssignmentRepository.class), mock(ITntRoleProvisioningPort.class),
                mock(ITntRoleAssignmentPort.class), new ObjectMapper(), 50, staleClaimTimeout);
    }
}
