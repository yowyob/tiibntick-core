package com.yowyob.tiibntick.core.agency.commission.adapter.in.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.agency.commission.application.service.CommissionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes wallet payment confirmations to mark an agency commission as paid once the
 * underlying wallet payout succeeds.
 *
 * <p>Audit n°5 P-01, resolved 2026-07-23: this used to listen on {@code tnt.billing.wallet.events},
 * a topic nobody in the repo ever produced. {@code WalletCoreClient} smuggles the agency-side
 * {@code commissionId} into the wallet payment request's {@code invoiceId} field, and
 * {@code PaymentConfirmed} (tnt-billing-wallet) echoes it back unchanged, so this consumer listens on
 * {@link TntTopics#BILLING_WALLET_PAYMENT_CONFIRMED} and reads the commission id back out of it.
 *
 * <p>Lot L7 (spec §3.10): the work is awaited on the container thread ({@link ReactiveListenerSupport}) and the
 * record acknowledged only once it succeeded; any failure (unreadable record, database error) reaches the
 * container: retries, then {@code tnt.dlq}. A payment that is not an agency commission (no or unknown
 * {@code invoiceId}) is acknowledged without effect.
 */
@Component
@ConditionalOnProperty(name = "tnt.agency.kafka.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class WalletPayoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(WalletPayoutConsumer.class);

    private final CommissionService commissionService;
    private final ObjectMapper objectMapper;
    private final ReactiveListenerSupport listenerSupport;

    public WalletPayoutConsumer(CommissionService commissionService, ObjectMapper objectMapper,
                                ReactiveListenerSupport listenerSupport) {
        this.commissionService = commissionService;
        this.objectMapper = objectMapper;
        this.listenerSupport = listenerSupport;
    }

    @KafkaListener(
            topics = "${tnt.kafka.topics.consumed.core-wallet}",
            groupId = "${tnt.kafka.topics.groups.agency-erp-billing:tnt-agency-erp-billing-consumer}",
            containerFactory = "agencyCommissionKafkaListenerFactory"
    )
    public void onWalletEvent(ConsumerRecord<String, String> record, Acknowledgment ack) throws IOException {
        Map<String, Object> payload = objectMapper.readValue(record.value(), new TypeReference<>() {});
        Object commissionIdValue = payload.get("invoiceId");
        Object tenantIdValue = payload.get("tenantId");
        if (!(commissionIdValue instanceof String commissionIdStr) || commissionIdStr.isBlank()
                || !(tenantIdValue instanceof String tenantIdStr) || tenantIdStr.isBlank()) {
            ack.acknowledge();
            return;
        }
        UUID commissionId;
        try {
            commissionId = UUID.fromString(commissionIdStr);
        } catch (IllegalArgumentException notACommission) {
            // invoiceId of another kind of payment (not a UUID): not ours
            ack.acknowledge();
            return;
        }
        UUID tenantId = UUID.fromString(tenantIdStr);
        listenerSupport.await(commissionService.confirmWalletPayment(tenantId, commissionId));
        log.info("[WalletPayoutConsumer] wallet confirmation handled commissionId={}", commissionId);
        ack.acknowledge();
    }
}
