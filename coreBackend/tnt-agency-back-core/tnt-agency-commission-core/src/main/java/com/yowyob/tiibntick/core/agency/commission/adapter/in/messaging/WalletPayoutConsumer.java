package com.yowyob.tiibntick.core.agency.commission.adapter.in.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.agency.commission.application.service.CommissionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "tnt.agency.kafka.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class WalletPayoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(WalletPayoutConsumer.class);

    private final CommissionService commissionService;
    private final ObjectMapper objectMapper;

    public WalletPayoutConsumer(CommissionService commissionService, ObjectMapper objectMapper) {
        this.commissionService = commissionService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${tnt.kafka.topics.consumed.core-wallet}",
            groupId = "${tnt.kafka.topics.groups.agency-erp-billing:tnt-agency-erp-billing-consumer}",
            containerFactory = "agencyCommissionKafkaListenerFactory"
    )
    public void onWalletEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            Map<String, Object> payload = objectMapper.readValue(record.value(), new TypeReference<>() {});
            if (!"PAYMENT_SUCCESS".equals(payload.get("eventType"))) {
                ack.acknowledge();
                return;
            }
            String commissionIdStr = (String) payload.get("commissionId");
            String tenantIdStr = (String) payload.get("tenantId");
            if (commissionIdStr == null || tenantIdStr == null) {
                ack.acknowledge();
                return;
            }
            UUID commissionId = UUID.fromString(commissionIdStr);
            UUID tenantId = UUID.fromString(tenantIdStr);
            commissionService.confirmWalletPayment(tenantId, commissionId)
                    .doOnSuccess(v -> log.info("[WalletPayoutConsumer] commission confirmed commissionId={}",
                            commissionId))
                    .doOnError(e -> log.warn("[WalletPayoutConsumer] failed commissionId={}: {}",
                            commissionId, e.getMessage()))
                    .onErrorResume(e -> Mono.empty())
                    .doFinally(s -> ack.acknowledge())
                    .subscribe();
        } catch (Exception e) {
            log.warn("[WalletPayoutConsumer] parse error: {}", e.getMessage());
            ack.acknowledge();
        }
    }
}
