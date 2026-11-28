package com.yowyob.kernel.event.application.service;

import com.yowyob.kernel.event.application.port.out.BrokerAvailabilityPort;
import com.yowyob.kernel.event.application.port.out.DeadLetterRepository;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.EventMetricsPort;
import com.yowyob.kernel.event.application.port.out.KafkaPublisherPort;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.kernel.event.domain.model.DeadLetterEntry;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.kernel.event.domain.vo.RetryPolicy;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Automatic outbox relay — the at-least-once guarantee of the yow-event-kernel (risk R2 of the
 * Kafka audit, decided 2026-11-26).
 *
 * <p>{@link EventPublisherService} persists every envelope, then publishes it directly. Whatever
 * did not end PUBLISHED is taken over here, on a ShedLock-guarded schedule (one instance at a
 * time across the cluster):
 * <ul>
 *   <li><b>FAILED</b> — the direct publish (or a previous relay attempt) failed; re-published once
 *       its exponential backoff ({@code initial-delay * multiplier^(n-1)}, capped at
 *       {@code max-delay}) is due;</li>
 *   <li><b>PENDING</b> older than {@code pending-grace} — the process died between the insert and
 *       the outcome of the direct publish;</li>
 *   <li><b>RETRYING</b> whose claim expired — a relay instance died mid-attempt.</li>
 * </ul>
 *
 * <p>Each envelope is first <em>claimed</em> with an optimistic update (version check), so even a
 * lock lost to {@code lockAtMostFor} cannot make two instances publish the same envelope in the
 * same round. Once {@code max-attempts} (the initial direct publish included) is reached, the
 * envelope becomes DEAD: a WAITING dead-letter entry is created (the existing manual
 * {@code ProcessDeadLetterUseCase#reprocess} replay keeps working on it), an ERROR is logged with
 * the greppable marker {@code OUTBOX_ENVELOPE_DEAD}, and the {@code yow.event.outbox.dead.total}
 * counter is incremented.
 *
 * <p>A broker outage never burns attempts: the cycle is skipped while the broker does not answer a
 * short probe, and a cycle stops at the first send that times out.
 *
 * <p>Delivery is at-least-once: a relay can re-send an event whose first publish actually reached
 * the broker before the process died — consumers deduplicate on the {@code X-Yow-Envelope-Id}
 * header, which is the envelope id and therefore identical on every re-send.
 */
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    static final String OUTCOME_PUBLISHED = "PUBLISHED";
    static final String OUTCOME_RETRY = "RETRY_SCHEDULED";
    static final String OUTCOME_DEAD = "DEAD";
    static final String OUTCOME_CLAIM_LOST = "CLAIM_LOST";
    static final String OUTCOME_BROKER_DOWN = "BROKER_UNAVAILABLE";

    /** Upper bound of one blocking cycle — below the lock's {@code lockAtMostFor}. */
    private static final Duration CYCLE_TIMEOUT = Duration.ofMinutes(4);

    private final EventEnvelopeRepository envelopeRepository;
    private final DeadLetterRepository dlqRepository;
    private final KafkaPublisherPort kafkaPublisher;
    private final BrokerAvailabilityPort broker;
    private final EventMetricsPort metrics;
    private final RetryPolicy retryPolicy;
    private final int batchSize;
    private final Duration pendingGrace;
    private final Duration claimTimeout;

    public OutboxRelayService(
            final EventEnvelopeRepository envelopeRepository,
            final DeadLetterRepository dlqRepository,
            final KafkaPublisherPort kafkaPublisher,
            final BrokerAvailabilityPort broker,
            final EventMetricsPort metrics,
            final RetryPolicy retryPolicy,
            final int batchSize,
            final Duration pendingGrace,
            final Duration claimTimeout) {
        this.envelopeRepository = Objects.requireNonNull(envelopeRepository);
        this.dlqRepository = Objects.requireNonNull(dlqRepository);
        this.kafkaPublisher = Objects.requireNonNull(kafkaPublisher);
        this.broker = Objects.requireNonNull(broker);
        this.metrics = Objects.requireNonNull(metrics);
        this.retryPolicy = Objects.requireNonNull(retryPolicy);
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
        this.batchSize = batchSize;
        this.pendingGrace = Objects.requireNonNull(pendingGrace);
        this.claimTimeout = Objects.requireNonNull(claimTimeout);
    }

    /**
     * Scheduled entry point. Blocks (on the scheduler thread, never a reactive one) until the
     * cycle is over, so that the ShedLock lock really covers the whole cycle.
     */
    @Scheduled(fixedDelayString = "${yow.event.outbox.relay.poll-interval-ms:5000}",
               initialDelayString = "${yow.event.outbox.relay.poll-interval-ms:5000}")
    @SchedulerLock(name = "yow-event-outbox-relay", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1S")
    public void scheduledRelay() {
        try {
            Integer handled = relayDueEnvelopes().block(CYCLE_TIMEOUT);
            if (handled != null && handled > 0) {
                log.info("Outbox relay cycle: {} envelope(s) handled", handled);
            }
        } catch (RuntimeException e) {
            log.error("Outbox relay cycle failed: {}", e.toString(), e);
        }
    }

    /**
     * One relay cycle.
     *
     * @return the number of envelopes whose attempt was recorded (published, rescheduled or dead)
     */
    public Mono<Integer> relayDueEnvelopes() {
        return broker.isAvailable().flatMap(available -> {
            if (!Boolean.TRUE.equals(available)) {
                log.warn("Outbox relay cycle skipped: Kafka broker unreachable (no attempt is consumed)");
                return Mono.just(0);
            }
            return envelopeRepository.findRelayCandidates(pendingGrace, batchSize)
                .concatMap(this::relay)
                .takeUntil(OUTCOME_BROKER_DOWN::equals)
                .filter(outcome -> !OUTCOME_CLAIM_LOST.equals(outcome))
                .count()
                .map(Long::intValue);
        });
    }

    private Mono<String> relay(final DomainEventEnvelope candidate) {
        DomainEventEnvelope envelope = candidate.withRetryPolicy(retryPolicy);
        int expectedVersion = envelope.getVersion();
        return envelopeRepository.claimForRelay(envelope.getId(), expectedVersion, claimTimeout)
            .flatMap(claimed -> {
                if (!Boolean.TRUE.equals(claimed)) {
                    metrics.recordRelayOutcome(envelope, OUTCOME_CLAIM_LOST);
                    return Mono.just(OUTCOME_CLAIM_LOST);
                }
                envelope.claimForRelay();
                return publish(envelope);
            });
    }

    private Mono<String> publish(final DomainEventEnvelope envelope) {
        long startMs = System.currentTimeMillis();
        return Mono.defer(() -> kafkaPublisher.publish(envelope))
            .then(Mono.defer(() -> {
                envelope.markPublished();
                metrics.recordPublished(envelope, System.currentTimeMillis() - startMs);
                metrics.recordRelayOutcome(envelope, OUTCOME_PUBLISHED);
                log.info("Outbox relay published envelope {} to {} (attempt {})",
                    envelope.getId().value(), envelope.getKafkaTopic(), envelope.getRetryCount() + 1);
                return envelopeRepository.updateStatus(envelope.getId(), envelope.getStatus(),
                        envelope.getPublishedAt(), null, envelope.getRetryCount(), envelope.getVersion())
                    .thenReturn(OUTCOME_PUBLISHED);
            }))
            .onErrorResume(error -> recordFailure(envelope, error));
    }

    private Mono<String> recordFailure(final DomainEventEnvelope envelope, final Throwable error) {
        String reason = String.valueOf(error.getMessage());
        metrics.recordFailed(envelope, error.getClass().getSimpleName());
        envelope.markFailed(reason);
        boolean brokerDown = isTimeout(error);

        if (envelope.isDead()) {
            log.error("OUTBOX_ENVELOPE_DEAD envelope={} topic={} eventType={} tenant={} attempts={} — "
                    + "no further automatic re-publication; replay it from the dead-letter queue. Last error: {}",
                envelope.getId().value(), envelope.getKafkaTopic(), envelope.getEventType(),
                envelope.getTenantId(), envelope.getRetryCount(), reason);
            metrics.recordMovedToDlq(envelope);
            metrics.recordRelayOutcome(envelope, OUTCOME_DEAD);
            return envelopeRepository.recordRelayFailure(envelope.getId(), EnvelopeStatus.DEAD, reason,
                    envelope.getRetryCount(), envelope.getVersion(), Duration.ZERO)
                .then(dlqRepository.save(DeadLetterEntry.from(envelope, reason)))
                .thenReturn(brokerDown ? OUTCOME_BROKER_DOWN : OUTCOME_DEAD);
        }

        Duration retryIn = retryPolicy.nextDelay(envelope.getRetryCount());
        log.warn("Outbox relay attempt {} of envelope {} to {} failed ({}); next attempt in {}",
            envelope.getRetryCount(), envelope.getId().value(), envelope.getKafkaTopic(), reason, retryIn);
        metrics.recordRelayOutcome(envelope, OUTCOME_RETRY);
        return envelopeRepository.recordRelayFailure(envelope.getId(), envelope.getStatus(), reason,
                envelope.getRetryCount(), envelope.getVersion(), retryIn)
            .thenReturn(brokerDown ? OUTCOME_BROKER_DOWN : OUTCOME_RETRY);
    }

    /** A send that timed out means the broker went away mid-cycle: stop instead of burning attempts. */
    private static boolean isTimeout(final Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException || t.getClass().getSimpleName().equals("TimeoutException")) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
