package com.yowyob.kernel.event.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.yowyob.kernel.event.domain.enums.DLQStatus;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.kernel.event.domain.enums.SchemaCompatibility;
import com.yowyob.kernel.event.domain.model.DeadLetterEntry;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.kernel.event.domain.model.EventSchema;
import com.yowyob.kernel.event.domain.model.OutboxEntry;
import com.yowyob.kernel.event.domain.vo.DeadLetterEntryId;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;
import com.yowyob.kernel.event.domain.vo.OutboxEntryId;
import com.yowyob.kernel.event.domain.vo.RetryPolicy;
import com.yowyob.kernel.event.domain.vo.SchemaId;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Additional domain unit tests covering:
 * <ul>
 *   <li>{@link DeadLetterEntry} lifecycle and restore()</li>
 *   <li>{@link DomainEventEnvelope#restore} factory</li>
 *   <li>{@link OutboxEntry#restore} factory</li>
 *   <li>{@link EventSchema} deprecation</li>
 *   <li>{@link yowyob.kernel.event.domain.vo.EventBusStats} computations</li>
 * </ul>
 */
class YowEventKernelDomainExtendedTest {

    // ── DeadLetterEntry ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("DeadLetterEntry")
    class DeadLetterEntryTest {

        private DomainEventEnvelope buildDeadEnvelope() {
            DomainEventEnvelope env = DomainEventEnvelope.wrap()
                .correlationId("corr-dlq-001")
                .eventType("DLQTestEvent")
                .aggregateId("agg-dlq-001")
                .aggregateType("DLQAggregate")
                .tenantId("tenant-dlq")
                .solutionCode("TEST")
                .payload("{\"test\":true}")
                .kafkaTopic("test.dlq.events")
                .retryPolicy(new RetryPolicy(1, 100L, 1.0, 1_000L))
                .build();
            env.markFailed("exhausted"); // retryCount=1 >= maxAttempts=1 → DEAD
            return env;
        }

        @Test
        @DisplayName("from() creates WAITING entry from dead envelope")
        void fromCreatesWaitingEntry() {
            DomainEventEnvelope env = buildDeadEnvelope();
            DeadLetterEntry entry = DeadLetterEntry.from(env, "Kafka unreachable");

            assertThat(entry.getStatus()).isEqualTo(DLQStatus.WAITING);
            assertThat(entry.getFailureReason()).isEqualTo("Kafka unreachable");
            assertThat(entry.getOriginalEnvelopeId()).isEqualTo(env.getId());
            assertThat(entry.getReprocessedAt()).isNull();
            assertThat(entry.getDiscardReason()).isNull();
        }

        @Test
        @DisplayName("reprocess() transitions WAITING → REPROCESSING")
        void reprocessTransitionsToReprocessing() {
            DeadLetterEntry entry = DeadLetterEntry.from(buildDeadEnvelope(), "error");
            entry.reprocess();
            assertThat(entry.getStatus()).isEqualTo(DLQStatus.REPROCESSING);
        }

        @Test
        @DisplayName("markReprocessed() transitions REPROCESSING → REPROCESSED")
        void markReprocessedSetsTimestamp() {
            DeadLetterEntry entry = DeadLetterEntry.from(buildDeadEnvelope(), "error");
            entry.reprocess();
            entry.markReprocessed();

            assertThat(entry.getStatus()).isEqualTo(DLQStatus.REPROCESSED);
            assertThat(entry.getReprocessedAt()).isNotNull();
        }

        @Test
        @DisplayName("discard() transitions to DISCARDED with reason")
        void discardSetsReason() {
            DeadLetterEntry entry = DeadLetterEntry.from(buildDeadEnvelope(), "error");
            entry.discard("Aggregate no longer exists");

            assertThat(entry.getStatus()).isEqualTo(DLQStatus.DISCARDED);
            assertThat(entry.getDiscardReason()).isEqualTo("Aggregate no longer exists");
        }

        @Test
        @DisplayName("reprocess() from non-WAITING state throws IllegalStateException")
        void reprocessFromReprocessingThrows() {
            DeadLetterEntry entry = DeadLetterEntry.from(buildDeadEnvelope(), "error");
            entry.reprocess();
            assertThatIllegalStateException().isThrownBy(entry::reprocess);
        }

        @Test
        @DisplayName("discard() after REPROCESSED throws IllegalStateException")
        void discardAfterReprocessedThrows() {
            DeadLetterEntry entry = DeadLetterEntry.from(buildDeadEnvelope(), "error");
            entry.reprocess();
            entry.markReprocessed();
            assertThatIllegalStateException()
                .isThrownBy(() -> entry.discard("too late"));
        }

        @Test
        @DisplayName("restore() correctly sets all persisted fields")
        void restoreSetsAllFields() {
            LocalDateTime failedAt     = LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.MILLIS);
            LocalDateTime reprocessed  = LocalDateTime.now().minusMinutes(5).truncatedTo(ChronoUnit.MILLIS);
            DeadLetterEntryId id       = DeadLetterEntryId.generate();
            EnvelopeId envId           = EnvelopeId.generate();

            DeadLetterEntry entry = DeadLetterEntry.restore(
                id, envId, "test.topic", "{}", "original error",
                DLQStatus.REPROCESSED, failedAt, reprocessed,
                null, RetryPolicy.defaultDlqPolicy()
            );

            assertThat(entry.getId()).isEqualTo(id);
            assertThat(entry.getOriginalEnvelopeId()).isEqualTo(envId);
            assertThat(entry.getStatus()).isEqualTo(DLQStatus.REPROCESSED);
            assertThat(entry.getFailedAt()).isCloseTo(failedAt, within(1, ChronoUnit.SECONDS));
            assertThat(entry.getReprocessedAt()).isCloseTo(reprocessed, within(1, ChronoUnit.SECONDS));
        }
    }

    // ── DomainEventEnvelope.restore ──────────────────────────────────────────

    @Nested
    @DisplayName("DomainEventEnvelope.restore()")
    class EnvelopeRestoreTest {

        @Test
        @DisplayName("restore() correctly reconstructs PUBLISHED envelope")
        void restorePublishedEnvelope() {
            EnvelopeId id = EnvelopeId.generate();
            LocalDateTime occurred   = LocalDateTime.now().minusMinutes(10);
            LocalDateTime published  = LocalDateTime.now().minusMinutes(8);

            DomainEventEnvelope env = DomainEventEnvelope.restore(
                id, "corr-001", null, "TestEvent",
                "agg-001", "TestAggregate", "tenant-001", "TEST",
                "{\"x\":1}", 1,
                "test.events", "agg-001",
                EnvelopeStatus.PUBLISHED, 0, null,
                occurred, published, 1,
                RetryPolicy.defaultOutboxPolicy()
            );

            assertThat(env.getId()).isEqualTo(id);
            assertThat(env.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED);
            assertThat(env.getRetryCount()).isZero();
            assertThat(env.getPublishedAt()).isEqualTo(published);
            assertThat(env.getOccurredAt()).isEqualTo(occurred);
            assertThat(env.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("restore() correctly reconstructs FAILED envelope with retryCount")
        void restoreFailedEnvelopeWithRetryCount() {
            DomainEventEnvelope env = DomainEventEnvelope.restore(
                EnvelopeId.generate(), "corr-002", null, "FailEvent",
                "agg-002", "Aggregate", "tenant-001", "TEST",
                "{}", 1, "test.events", "agg-002",
                EnvelopeStatus.FAILED, 3, "last known error",
                LocalDateTime.now(), null, 3,
                RetryPolicy.defaultOutboxPolicy()
            );

            assertThat(env.getStatus()).isEqualTo(EnvelopeStatus.FAILED);
            assertThat(env.getRetryCount()).isEqualTo(3);
            assertThat(env.getLastError()).isEqualTo("last known error");
            // isRetryable() should be true for FAILED status
            assertThat(env.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("restore() does not call state-machine and cannot throw")
        void restoreDoesNotThrowForAnyStatus() {
            // Previously, trying to markPublished on a DEAD envelope would throw.
            // With restore(), all statuses can be reconstructed safely.
            assertThatNoException().isThrownBy(() -> DomainEventEnvelope.restore(
                EnvelopeId.generate(), "corr-003", null, "DeadEvent",
                "agg-003", "Aggregate", "tenant-001", "TEST",
                "{}", 1, "test.events", "agg-003",
                EnvelopeStatus.DEAD, 5, "max attempts exhausted",
                LocalDateTime.now(), null, 5,
                RetryPolicy.defaultOutboxPolicy()
            ));
        }
    }

    // ── OutboxEntry.restore ───────────────────────────────────────────────────

    @Nested
    @DisplayName("OutboxEntry.restore()")
    class OutboxEntryRestoreTest {

        @Test
        @DisplayName("restore() correctly sets all persisted fields")
        void restoreSetsAllFields() {
            OutboxEntryId id    = OutboxEntryId.generate();
            EnvelopeId envId   = EnvelopeId.generate();
            LocalDateTime sched = LocalDateTime.now().minusMinutes(30);
            LocalDateTime proc  = LocalDateTime.now().minusMinutes(29);

            OutboxEntry entry = OutboxEntry.restore(
                id, envId, "tenant-001",
                "test.topic", "agg-key",
                com.yowyob.kernel.event.domain.enums.OutboxStatus.PROCESSED,
                sched, proc, 1, Map.of("X-Test", "value")
            );

            assertThat(entry.getId()).isEqualTo(id);
            assertThat(entry.getEnvelopeId()).isEqualTo(envId);
            assertThat(entry.getTenantId()).isEqualTo("tenant-001");
            assertThat(entry.getStatus())
                .isEqualTo(com.yowyob.kernel.event.domain.enums.OutboxStatus.PROCESSED);
            assertThat(entry.getProcessingAttempt()).isEqualTo(1);
            assertThat(entry.getProcessedAt()).isEqualTo(proc);
            assertThat(entry.getHeaders()).containsEntry("X-Test", "value");
        }
    }

    // ── EventSchema ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("EventSchema")
    class EventSchemaTest {

        @Test
        @DisplayName("new schema is not deprecated")
        void newSchemaIsNotDeprecated() {
            EventSchema schema = new EventSchema(
                SchemaId.generate(), "TestEvent", 1, "TEST", "{}", SchemaCompatibility.FULL);
            assertThat(schema.isDeprecated()).isFalse();
            assertThat(schema.getDeprecatedAt()).isNull();
        }

        @Test
        @DisplayName("deprecate() sets deprecatedAt timestamp")
        void deprecateSetsTimestamp() {
            EventSchema schema = new EventSchema(
                SchemaId.generate(), "TestEvent", 1, "TEST", "{}", SchemaCompatibility.FULL);
            schema.deprecate();
            assertThat(schema.isDeprecated()).isTrue();
            assertThat(schema.getDeprecatedAt()).isNotNull();
        }

        @Test
        @DisplayName("BACKWARD and FULL compatibility are backward-compatible")
        void backwardCompatibilityFlags() {
            EventSchema backward = new EventSchema(
                SchemaId.generate(), "E", 1, "T", "{}", SchemaCompatibility.BACKWARD);
            EventSchema forward = new EventSchema(
                SchemaId.generate(), "E", 2, "T", "{}", SchemaCompatibility.FORWARD);
            EventSchema full = new EventSchema(
                SchemaId.generate(), "E", 3, "T", "{}", SchemaCompatibility.FULL);

            assertThat(backward.isBackwardCompatible()).isTrue();
            assertThat(forward.isBackwardCompatible()).isFalse();
            assertThat(full.isBackwardCompatible()).isTrue();
        }
    }
}
