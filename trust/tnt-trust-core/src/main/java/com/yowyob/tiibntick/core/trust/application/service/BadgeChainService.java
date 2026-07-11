package com.yowyob.tiibntick.core.trust.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordBadgeUseCase;

/**
 * Application Service — {@code BadgeChainService}.
 *
 * <p>Anchors deliverer reputation badge awards on Hyperledger Fabric.
 * Badges are portable, verifiable credentials that actors can carry
 * independently of the TiiBnTick platform.
 *
 * <p>Implements {@link RecordBadgeUseCase}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Service
public class BadgeChainService implements RecordBadgeUseCase {

    private static final Logger log = LoggerFactory.getLogger(BadgeChainService.class);

    private final LogisticEventPublisherService publisherService;
    private final MeterRegistry meterRegistry;

    public BadgeChainService(
            final LogisticEventPublisherService publisherService,
            final MeterRegistry meterRegistry) {
        this.publisherService = publisherService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Builds a {@code BADGE_AWARDED} logistic event and publishes it
     * to Kafka for Fabric anchoring.
     */
    @Override
    public Mono<String> record(
            final String actorId,
            final String badgeType,
            final int points,
            final String tenantId) {

        log.info("Recording badge award — actorId={}, badge={}, points={}", actorId, badgeType, points);

        final LogisticTrustEvent event = LogisticTrustEvent.forBadgeAwarded(
                actorId, tenantId, badgeType, points);

        return publisherService.publish(event)
                .doOnSuccess(v -> {
                    meterRegistry.counter("tnt.trust.badge.awarded",
                            "badgeType", badgeType,
                            "tenant", tenantId).increment();
                    log.info("Badge event published — correlationId={}", event.getCorrelationId());
                })
                .thenReturn(event.getCorrelationId());
    }
}
