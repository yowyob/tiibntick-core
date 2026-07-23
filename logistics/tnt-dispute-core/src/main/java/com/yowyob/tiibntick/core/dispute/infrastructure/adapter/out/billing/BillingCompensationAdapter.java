package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IBillingCompensationPort;
import com.yowyob.tiibntick.core.dispute.domain.model.CompensationDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox-backed adapter implementing {@link IBillingCompensationPort}.
 *
 * <p>Chantier C · Audit n°3 · P5 (see {@code docs/audits/remediation/chantier-c-p5-inventory.md}):
 * delegates to {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of
 * sending to Kafka directly via {@code KafkaTemplate}. Called only from
 * {@code DisputeCommandService#processCompensation}, which is already {@code @Transactional}, so
 * the DB write and the outbox insert commit atomically.
 *
 * <p>The Kafka wire format is unchanged: same payload shape, same topic, same partition key
 * (disputeId) — only the transport changed.
 *
 * @author MANFOUO Braun
 */
@Component
public class BillingCompensationAdapter implements IBillingCompensationPort {

    private static final Logger log = LoggerFactory.getLogger(BillingCompensationAdapter.class);
    private static final String TOPIC_COMPENSATION_INITIATED = "tnt.billing.compensation.initiated";
    private static final String AGGREGATE_TYPE = "Dispute";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public BillingCompensationAdapter(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("disputeObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<String> initiateCompensationPayment(String disputeId, String tenantId, CompensationDetails details) {
        String paymentRef = "COMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Map<String, Object> payload = Map.of(
                "paymentRef", paymentRef,
                "disputeId", disputeId,
                "tenantId", tenantId,
                "amount", details.getAmount(),
                "currency", details.getCurrency(),
                "method", details.getMethod().name(),
                "beneficiaryId", details.getBeneficiaryId(),
                "requestedAt", LocalDateTime.now().toString());
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .map(json -> DomainEventEnvelope.wrap()
                        .correlationId(UUID.randomUUID().toString())
                        .eventType(TOPIC_COMPENSATION_INITIATED)
                        .aggregateId(disputeId)
                        .aggregateType(AGGREGATE_TYPE)
                        .tenantId(tenantId)
                        .solutionCode(SOLUTION_CODE)
                        .payload(json)
                        .kafkaTopic(TOPIC_COMPENSATION_INITIATED)
                        .kafkaPartitionKey(disputeId)
                        .occurredAt(LocalDateTime.now(ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.info("Compensation payment initiated ref={} disputeId={} amount={}",
                        paymentRef, disputeId, details.formattedAmount()))
                .doOnError(e -> log.error("Failed to initiate compensation for disputeId={}: {}", disputeId, e.getMessage()))
                .thenReturn(paymentRef);
    }
}
