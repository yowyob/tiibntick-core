package com.yowyob.tiibntick.core.trust.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordDaoRuleUseCase;

/**
 * Application Service — {@code DaoRuleChainService}.
 *
 * <p>Anchors DAO zone collective governance rules on Hyperledger Fabric.
 * Once a rule is activated on-chain, it is immutable and serves as the
 * authoritative reference for zone governance decisions.
 *
 * <p>Implements {@link RecordDaoRuleUseCase}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Service
public class DaoRuleChainService implements RecordDaoRuleUseCase {

    private static final Logger log = LoggerFactory.getLogger(DaoRuleChainService.class);

    private final LogisticEventPublisherService publisherService;
    private final MeterRegistry meterRegistry;

    public DaoRuleChainService(
            final LogisticEventPublisherService publisherService,
            final MeterRegistry meterRegistry) {
        this.publisherService = publisherService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Builds a {@code DAO_RULE_ACTIVATED} logistic event and publishes it
     * to Kafka for Fabric anchoring.
     */
    @Override
    public Mono<String> record(final String zoneId, final String rule, final String tenantId) {
        log.info("Recording DAO rule activation — zoneId={}, tenant={}", zoneId, tenantId);

        final LogisticTrustEvent event = LogisticTrustEvent.forDaoRuleActivated(
                zoneId, tenantId, rule);

        return publisherService.publish(event)
                .doOnSuccess(v -> {
                    meterRegistry.counter("tnt.trust.dao.rule.activated",
                            "tenant", tenantId).increment();
                    log.info("DAO rule event published — correlationId={}", event.getCorrelationId());
                })
                .thenReturn(event.getCorrelationId());
    }
}
