package com.yowyob.tiibntick.core.billing.wallet.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


/**
 * Kafka entry points of the wallet module after the Kernel wallet migration.
 *
 * <p>The commission earnings credit cannot be performed by the Core (no server-to-server credit in the Kernel, see
 * docs/audits/remediation/kernel-wallet-migration.md, request R1): it is refused <em>loudly</em> with the marker
 * {@code COMMISSION_CREDIT_NOT_SUPPORTED}. Dispute compensations are paid synchronously by the Kernel compensation
 * fund since lot L5 ({@code CompensationService}); {@code tnt.billing.compensation.initiated} is gone. The escrow of a
 * delivery under dispute is frozen from {@code tnt.dispute.events} ({@code WalletPaymentLifecycleConsumer}), no longer
 * from {@code tnt.incident.escalated.to.dispute} with a local hold.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletBillingEventConsumer {

    @Qualifier("walletObjectMapper")
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = TntTopics.BILLING_WALLET_COMMISSION_CALCULATED,
            groupId = "tnt-billing-wallet-commission",
            containerFactory = "walletKafkaListenerContainerFactory"
    )
    public void onCommissionCalculated(String message) throws com.fasterxml.jackson.core.JsonProcessingException {
        // Lot L7 (spec §3.10): an unreadable record is no longer logged and dropped; it reaches the container
        // (retries, then tnt.dlq).
        JsonNode node = objectMapper.readTree(message);
        if ("PLATFORM_FEE".equals(node.path("commissionType").asText())) {
            // Published by the Core's own SettlementService (R1, 2026-11-26): a platform commission
            // owed BY the payee, tracked as DUE — there is nobody to credit, so nothing to refuse.
            log.info("Platform commission due: settlement={} reference={} {} commission={} {}",
                    node.path("settlementId").asText("?"), node.path("referenceType").asText("?"),
                    node.path("referenceId").asText("?"), node.path("commission").asText("?"),
                    node.path("currency").asText("?"));
            return;
        }
        log.error("COMMISSION_CREDIT_NOT_SUPPORTED mission={} invoice={} deliverer={} — the Kernel wallet has "
                        + "no server-to-server credit; the earnings credit must be settled manually "
                        + "(Kernel request R1)",
                node.path("missionId").asText("?"), node.path("invoiceId").asText("?"),
                node.path("delivererId").asText("?"));
    }
}
