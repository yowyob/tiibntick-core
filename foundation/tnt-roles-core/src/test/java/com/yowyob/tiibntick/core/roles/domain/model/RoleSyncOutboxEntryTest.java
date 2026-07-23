package com.yowyob.tiibntick.core.roles.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoleSyncOutboxEntryTest {

    private static final UUID AGGREGATE_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    private RoleSyncOutboxEntry newPendingEntry() {
        return RoleSyncOutboxEntry.pending(
                RoleSyncOperation.PROVISION_ROLE,
                RoleSyncAggregateType.ROLE,
                AGGREGATE_ID,
                TENANT_ID,
                "{\"code\":\"AGENCY_MANAGER\"}");
    }

    @Test
    void pending_shouldCreateEntryInPendingStatusWithZeroAttempts() {
        RoleSyncOutboxEntry entry = newPendingEntry();

        assertThat(entry.id()).isNotNull();
        assertThat(entry.operation()).isEqualTo(RoleSyncOperation.PROVISION_ROLE);
        assertThat(entry.aggregateType()).isEqualTo(RoleSyncAggregateType.ROLE);
        assertThat(entry.aggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(entry.tenantId()).isEqualTo(TENANT_ID);
        assertThat(entry.payload()).isEqualTo("{\"code\":\"AGENCY_MANAGER\"}");
        assertThat(entry.status()).isEqualTo(RoleSyncStatus.PENDING);
        assertThat(entry.attemptCount()).isZero();
        assertThat(entry.lastError()).isNull();
        assertThat(entry.kernelRefId()).isNull();
        assertThat(entry.createdAt()).isNotNull();
        assertThat(entry.nextAttemptAt()).isEqualTo(entry.createdAt());
        assertThat(entry.processedAt()).isNull();
    }

    @Test
    void pending_distinctCalls_shouldProduceDistinctIds() {
        RoleSyncOutboxEntry first = newPendingEntry();
        RoleSyncOutboxEntry second = newPendingEntry();

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void asProcessing_shouldTransitionToProcessingAndIncrementAttemptCount() {
        RoleSyncOutboxEntry entry = newPendingEntry();

        RoleSyncOutboxEntry processing = entry.asProcessing();

        assertThat(processing.status()).isEqualTo(RoleSyncStatus.PROCESSING);
        assertThat(processing.attemptCount()).isEqualTo(1);
        // identity/immutable fields unchanged
        assertThat(processing.id()).isEqualTo(entry.id());
        assertThat(processing.operation()).isEqualTo(entry.operation());
        assertThat(processing.aggregateType()).isEqualTo(entry.aggregateType());
        assertThat(processing.aggregateId()).isEqualTo(entry.aggregateId());
        assertThat(processing.tenantId()).isEqualTo(entry.tenantId());
        assertThat(processing.payload()).isEqualTo(entry.payload());
        assertThat(processing.createdAt()).isEqualTo(entry.createdAt());
    }

    @Test
    void asProcessing_calledRepeatedly_shouldKeepIncrementingAttemptCount() {
        RoleSyncOutboxEntry entry = newPendingEntry();

        RoleSyncOutboxEntry afterThreeAttempts = entry.asProcessing().asRetrying("boom", LocalDateTime.now())
                .asProcessing().asRetrying("boom again", LocalDateTime.now())
                .asProcessing();

        assertThat(afterThreeAttempts.attemptCount()).isEqualTo(3);
        assertThat(afterThreeAttempts.status()).isEqualTo(RoleSyncStatus.PROCESSING);
    }

    @Test
    void asProvisioned_shouldTransitionToProvisionedAndRecordKernelRefIdAndProcessedAt() {
        RoleSyncOutboxEntry entry = newPendingEntry().asProcessing();
        UUID kernelRefId = UUID.randomUUID();

        RoleSyncOutboxEntry provisioned = entry.asProvisioned(kernelRefId);

        assertThat(provisioned.status()).isEqualTo(RoleSyncStatus.PROVISIONED);
        assertThat(provisioned.kernelRefId()).isEqualTo(kernelRefId);
        assertThat(provisioned.processedAt()).isNotNull();
        assertThat(provisioned.lastError()).isNull();
        assertThat(provisioned.attemptCount()).isEqualTo(entry.attemptCount());
    }

    @Test
    void asProvisioned_shouldClearAnyPriorError() {
        RoleSyncOutboxEntry entry = newPendingEntry().asProcessing()
                .asRetrying("transient network error", LocalDateTime.now());
        assertThat(entry.lastError()).isNotNull();

        RoleSyncOutboxEntry provisioned = entry.asProcessing().asProvisioned(UUID.randomUUID());

        assertThat(provisioned.lastError()).isNull();
    }

    @Test
    void asRetrying_shouldTransitionToRetryingAndRecordErrorAndNextAttemptAt() {
        RoleSyncOutboxEntry entry = newPendingEntry().asProcessing();
        LocalDateTime nextAttempt = LocalDateTime.now().plusMinutes(5);

        RoleSyncOutboxEntry retrying = entry.asRetrying("Kernel returned 503", nextAttempt);

        assertThat(retrying.status()).isEqualTo(RoleSyncStatus.RETRYING);
        assertThat(retrying.lastError()).isEqualTo("Kernel returned 503");
        assertThat(retrying.nextAttemptAt()).isEqualTo(nextAttempt);
        assertThat(retrying.processedAt()).isNull();
        assertThat(retrying.attemptCount()).isEqualTo(entry.attemptCount());
    }

    @Test
    void asRetrying_thenAsProcessing_shouldAllowReEntryIntoProcessing() {
        RoleSyncOutboxEntry entry = newPendingEntry().asProcessing()
                .asRetrying("timeout", LocalDateTime.now().plusSeconds(30));

        RoleSyncOutboxEntry reprocessing = entry.asProcessing();

        assertThat(reprocessing.status()).isEqualTo(RoleSyncStatus.PROCESSING);
        assertThat(reprocessing.attemptCount()).isEqualTo(2);
    }

    @Test
    void asDead_shouldTransitionToDeadAndRecordErrorAndProcessedAt() {
        RoleSyncOutboxEntry entry = newPendingEntry().asProcessing();

        RoleSyncOutboxEntry dead = entry.asDead("retries exhausted after 5 attempts");

        assertThat(dead.status()).isEqualTo(RoleSyncStatus.DEAD);
        assertThat(dead.lastError()).isEqualTo("retries exhausted after 5 attempts");
        assertThat(dead.processedAt()).isNotNull();
        assertThat(dead.attemptCount()).isEqualTo(entry.attemptCount());
    }

    @Test
    void fullLifecycle_pendingToProcessingToRetryingToProcessingToProvisioned() {
        RoleSyncOutboxEntry entry = newPendingEntry();
        assertThat(entry.status()).isEqualTo(RoleSyncStatus.PENDING);

        entry = entry.asProcessing();
        assertThat(entry.status()).isEqualTo(RoleSyncStatus.PROCESSING);
        assertThat(entry.attemptCount()).isEqualTo(1);

        entry = entry.asRetrying("Kernel unreachable", LocalDateTime.now().plusSeconds(10));
        assertThat(entry.status()).isEqualTo(RoleSyncStatus.RETRYING);

        entry = entry.asProcessing();
        assertThat(entry.status()).isEqualTo(RoleSyncStatus.PROCESSING);
        assertThat(entry.attemptCount()).isEqualTo(2);

        UUID kernelRefId = UUID.randomUUID();
        entry = entry.asProvisioned(kernelRefId);
        assertThat(entry.status()).isEqualTo(RoleSyncStatus.PROVISIONED);
        assertThat(entry.kernelRefId()).isEqualTo(kernelRefId);
        assertThat(entry.lastError()).isNull();
    }

    @Test
    void fullLifecycle_pendingToProcessingToDead() {
        RoleSyncOutboxEntry entry = newPendingEntry()
                .asProcessing()
                .asDead("retries exhausted");

        assertThat(entry.status()).isEqualTo(RoleSyncStatus.DEAD);
        assertThat(entry.attemptCount()).isEqualTo(1);
        assertThat(entry.lastError()).isEqualTo("retries exhausted");
        assertThat(entry.processedAt()).isNotNull();
    }

    @Test
    void transitions_shouldNotMutateOriginalInstance_recordsAreImmutable() {
        RoleSyncOutboxEntry original = newPendingEntry();

        original.asProcessing();

        assertThat(original.status()).isEqualTo(RoleSyncStatus.PENDING);
        assertThat(original.attemptCount()).isZero();
    }
}
