package com.yowyob.tiibntick.core.marketback.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMarketOrderUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer — reacts to delivery and payment events from
 * tnt-delivery-core / tnt-billing-wallet (published on shared topics declared
 * in tnt-bootstrap's {@code TntKafkaTopicsConfig}) to update MarketOrder state.
 *
 * <p>Topic names are the literal strings from {@code TntKafkaTopicsConfig}
 * ({@code tntMissionStatusChanged} / {@code tntPaymentConfirmed}) — this
 * module has no local {@code kafkaTopics} bean, unlike the original
 * standalone tiibntick-market-backend app.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketKafkaConsumer {

    private final IManageMarketOrderUseCase orderUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "tnt.delivery.mission.status-changed", groupId = "tnt-market-group")
    public void onMissionStatusChanged(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String status = node.get("newStatus").asText();
            UUID orderId = UUID.fromString(node.get("marketOrderId").asText());
            String tenantId = node.get("tenantId").asText();

            log.debug("Received mission status change: orderId={} status={}", orderId, status);
            switch (status) {
                case "DISPATCHED" -> orderUseCase.dispatchOrder(orderId, node.get("missionId").asText(), tenantId).subscribe();
                case "IN_TRANSIT" -> orderUseCase.markInTransit(orderId, tenantId).subscribe();
                case "DELIVERED"  -> orderUseCase.markDelivered(orderId, tenantId).subscribe();
                default -> log.warn("Unhandled mission status: {}", status);
            }
        } catch (Exception e) {
            log.error("Failed to process missionStatusChanged event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "tnt.billing.payment.confirmed", groupId = "tnt-market-group")
    public void onPaymentConfirmed(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            UUID orderId = UUID.fromString(node.get("orderId").asText());
            String tenantId = node.get("tenantId").asText();
            log.debug("Received payment confirmation for orderId={}", orderId);
            orderUseCase.confirmOrder(orderId, tenantId).subscribe();
        } catch (Exception e) {
            log.error("Failed to process paymentConfirmed event: {}", e.getMessage(), e);
        }
    }
}
