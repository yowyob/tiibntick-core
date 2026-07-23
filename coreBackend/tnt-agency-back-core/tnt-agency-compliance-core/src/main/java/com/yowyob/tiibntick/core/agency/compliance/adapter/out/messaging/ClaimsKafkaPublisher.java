package com.yowyob.tiibntick.core.agency.compliance.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.agency.compliance.application.port.out.ClaimEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes client claim notifications to {@code tnt.agency.claims.submitted}.
 *
 * <p>Chantier C · Audit n°3 · P5 migration (see
 * {@code docs/audits/remediation/chantier-c-p5-inventory.md}): delegates to
 * {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of sending to
 * Kafka directly via a shared {@code KafkaTemplate}. The envelope is persisted durably and
 * {@code OutboxPollerService} relays it to Kafka asynchronously with retry/DLQ — a claim
 * submission can no longer report success while its notification event is silently lost.
 *
 * <p>The Kafka wire format is unchanged: the exact same flat
 * {@code {claimRef, tenantId, agencyId, missionId, claimType, description, contactEmail}} JSON
 * is still the message body, so existing consumers require no change.
 *
 * <p>Unlike the typical migration pattern (business save + event publish wrapped in a single
 * {@code @Transactional} boundary), {@link com.yowyob.tiibntick.core.agency.compliance.application.service.ClaimSubmissionService}
 * has no local aggregate to persist — the claim is stored on the platform dispute-core (a remote
 * Kernel call via {@code DisputeCorePort}), and the outbox envelope insert here is this module's
 * only local database write. A single insert is already atomic, so no additional
 * {@code @Transactional} boundary is needed at the call site.
 */
@Component
public class ClaimsKafkaPublisher implements ClaimEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(ClaimsKafkaPublisher.class);

    private static final String AGGREGATE_TYPE = "Claim";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;
    private final String claimsTopic;

    public ClaimsKafkaPublisher(
            PublishEventUseCase publishEventUseCase,
            ObjectMapper objectMapper,
            @Value("${tnt.kafka.topics.produced.claims:tnt.agency.claims.submitted}") String claimsTopic) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
        this.claimsTopic = claimsTopic;
    }

    @Override
    public Mono<String> submitClaim(ClaimSubmission submission) {
        String claimRef = "CLM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return Mono.fromCallable(() -> serialize(submission, claimRef))
                .map(payload -> toEnvelope(submission, claimRef, payload))
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.debug("[Claims] enqueued claim={} agency={} to outbox",
                        claimRef, submission.agencyId()))
                .doOnError(e -> log.error("[Claims] failed to enqueue claim={} agency={} to outbox: {}",
                        claimRef, submission.agencyId(), e.getMessage()))
                .thenReturn(claimRef);
    }

    // ── Private helpers ───────────────────────────────────────────────

    private String serialize(ClaimSubmission submission, String claimRef) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("claimRef", claimRef);
        payload.put("tenantId", submission.tenantId().toString());
        payload.put("agencyId", submission.agencyId().toString());
        payload.put("missionId", submission.missionId() != null
                ? submission.missionId().toString() : null);
        payload.put("claimType", submission.claimType());
        payload.put("description", submission.description());
        payload.put("contactEmail", submission.contactEmail());
        return objectMapper.writeValueAsString(payload);
    }

    private DomainEventEnvelope toEnvelope(ClaimSubmission submission, String claimRef, String payload) {
        return DomainEventEnvelope.wrap()
                .correlationId(UUID.randomUUID().toString())
                .eventType("ClaimSubmitted")
                .aggregateId(claimRef)
                .aggregateType(AGGREGATE_TYPE)
                .tenantId(submission.tenantId().toString())
                .solutionCode(SOLUTION_CODE)
                .payload(payload)
                .kafkaTopic(claimsTopic)
                .kafkaPartitionKey(submission.tenantId().toString())
                .build();
    }
}
