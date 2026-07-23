package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgEventPublisherPort;
import com.yowyob.tiibntick.core.organization.domain.event.FreelancerOrgCreatedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.FreelancerOrgSuspendedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.FreelancerOrgVerifiedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.KycLevelUpgradedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.SubDelivererAssociatedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.SubDelivererRevokedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Outbox-backed messaging adapter implementing {@link FreelancerOrgEventPublisherPort}.
 *
 * <p>Chantier C · Audit n°3 · P5 (identity domain). The previous implementation
 * published Spring {@code ApplicationEvent}s and claimed a {@code tnt-bootstrap}
 * listener forwarded them to Kafka — <strong>no such listener ever existed</strong>
 * (repo-wide grep, 2026-07-20), so every {@code tnt.freelancer_org.*} event was
 * silently lost, which is exactly the Audit n°5 P-01 "never emitted" debt for these
 * topics. This adapter now enqueues each event into yow-event-kernel's transactional
 * outbox ({@link PublishEventUseCase}); {@code OutboxPollerService} relays them to
 * Kafka, finally feeding the already-existing consumer
 * ({@code tnt-actor-core}'s {@code FreelancerOrgEventConsumer}).
 *
 * <p>Kafka topics targeted:
 * <ul>
 *   <li>{@link TntTopics#FREELANCER_ORG_CREATED}</li>
 *   <li>{@link TntTopics#FREELANCER_ORG_VERIFIED}</li>
 *   <li>{@code tnt.freelancer_org.suspended}</li>
 *   <li>{@code tnt.freelancer_org.kyc.upgraded}</li>
 *   <li>{@link TntTopics#FREELANCER_ORG_SUB_DELIVERER_ASSOCIATED}</li>
 *   <li>{@link TntTopics#FREELANCER_ORG_SUB_DELIVERER_REVOKED}</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public class FreelancerOrgEventPublisherAdapter implements FreelancerOrgEventPublisherPort {

    private static final Logger log =
            LoggerFactory.getLogger(FreelancerOrgEventPublisherAdapter.class);

    /** Documented in the module Javadoc since inception; no TntTopics constant yet. */
    static final String TOPIC_SUSPENDED    = "tnt.freelancer_org.suspended";
    /** Documented in the module Javadoc since inception; no TntTopics constant yet. */
    static final String TOPIC_KYC_UPGRADED = "tnt.freelancer_org.kyc.upgraded";

    private static final String AGGREGATE_TYPE = "FreelancerOrganization";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public FreelancerOrgEventPublisherAdapter(PublishEventUseCase publishEventUseCase,
                                              ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishFreelancerOrgCreated(FreelancerOrgCreatedEvent event) {
        log.info("Enqueuing FreelancerOrgCreatedEvent: orgId={}, tenantId={}",
                event.orgId(), event.tenantId());
        return enqueue(TntTopics.FREELANCER_ORG_CREATED, event.orgId(), event.tenantId(),
                event.occurredAt(), event);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishFreelancerOrgVerified(FreelancerOrgVerifiedEvent event) {
        log.info("Enqueuing FreelancerOrgVerifiedEvent: orgId={}, kycLevel={}",
                event.orgId(), event.kycLevel());
        return enqueue(TntTopics.FREELANCER_ORG_VERIFIED, event.orgId(), event.tenantId(),
                event.occurredAt(), event);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishFreelancerOrgSuspended(FreelancerOrgSuspendedEvent event) {
        log.warn("Enqueuing FreelancerOrgSuspendedEvent: orgId={}, reason={}",
                event.orgId(), event.reason());
        return enqueue(TOPIC_SUSPENDED, event.orgId(), event.tenantId(),
                event.occurredAt(), event);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishSubDelivererAssociated(SubDelivererAssociatedEvent event) {
        log.info("Enqueuing SubDelivererAssociatedEvent: orgId={}, subDeliverer={}",
                event.orgId(), event.subDelivererId());
        return enqueue(TntTopics.FREELANCER_ORG_SUB_DELIVERER_ASSOCIATED, event.orgId(),
                event.tenantId(), event.occurredAt(), event);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishSubDelivererRevoked(SubDelivererRevokedEvent event) {
        log.info("Enqueuing SubDelivererRevokedEvent: orgId={}, subDeliverer={}",
                event.orgId(), event.subDelivererId());
        return enqueue(TntTopics.FREELANCER_ORG_SUB_DELIVERER_REVOKED, event.orgId(),
                event.tenantId(), event.occurredAt(), event);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishKycLevelUpgraded(KycLevelUpgradedEvent event) {
        log.info("Enqueuing KycLevelUpgradedEvent: orgId={}, {} -> {}",
                event.orgId(), event.previousLevel(), event.newLevel());
        return enqueue(TOPIC_KYC_UPGRADED, event.orgId(), event.tenantId(),
                event.occurredAt(), event);
    }

    // ── Private helper ──────────────────────────────────────────────────────

    private Mono<Void> enqueue(String topic, UUID orgId, String tenantId,
                               Instant occurredAt, Object payload) {
        return Mono.fromCallable(() -> serialize(payload))
                .map(json -> DomainEventEnvelope.wrap()
                        .correlationId(UUID.randomUUID().toString())
                        .eventType(payload.getClass().getSimpleName())
                        .aggregateId(orgId.toString())
                        .aggregateType(AGGREGATE_TYPE)
                        .tenantId(tenantId)
                        .solutionCode(SOLUTION_CODE)
                        .payload(json)
                        .kafkaTopic(topic)
                        .occurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnError(ex -> log.error("Failed to enqueue {} to outbox for topic {}: {}",
                        payload.getClass().getSimpleName(), topic, ex.getMessage()));
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize event: " + event.getClass().getSimpleName(), e);
        }
    }
}
