package com.yowyob.tiibntick.core.trust.adapter.out.kafka;

import com.yowyob.tiibntick.core.trust.adapter.out.health.TrustAvailabilityGuard;
import com.yowyob.tiibntick.core.trust.adapter.out.messaging.KafkaTrustEventPublisherAdapter;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustRetryQueueRepository;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.TrustRetryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Scheduled Adapter — {@code TrustRetryQueueDrainer}.
 *
 * <p>Periodically flushes {@code tnt_trust.trust_retry_queue} back onto
 * {@code yow.trust.events} once {@link TrustAvailabilityGuard} reports the
 * gateway available again. See §15.5 of
 * {@code TNT_CORE_Connexion_Trust_Module.md} — resilience design.
 *
 * <p>{@code @Transactional} spans the lock-select, the republish, and the
 * mark-processed/mark-failed update in a single transaction, so the
 * {@code FOR UPDATE SKIP LOCKED} row lock taken by
 * {@code TrustRetryQueueR2dbcRepository.lockPending} is actually held across
 * all three steps — required for the multi-replica safety this queue exists
 * to provide (see §15.5bis of the design doc).
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Component
public class TrustRetryQueueDrainer {

    private static final Logger log = LoggerFactory.getLogger(TrustRetryQueueDrainer.class);

    private final TrustRetryQueueRepository retryQueue;
    private final KafkaTrustEventPublisherAdapter gatewayAdapter;
    private final TrustAvailabilityGuard guard;
    private final int batchSize;

    public TrustRetryQueueDrainer(
            final TrustRetryQueueRepository retryQueue,
            final KafkaTrustEventPublisherAdapter gatewayAdapter,
            final TrustAvailabilityGuard guard,
            @org.springframework.beans.factory.annotation.Value(
                    "${tnt.trust.retry-drain-batch-size:100}") final int batchSize) {
        this.retryQueue = retryQueue;
        this.gatewayAdapter = gatewayAdapter;
        this.guard = guard;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${tnt.trust.retry-drain-interval-ms:60000}")
    @Transactional
    public void drainIfAvailable() {
        if (!guard.isAvailable()) {
            return; // no point draining if still down
        }
        retryQueue.lockPendingBatch(batchSize)
                .flatMap(this::republishOne)
                .subscribe();
    }

    private Mono<Void> republishOne(final TrustRetryRecord record) {
        return gatewayAdapter.republish(record.messageKey(), record.messagePayload())
                .then(retryQueue.markProcessed(record.retryId()))
                .doOnSuccess(v -> log.debug("Retry-queue drain: republished retryId={}, type={}",
                        record.retryId(), record.eventType()))
                .onErrorResume(e -> {
                    log.warn("Retry-queue drain: republish failed for retryId={} — {}",
                            record.retryId(), e.getMessage());
                    guard.markUnavailable();
                    return retryQueue.markFailed(record.retryId(), e.getMessage());
                });
    }
}
