package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IBillingCompensationPort;
import com.yowyob.tiibntick.core.dispute.domain.model.CompensationDetails;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class BillingCompensationAdapter implements IBillingCompensationPort {

    private static final Logger log = LoggerFactory.getLogger(BillingCompensationAdapter.class);
    private static final String TOPIC_COMPENSATION_INITIATED = "tnt.billing.compensation.initiated";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public BillingCompensationAdapter(
            @Qualifier("disputeKafkaProducerTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("disputeObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
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
                .flatMap(json -> Mono.fromFuture(
                        kafkaTemplate.send(new ProducerRecord<>(TOPIC_COMPENSATION_INITIATED, disputeId, json))))
                .doOnSuccess(v -> log.info("Compensation payment initiated ref={} disputeId={} amount={}",
                        paymentRef, disputeId, details.formattedAmount()))
                .doOnError(e -> log.error("Failed to initiate compensation for disputeId={}: {}", disputeId, e.getMessage()))
                .thenReturn(paymentRef);
    }
}