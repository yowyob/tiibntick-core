package com.yowyob.kernel.event.application.service;

import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.application.port.out.DeadLetterRepository;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.EventMetricsPort;
import com.yowyob.kernel.event.application.port.out.KafkaPublisherPort;
import com.yowyob.kernel.event.application.port.out.OutboxEntryRepository;
import com.yowyob.kernel.event.application.port.out.OutboxPollerPort;
import com.yowyob.kernel.event.domain.enums.OutboxStatus;
import com.yowyob.kernel.event.domain.model.DeadLetterEntry;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.kernel.event.domain.model.OutboxEntry;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core outbox polling service.
 *
 * <p>Runs on a fixed-delay schedule (default: 1 second) and processes batches
 * of pending {@link OutboxEntry} objects. For each entry:
 * <ol>
 *   <li>Loads the corresponding {@link DomainEventEnvelope} from the event store.</li>
 *   <li>Publishes the envelope payload to Kafka.</li>
 *   <li>On success: marks the entry as PROCESSED and the envelope as PUBLISHED.</li>
 *   <li>On failure: increments the retry counter; if exhausted, moves to the DLQ.</li>
 * </ol>
 *
 * <p>Concurrent execution is prevented by a combination of:
 * <ul>
 *   <li>An in-process {@link AtomicBoolean} lock ({@code pollingInProgress}).</li>
 *   <li>PostgreSQL {@code SELECT … FOR UPDATE SKIP LOCKED} in
 *       {@link OutboxEntryRepository#fetchPendingBatch}.</li>
 * </ul>
 *
 * <p>The batch size and poll interval are configurable via
 * {@code yow.event.outbox.batch-size} and {@code yow.event.outbox.poll-interval-ms}.
 */
@Service
public class OutboxPollerService implements OutboxPollerPort {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollerService.class);

    private static final int DEFAULT_BATCH_SIZE = 50;

    private final EventEnvelopeRepository envelopeRepository;
    private final OutboxEntryRepository   outboxRepository;
    private final DeadLetterRepository    dlqRepository;
    private final KafkaPublisherPort      kafkaPublisher;
    private final EventMetricsPort        metrics;

    /** Guards against overlapping poll cycles. */
    private final AtomicBoolean pollingInProgress = new AtomicBoolean(false);

    public OutboxPollerService(
            final EventEnvelopeRepository envelopeRepository,
            final OutboxEntryRepository outboxRepository,
            final DeadLetterRepository dlqRepository,
            final KafkaPublisherPort kafkaPublisher,
            final EventMetricsPort metrics) {
        this.envelopeRepository = Objects.requireNonNull(envelopeRepository);
        this.outboxRepository   = Objects.requireNonNull(outboxRepository);
        this.dlqRepository      = Objects.requireNonNull(dlqRepository);
        this.kafkaPublisher     = Objects.requireNonNull(kafkaPublisher);
        this.metrics            = Objects.requireNonNull(metrics);
    }

    // ── Scheduled polling ────────────────────────────────────────────────────

    /**
     * Entry point for the scheduled polling loop.
     *
     * <p>Delegates to {@link #poll()}. {@code poll()} itself returns as soon as it has
     * subscribed the batch (fire-and-forget, like the rest of this reactive pipeline),
     * so {@code lockAtLeastFor} is what actually prevents another instance from grabbing
     * the lock immediately after this method returns, before the subscribed batch has
     * finished — same reasoning as {@code MediaFileCleanupScheduler}.
     *
     * <p><b>ShedLock-guarded</b> (Chantier D · Audit n°6 · S2 gap closed 2026-07-19):
     * this module is now genuinely wired into the running application —
     * {@code YowEventKernelAutoConfiguration} has been {@code @Import}ed into
     * {@code tnt-bootstrap}'s {@code TntCoreConfig} since the Chantier C · Audit n°3 P1
     * reactivation (2026-07-18) — so this poller runs for real, on every instance, in a
     * multi-instance deployment. A prior version of this Javadoc claimed the opposite and
     * argued a lock here would be dead code; that stopped being true the moment P1 shipped
     * and was not updated in lockstep — see {@code docs/audits/remediation/chantier-c-p5-inventory.md} §3.
     */
    @Scheduled(fixedDelayString = "${yow.event.outbox.poll-interval-ms:1000}")
    @SchedulerLock(name = "yow-event-outbox-poll", lockAtMostFor = "PT1M", lockAtLeastFor = "PT1S")
    public void scheduledPoll() {
        LockAssert.assertLocked();
        poll().subscribe(
            count -> {
                if (count > 0) {
                    log.debug("Outbox poll cycle completed: {} entries processed", count);
                }
            },
            error -> log.error("Outbox poll cycle failed", error)
        );
    }

    // ── OutboxPollerPort ─────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Skips the cycle if a previous poll is still running. This guard is a
     * secondary safety net; the primary protection is the DB-level SKIP LOCKED.
     */
    @Override
    public Mono<Integer> poll() {
        if (!pollingInProgress.compareAndSet(false, true)) {
            log.trace("Poll cycle skipped — previous cycle still running");
            return Mono.just(0);
        }

        return outboxRepository.fetchPendingBatch(DEFAULT_BATCH_SIZE)
            .flatMap(entry -> processEntry(entry).thenReturn(entry))
            .count()
            .map(Long::intValue)
            .doFinally(signal -> pollingInProgress.set(false));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> processEntry(final OutboxEntry entry) {
        entry.process(); // increments attempt counter, sets status PROCESSING

        // publishAndCommit()/handleMissingEnvelope() are both Mono<Void> — they never emit an
        // element even on success — so switchIfEmpty() cannot be chained directly after flatMap()
        // here: it would see an "empty" Mono regardless of whether the envelope was actually
        // found and successfully processed, and fire handleMissingEnvelope() a second time on
        // every single entry. .thenReturn(...)/.then() make both branches genuinely distinguish
        // "found" from "empty" before switchIfEmpty() decides, then collapse back to Mono<Void>.
        return envelopeRepository.findById(entry.getEnvelopeId(), /* tenantId resolved from entry */ null)
            .flatMap(envelope -> publishAndCommit(entry, envelope).thenReturn(true))
            .switchIfEmpty(Mono.defer(() -> handleMissingEnvelope(entry).thenReturn(true)))
            .then();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPolling() {
        return pollingInProgress.get();
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private Mono<Void> publishAndCommit(
            final OutboxEntry entry,
            final DomainEventEnvelope envelope) {
        long startMs = System.currentTimeMillis();

        return kafkaPublisher.publish(envelope)
            .then(Mono.defer(() -> {
                // Deferred so entry/envelope mutation below happens at subscription time,
                // right before commitSuccess() reads their now-updated state — commitSuccess(...)
                // is a plain method call and its arguments (envelope.getStatus()/getVersion()/...)
                // would otherwise be evaluated eagerly, at chain-assembly time, capturing the
                // pre-mutation PENDING/version values instead of the post-publish ones.
                long elapsed = System.currentTimeMillis() - startMs;
                entry.succeed();
                envelope.markPublished();
                metrics.recordPublished(envelope, elapsed);
                log.debug("Published envelope {} to topic {} in {}ms",
                    envelope.getId().value(), envelope.getKafkaTopic(), elapsed);
                return commitSuccess(entry, envelope);
            }))
            .onErrorResume(error -> handlePublishFailure(entry, envelope, error));
    }

    private Mono<Void> commitSuccess(final OutboxEntry entry, final DomainEventEnvelope envelope) {
        return outboxRepository.updateStatus(entry.getId(), OutboxStatus.PROCESSED, LocalDateTime.now())
            .then(envelopeRepository.updateStatus(
                envelope.getId(),
                envelope.getStatus(),
                envelope.getPublishedAt(),
                null,
                envelope.getRetryCount(),
                envelope.getVersion()
            ))
            .then();
    }

    private Mono<Void> handlePublishFailure(
            final OutboxEntry entry,
            final DomainEventEnvelope envelope,
            final Throwable error) {
        log.warn("Failed to publish envelope {} (attempt {}): {}",
            envelope.getId().value(), entry.getProcessingAttempt(), error.getMessage());

        envelope.markFailed(error.getMessage());
        metrics.recordFailed(envelope, error.getClass().getSimpleName());

        if (envelope.isDead()) {
            return moveToDlq(entry, envelope, error.getMessage());
        }

        entry.fail();
        return outboxRepository.updateStatus(entry.getId(), OutboxStatus.FAILED, null)
            .then(envelopeRepository.updateStatus(
                envelope.getId(), envelope.getStatus(), null,
                error.getMessage(), envelope.getRetryCount(), envelope.getVersion()
            ))
            .then();
    }

    private Mono<Void> moveToDlq(
            final OutboxEntry entry,
            final DomainEventEnvelope envelope,
            final String reason) {
        log.error("Envelope {} moved to DLQ after {} attempts. Reason: {}",
            envelope.getId().value(), entry.getProcessingAttempt(), reason);

        metrics.recordMovedToDlq(envelope);

        DeadLetterEntry dlqEntry = DeadLetterEntry.from(envelope, reason);

        return dlqRepository.save(dlqEntry)
            .then(outboxRepository.updateStatus(entry.getId(), OutboxStatus.FAILED, null))
            .then(envelopeRepository.updateStatus(
                envelope.getId(), envelope.getStatus(), null,
                reason, envelope.getRetryCount(), envelope.getVersion()
            ))
            .then();
    }

    private Mono<Void> handleMissingEnvelope(final OutboxEntry entry) {
        // The envelope was deleted or never persisted — skip this entry silently
        log.warn("No envelope found for outbox entry {} (envelopeId={}). Marking as processed.",
            entry.getId().value(), entry.getEnvelopeId().value());
        return outboxRepository.updateStatus(entry.getId(), OutboxStatus.PROCESSED, LocalDateTime.now())
            .then();
    }
}
