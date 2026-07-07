package com.yowyob.kernel.event.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.kernel.event.domain.model.OutboxEntry;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;
import com.yowyob.kernel.event.domain.vo.RetryPolicy;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@code yow-event-kernel} domain model.
 *
 * <p>Tests exercise the pure domain logic (no Spring context, no infrastructure)
 * to verify invariants, state machine transitions and business rules.
 *
 * <p>Coverage targets:
 * <ul>
 *   <li>{@link RetryPolicy} — delay computation and max-attempts check</li>
 *   <li>{@link DomainEventEnvelope} — lifecycle state machine</li>
 *   <li>{@link OutboxEntry} — processing state transitions</li>
 * </ul>
 */
class YowEventKernelDomainTest {

    // ── RetryPolicy ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("RetryPolicy")
    class RetryPolicyTest {

        @Test
        @DisplayName("default outbox policy has correct parameters")
        void defaultOutboxPolicyHasCorrectParameters() {
            RetryPolicy policy = RetryPolicy.defaultOutboxPolicy();

            assertThat(policy.maxAttempts()).isEqualTo(5);
            assertThat(policy.initialDelayMs()).isEqualTo(1_000L);
            assertThat(policy.multiplier()).isEqualTo(2.0);
            assertThat(policy.maxDelayMs()).isEqualTo(60_000L);
        }

        @Test
        @DisplayName("first retry uses initial delay")
        void firstRetryUsesInitialDelay() {
            RetryPolicy policy = RetryPolicy.defaultOutboxPolicy();
            Duration delay = policy.nextDelay(1);
            assertThat(delay).isEqualTo(Duration.ofMillis(1_000L));
        }

        @Test
        @DisplayName("second retry doubles the delay (exponential back-off)")
        void secondRetryDoublesDelay() {
            RetryPolicy policy = RetryPolicy.defaultOutboxPolicy();
            Duration delay = policy.nextDelay(2);
            // 1000 * 2^1 = 2000 ms
            assertThat(delay).isEqualTo(Duration.ofMillis(2_000L));
        }

        @Test
        @DisplayName("delay is capped at maxDelayMs")
        void delayIsCappedAtMaxDelay() {
            RetryPolicy policy = RetryPolicy.defaultOutboxPolicy();
            // Attempt 10: 1000 * 2^9 = 512000 ms → capped at 60000
            Duration delay = policy.nextDelay(10);
            assertThat(delay).isEqualTo(Duration.ofMillis(60_000L));
        }

        @Test
        @DisplayName("hasExceededMaxAttempts returns false when under limit")
        void hasNotExceededWhenUnderLimit() {
            RetryPolicy policy = RetryPolicy.defaultOutboxPolicy();
            assertThat(policy.hasExceededMaxAttempts(4)).isFalse();
        }

        @Test
        @DisplayName("hasExceededMaxAttempts returns true at limit")
        void hasExceededAtLimit() {
            RetryPolicy policy = RetryPolicy.defaultOutboxPolicy();
            assertThat(policy.hasExceededMaxAttempts(5)).isTrue();
        }

        @Test
        @DisplayName("constructor rejects multiplier less than 1.0")
        void rejectsMultiplierLessThanOne() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> new RetryPolicy(3, 1_000L, 0.5, 60_000L))
                .withMessageContaining("multiplier");
        }

        @Test
        @DisplayName("constructor rejects maxDelayMs less than initialDelayMs")
        void rejectsMaxDelayLessThanInitial() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> new RetryPolicy(3, 5_000L, 1.5, 1_000L))
                .withMessageContaining("maxDelayMs");
        }
    }

    // ── DomainEventEnvelope ──────────────────────────────────────────────────

    @Nested
    @DisplayName("DomainEventEnvelope — state machine")
    class DomainEventEnvelopeTest {

        private DomainEventEnvelope buildEnvelope() {
            return DomainEventEnvelope.wrap()
                .correlationId("corr-001")
                .eventType("TestEvent")
                .aggregateId("agg-001")
                .aggregateType("TestAggregate")
                .tenantId("tenant-001")
                .solutionCode("TEST")
                .payload("{\"field\":\"value\"}")
                .kafkaTopic("test.events")
                .build();
        }

        @Test
        @DisplayName("new envelope starts in PENDING status")
        void newEnvelopeIsPending() {
            DomainEventEnvelope env = buildEnvelope();
            assertThat(env.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
            assertThat(env.getRetryCount()).isZero();
            assertThat(env.isRetryable()).isTrue();
            assertThat(env.isDead()).isFalse();
        }

        @Test
        @DisplayName("payload hash is computed from payload content")
        void payloadHashIsComputedFromContent() {
            DomainEventEnvelope env = buildEnvelope();
            assertThat(env.getPayloadHash())
                .isNotNull()
                .hasSize(64);  // SHA-256 hex = 64 chars
        }

        @Test
        @DisplayName("two envelopes with same payload have same hash")
        void samePayloadProducesSameHash() {
            DomainEventEnvelope e1 = buildEnvelope();
            DomainEventEnvelope e2 = buildEnvelope();
            assertThat(e1.getPayloadHash()).isEqualTo(e2.getPayloadHash());
        }

        @Test
        @DisplayName("markPublished transitions PENDING → PUBLISHED")
        void markPublishedFromPending() {
            DomainEventEnvelope env = buildEnvelope();
            env.markPublished();

            assertThat(env.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED);
            assertThat(env.getPublishedAt()).isNotNull();
            assertThat(env.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("markPublished from PUBLISHED throws IllegalStateException")
        void markPublishedFromPublishedThrows() {
            DomainEventEnvelope env = buildEnvelope();
            env.markPublished();

            assertThatIllegalStateException()
                .isThrownBy(env::markPublished);
        }

        @Test
        @DisplayName("markFailed increments retry count and sets FAILED status")
        void markFailedIncrementsRetryCount() {
            DomainEventEnvelope env = buildEnvelope();
            env.markFailed("Kafka unavailable");

            assertThat(env.getStatus()).isEqualTo(EnvelopeStatus.FAILED);
            assertThat(env.getRetryCount()).isEqualTo(1);
            assertThat(env.getLastError()).isEqualTo("Kafka unavailable");
        }

        @Test
        @DisplayName("markFailed moves envelope to DEAD after max attempts")
        void markFailedMovesToDeadAfterMaxAttempts() {
            RetryPolicy policy = new RetryPolicy(2, 100L, 1.5, 5_000L);
            DomainEventEnvelope env = DomainEventEnvelope.wrap()
                .correlationId("corr-002")
                .eventType("TestEvent")
                .aggregateId("agg-002")
                .aggregateType("TestAggregate")
                .tenantId("tenant-001")
                .solutionCode("TEST")
                .payload("{}")
                .kafkaTopic("test.events")
                .retryPolicy(policy)
                .build();

            env.markFailed("Error 1");   // attempt 1 → FAILED
            env.scheduleRetry();         // FAILED → RETRYING
            env.markFailed("Error 2");   // attempt 2 → DEAD (maxAttempts = 2)

            assertThat(env.getStatus()).isEqualTo(EnvelopeStatus.DEAD);
            assertThat(env.isDead()).isTrue();
            assertThat(env.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("scheduleRetry transitions FAILED → RETRYING")
        void scheduleRetryFromFailed() {
            DomainEventEnvelope env = buildEnvelope();
            env.markFailed("transient error");
            env.scheduleRetry();

            assertThat(env.getStatus()).isEqualTo(EnvelopeStatus.RETRYING);
        }

        @Test
        @DisplayName("scheduleRetry from PENDING throws IllegalStateException")
        void scheduleRetryFromPendingThrows() {
            DomainEventEnvelope env = buildEnvelope();
            assertThatIllegalStateException()
                .isThrownBy(env::scheduleRetry);
        }

        @Test
        @DisplayName("optimistic lock version increments on every state change")
        void versionIncrementsOnStateChange() {
            DomainEventEnvelope env = buildEnvelope();
            assertThat(env.getVersion()).isZero();

            env.markFailed("error");
            assertThat(env.getVersion()).isEqualTo(1);

            env.scheduleRetry();
            assertThat(env.getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("partition key defaults to aggregateId when not specified")
        void partitionKeyDefaultsToAggregateId() {
            DomainEventEnvelope env = buildEnvelope();
            assertThat(env.getKafkaPartitionKey()).isEqualTo("agg-001");
        }

        @Test
        @DisplayName("builder rejects null required fields")
        void builderRejectsNullRequiredFields() {
            assertThatNullPointerException()
                .isThrownBy(() -> DomainEventEnvelope.wrap()
                    // eventType missing — null
                    .correlationId("corr")
                    .aggregateId("agg")
                    .aggregateType("Type")
                    .tenantId("tenant")
                    .solutionCode("TST")
                    .payload("{}")
                    .kafkaTopic("topic")
                    .build()
                );
        }
    }

    // ── EnvelopeId ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("EnvelopeId")
    class EnvelopeIdTest {

        @Test
        @DisplayName("generate() produces unique identifiers")
        void generateProducesUniqueIds() {
            EnvelopeId id1 = EnvelopeId.generate();
            EnvelopeId id2 = EnvelopeId.generate();
            assertThat(id1.value()).isNotEqualTo(id2.value());
        }

        @Test
        @DisplayName("of() rejects blank value")
        void ofRejectsBlank() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> EnvelopeId.of("  "));
        }

        @Test
        @DisplayName("of() rejects null value")
        void ofRejectsNull() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> EnvelopeId.of(null));
        }
    }
}
