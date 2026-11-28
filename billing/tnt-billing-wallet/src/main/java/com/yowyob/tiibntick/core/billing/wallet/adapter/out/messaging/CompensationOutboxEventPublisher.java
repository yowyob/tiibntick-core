package com.yowyob.tiibntick.core.billing.wallet.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ICompensationEventPublisher;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletCompensation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@code tnt.billing.compensation.paid} through the outbox (spec §4.3, lot L5): {@code disputeId},
 * {@code compensationId}, {@code beneficiaryActorId}, {@code amount}, {@code currency}, {@code fundWalletId}, plus
 * {@code tenantId} and {@code paymentReference} (the Kernel compensation id) read by the dispute consumer. Key: the
 * dispute id.
 */
@Component
public class CompensationOutboxEventPublisher implements ICompensationEventPublisher {

    static final String EVENT_TYPE = "CompensationPaid";
    static final String AGGREGATE_TYPE = "WalletCompensation";

    private final PublishEventUseCase publishEvent;
    private final ObjectMapper objectMapper;

    public CompensationOutboxEventPublisher(PublishEventUseCase publishEvent,
                                            @Qualifier("walletObjectMapper") ObjectMapper objectMapper) {
        this.publishEvent = publishEvent;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> compensationPaid(WalletCompensation c) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", EVENT_TYPE);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("tenantId", c.tenantId().toString());
        boolean ret = c.disputeId() != null && c.disputeId().startsWith(WalletCompensation.RETURN_PREFIX);
        boolean replacement = c.disputeId() != null
                && c.disputeId().startsWith(WalletCompensation.REPLACEMENT_PREFIX);
        // lot L6: DISPUTE_COMPENSATION (a dispute's indemnity) or RETURN_COST (a return paid by the fund, QO-22);
        // QO-11 / D-153: COURIER_REPLACEMENT (a replacement courier paid at the prorata of the distance)
        payload.put("reason", ret ? "RETURN_COST" : replacement ? "COURIER_REPLACEMENT" : "DISPUTE_COMPENSATION");
        payload.put("disputeId", ret || replacement ? null : c.disputeId());
        String subject = ret ? c.disputeId().substring(WalletCompensation.RETURN_PREFIX.length())
                : replacement ? c.disputeId().substring(WalletCompensation.REPLACEMENT_PREFIX.length(),
                WalletCompensation.REPLACEMENT_PREFIX.length() + 36)
                : c.disputeId();
        payload.put("returnDeliveryId", ret ? subject : null);
        payload.put("deliveryId", ret || replacement ? subject : null);
        payload.put("compensationId", c.kernelCompensationId().toString());
        payload.put("paymentReference", c.kernelCompensationId().toString());
        payload.put("beneficiaryActorId", c.beneficiaryActorId().toString());
        payload.put("beneficiaryWalletId", c.beneficiaryWalletId().toString());
        payload.put("amount", c.amount().toPlainString());
        payload.put("currency", c.currency());
        payload.put("fundWalletId", c.fundWalletId() != null ? c.fundWalletId().toString() : null);
        return Mono.fromCallable(() -> serialize(payload))
                .map(json -> DomainEventEnvelope.wrap()
                        // the dispute id, or the return's delivery id (the envelope column holds 36 characters)
                        .correlationId(subject)
                        .eventType(EVENT_TYPE)
                        .aggregateId(c.id().toString())
                        .aggregateType(AGGREGATE_TYPE)
                        .tenantId(c.tenantId().toString())
                        .solutionCode("TNT")
                        .payload(json)
                        .kafkaTopic(TntTopics.BILLING_COMPENSATION_PAID)
                        .kafkaPartitionKey(subject)
                        .occurredAt(LocalDateTime.now())
                        .build())
                .flatMap(publishEvent::publish);
    }

    private String serialize(Map<String, Object> payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(payload);
    }
}
