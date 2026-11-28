package com.yowyob.tiibntick.core.billing.wallet.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ISettlementEventPublisher;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@link ISettlementEventPublisher} over the yow-event-kernel outbox ({@link PublishEventUseCase}).
 *
 * <p>The payloads are flat JSON objects in the shape the existing consumers read at the root:
 * <ul>
 *   <li>{@code payment.confirmed} — accounting ({@code tenantId}, {@code organizationId},
 *       {@code paymentId}, {@code amount}, {@code method}), invoice ({@code paymentRef}; no
 *       {@code invoiceId}: the settlement does not know the invoice, which its caller issues
 *       already paid), market (no {@code orderId}: a market order is PAID after it was
 *       confirmed, the consumer's confirm step does not apply), agency payout (no
 *       {@code invoiceId}, which it would read as an agency commission id);</li>
 *   <li>{@code commission.calculated} — accounting ({@code tenantId}, {@code organizationId},
 *       {@code missionId}, {@code commission}), wallet ({@code commissionType = PLATFORM_FEE}).</li>
 * </ul>
 * {@code organizationId} is the tenant id (the settlement carries no organisation — same
 * convention as the accounting consumer's freelancer-org handler). Amounts are decimal strings.
 * Every payload carries a fresh {@code eventId}; consumers deduplicate on the envelope id header.
 */
@Component
public class SettlementOutboxEventPublisher implements ISettlementEventPublisher {

    static final String AGGREGATE_TYPE = "WalletSettlement";
    static final String SOLUTION_CODE = "TNT";
    static final String SOURCE = "CORE_SETTLEMENT";
    static final String COMMISSION_TYPE_PLATFORM_FEE = "PLATFORM_FEE";
    static final String REFERENCE_TYPE_DELIVERY = "DELIVERY";

    private final PublishEventUseCase publishEvent;
    private final ObjectMapper objectMapper;

    public SettlementOutboxEventPublisher(PublishEventUseCase publishEvent,
                                          @Qualifier("walletObjectMapper") ObjectMapper objectMapper) {
        this.publishEvent = publishEvent;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> paymentConfirmed(WalletSettlement s) {
        Map<String, Object> payload = common("PaymentConfirmed", s);
        payload.put("paymentId", s.getId().toString());
        payload.put("paymentRef", s.getKernelReference() != null ? s.getKernelReference() : s.getId().toString());
        payload.put("payerUserId", str(s.getPayerUserId()));
        payload.put("payeeUserId", str(s.getPayeeUserId()));
        payload.put("amount", s.getAmount().toPlainString());
        payload.put("currency", s.getCurrency());
        payload.put("method", s.getMethod() != null ? s.getMethod().name() : null);
        payload.put("paidAt", s.getPaidAt() != null ? s.getPaidAt().toString() : null);
        return publish(TntTopics.BILLING_WALLET_PAYMENT_CONFIRMED, "PaymentConfirmed", s, payload);
    }

    @Override
    public Mono<Void> commissionCalculated(WalletSettlement s) {
        Map<String, Object> payload = common("CommissionCalculated", s);
        payload.put("commissionType", COMMISSION_TYPE_PLATFORM_FEE);
        payload.put("missionId", s.getReferenceId().toString());
        payload.put("delivererId", str(s.getPayeeUserId()));
        payload.put("payeeUserId", str(s.getPayeeUserId()));
        payload.put("commission", s.getCommissionAmount().toPlainString());
        payload.put("commissionRate", s.getCommissionRate() != null ? s.getCommissionRate().toPlainString() : null);
        payload.put("amount", s.getAmount().toPlainString());
        payload.put("currency", s.getCurrency());
        payload.put("commissionStatus", s.getCommissionStatus().name());
        return publish(TntTopics.BILLING_WALLET_COMMISSION_CALCULATED, "CommissionCalculated", s, payload);
    }

    private Map<String, Object> common(String eventType, WalletSettlement s) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", eventType);
        payload.put("source", SOURCE);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("tenantId", s.getTenantId().toString());
        payload.put("organizationId", s.getTenantId().toString());
        payload.put("settlementId", s.getId().toString());
        payload.put("referenceType", s.getReferenceType());
        payload.put("referenceId", s.getReferenceId().toString());
        if (REFERENCE_TYPE_DELIVERY.equals(s.getReferenceType())) {
            payload.put("missionId", s.getReferenceId().toString());
        }
        return payload;
    }

    private Mono<Void> publish(String topic, String eventType, WalletSettlement s, Map<String, Object> payload) {
        return Mono.fromCallable(() -> serialize(payload))
                .map(json -> DomainEventEnvelope.wrap()
                        .correlationId(s.getId().toString())
                        .eventType(eventType)
                        .aggregateId(s.getId().toString())
                        .aggregateType(AGGREGATE_TYPE)
                        .tenantId(s.getTenantId().toString())
                        .solutionCode(SOLUTION_CODE)
                        .payload(json)
                        .kafkaTopic(topic)
                        .kafkaPartitionKey(s.getReferenceId().toString())
                        .occurredAt(LocalDateTime.now())
                        .build())
                .flatMap(publishEvent::publish);
    }

    private String serialize(Map<String, Object> payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(payload);
    }

    private static String str(UUID id) {
        return id != null ? id.toString() : null;
    }
}
