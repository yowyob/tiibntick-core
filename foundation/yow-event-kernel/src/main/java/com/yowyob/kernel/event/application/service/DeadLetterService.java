package com.yowyob.kernel.event.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.application.port.in.ProcessDeadLetterUseCase;
import com.yowyob.kernel.event.application.port.out.DeadLetterRepository;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.KafkaPublisherPort;
import com.yowyob.kernel.event.domain.enums.DLQStatus;
import com.yowyob.kernel.event.domain.model.DeadLetterEntry;
import com.yowyob.kernel.event.domain.vo.DeadLetterEntryId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Application service for managing Dead-Letter Queue entries.
 *
 * <p>Provides administrative operations (list, reprocess, discard) used by
 * operators when dealing with permanently failed event deliveries.
 */
@Service
public class DeadLetterService implements ProcessDeadLetterUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

    private final DeadLetterRepository    dlqRepository;
    private final EventEnvelopeRepository envelopeRepository;
    private final KafkaPublisherPort      kafkaPublisher;

    public DeadLetterService(
            final DeadLetterRepository dlqRepository,
            final EventEnvelopeRepository envelopeRepository,
            final KafkaPublisherPort kafkaPublisher) {
        this.dlqRepository      = Objects.requireNonNull(dlqRepository);
        this.envelopeRepository = Objects.requireNonNull(envelopeRepository);
        this.kafkaPublisher     = Objects.requireNonNull(kafkaPublisher);
    }

    @Override
    public Flux<DeadLetterEntry> listWaiting(final String tenantId) {
        return dlqRepository.findWaiting(tenantId);
    }

    @Override
    public Mono<DeadLetterEntry> findById(final DeadLetterEntryId id, final String tenantId) {
        return dlqRepository.findById(id, tenantId);
    }

    @Override
    public Mono<Void> reprocess(final DeadLetterEntryId id, final String tenantId) {
        return dlqRepository.findById(id, tenantId)
            .switchIfEmpty(Mono.error(
                new IllegalArgumentException("DLQ entry not found: " + id.value())))
            .flatMap(entry -> {
                entry.reprocess();
                return envelopeRepository.findById(entry.getOriginalEnvelopeId(), tenantId)
                    .flatMap(envelope -> {
                        log.info("Reprocessing DLQ entry {} (envelope {})",
                            id.value(), envelope.getId().value());
                        return kafkaPublisher.publishAsReplay(envelope)
                            .then(Mono.defer(() -> {
                                entry.markReprocessed();
                                return dlqRepository.updateStatus(
                                    entry.getId(), DLQStatus.REPROCESSED,
                                    LocalDateTime.now(), null);
                            }));
                    })
                    .switchIfEmpty(Mono.error(
                        new IllegalStateException("Envelope not found for DLQ entry " + id.value())));
            })
            .then();
    }

    @Override
    public Mono<Long> reprocessByTopic(final String kafkaTopic, final String tenantId) {
        return dlqRepository.findWaitingByTopic(kafkaTopic, tenantId)
            .flatMap(entry -> reprocess(entry.getId(), tenantId).thenReturn(1L))
            .reduce(0L, Long::sum);
    }

    @Override
    public Mono<Void> discard(
            final DeadLetterEntryId id, final String tenantId, final String reason) {
        Objects.requireNonNull(reason, "reason must not be null when discarding a DLQ entry");
        return dlqRepository.findById(id, tenantId)
            .switchIfEmpty(Mono.error(
                new IllegalArgumentException("DLQ entry not found: " + id.value())))
            .flatMap(entry -> {
                entry.discard(reason);
                log.info("DLQ entry {} discarded by operator. Reason: {}", id.value(), reason);
                return dlqRepository.updateStatus(entry.getId(), DLQStatus.DISCARDED, null, reason);
            })
            .then();
    }

    @Override
    public Mono<Long> countWaiting(final String tenantId) {
        return dlqRepository.countWaiting(tenantId);
    }
}
