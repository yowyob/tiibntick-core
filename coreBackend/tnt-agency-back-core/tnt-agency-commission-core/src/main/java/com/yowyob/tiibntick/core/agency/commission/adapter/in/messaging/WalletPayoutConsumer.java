package com.yowyob.tiibntick.core.agency.commission.adapter.in.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
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

/**
 * Consumes wallet payment confirmations to mark an agency commission as paid once the
 * underlying wallet payout succeeds.
 *
 * <p>Audit n°5 P-01, resolved 2026-07-23: this used to listen on {@code tnt.billing.wallet.events},
 * a topic nobody in the repo ever produced — wallet only ever publishes 6 unitary event topics,
 * and none of them carry a {@code commissionId} field, so even fixing the topic name alone would
 * not have been enough. {@code WalletCoreClient} (this module's outbound port) already smuggles
 * the agency-side {@code commissionId} into the wallet payment request's {@code invoiceId} field
 * ({@code WalletCoreClient.java}, {@code "invoiceId", request.commissionId().toString()}) — the
 * wallet module has no concept of "commission" of its own. {@code PaymentConfirmed}
 * (tnt-billing-wallet) echoes that same {@code invoiceId} back unchanged, so this consumer now
 * listens on the real {@link TntTopics#BILLING_WALLET_PAYMENT_CONFIRMED} topic and reads the
 * commission id back out of it, instead of waiting for a payload shape that never existed.
 */
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
            String commissionIdStr = (String) payload.get("invoiceId");
            String tenantIdStr = (String) payload.get("tenantId");
            if (commissionIdStr == null || commissionIdStr.isBlank() || tenantIdStr == null) {
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
