package com.yowyob.tiibntick.core.trust.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DaoRuleRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordDaoRuleUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.GetDaoRulesUseCase;
import com.yowyob.tiibntick.core.trust.application.port.out.DaoRuleRepository;

/**
 * Application Service — {@code DaoRuleChainService}.
 *
 * <p>Anchors DAO zone collective governance rules on Hyperledger Fabric.
 * Once a rule is activated on-chain, it is immutable and serves as the
 * authoritative reference for zone governance decisions.
 *
 * <p>Implements {@link RecordDaoRuleUseCase} and {@link GetDaoRulesUseCase}.
 *
 * @author MANFOUO Braun
 * @version 1.1
 */
@Service
public class DaoRuleChainService implements RecordDaoRuleUseCase, GetDaoRulesUseCase {

    private static final Logger log = LoggerFactory.getLogger(DaoRuleChainService.class);

    private final LogisticEventPublisherService publisherService;
    private final DaoRuleRepository daoRuleRepository;
    private final MeterRegistry meterRegistry;

    public DaoRuleChainService(
            final LogisticEventPublisherService publisherService,
            final DaoRuleRepository daoRuleRepository,
            final MeterRegistry meterRegistry) {
        this.publisherService = publisherService;
        this.daoRuleRepository = daoRuleRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Persists a {@link DaoRuleRecord} locally first — so it is
     * immediately visible to reads — then builds a
     * {@code DAO_RULE_ACTIVATED} logistic event and publishes it
     * to Kafka for Fabric anchoring.
     */
    @Override
    @Transactional
    public Mono<String> record(final String zoneId, final String rule, final String tenantId) {
        log.info("Recording DAO rule activation — zoneId={}, tenant={}", zoneId, tenantId);

        final DaoRuleRecord ruleRecord = DaoRuleRecord.activate(zoneId, tenantId, rule);
        final LogisticTrustEvent event = LogisticTrustEvent.forDaoRuleActivated(ruleRecord);

        return daoRuleRepository.save(ruleRecord)
                .then(publisherService.publish(event))
                .doOnSuccess(v -> {
                    meterRegistry.counter("tnt.trust.dao.rule.activated",
                            "tenant", tenantId).increment();
                    log.info("DAO rule event published — correlationId={}", event.getCorrelationId());
                })
                .thenReturn(event.getCorrelationId());
    }

    /** {@inheritDoc} */
    @Override
    public Flux<DaoRuleRecord> getByZoneId(final String zoneId, final String tenantId) {
        return daoRuleRepository.findByZoneId(zoneId, tenantId);
    }
}
