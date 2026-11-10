package com.yowyob.tiibntick.core.billing.wallet.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletHoldUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka entry points of the wallet module after the Kernel wallet migration.
 *
 * <p>Only the incident → dispute hold remains functional. The three flows below cannot be
 * performed by the Core because the Kernel offers no server-to-server credit (every movement
 * needs the owner's challenge code — see docs/audits/remediation/kernel-wallet-migration.md,
 * request R1); they are refused <em>loudly</em> (ERROR with a greppable marker) instead of being
 * silently dropped, so operations can settle them by hand until the Kernel ships R1/R2:
 * <ul>
 *   <li>{@code COMMISSION_CREDIT_NOT_SUPPORTED} — commission earnings credit,</li>
 *   <li>{@code COMPENSATION_NOT_SUPPORTED} — dispute compensation payout (no
 *       {@code compensation.paid} event is emitted, so the dispute stays awaiting payment).</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletBillingEventConsumer {

    private static final String REFERENCE_TYPE_DELIVERY = "DELIVERY";

    private final IWalletHoldUseCase holds;
    @Qualifier("walletObjectMapper")
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = TntTopics.BILLING_WALLET_COMMISSION_CALCULATED,
            groupId = "tnt-billing-wallet-commission",
            containerFactory = "walletKafkaListenerContainerFactory"
    )
    public void onCommissionCalculated(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            log.error("COMMISSION_CREDIT_NOT_SUPPORTED mission={} invoice={} deliverer={} — the Kernel wallet has "
                            + "no server-to-server credit; the earnings credit must be settled manually "
                            + "(Kernel request R1)",
                    node.path("missionId").asText("?"), node.path("invoiceId").asText("?"),
                    node.path("delivererId").asText("?"));
        } catch (Exception e) {
            log.error("Failed to read CommissionCalculated event: {}", e.getMessage(), e);
        }
    }

    // ── Incident → Dispute escalation: hold the funds the beneficiary received ──

    @KafkaListener(
            topics = TntTopics.INCIDENT_ESCALATED_TO_DISPUTE,
            groupId = "tnt-billing-wallet-dispute-freeze",
            containerFactory = "walletKafkaListenerContainerFactory"
    )
    public void onIncidentEscalatedToDispute(String message) {
        log.info("IncidentEscalatedToDispute event received — placing payment hold");
        try {
            JsonNode node = objectMapper.readTree(message);
            String missionId = node.path("missionId").asText(null);
            String incidentId = node.path("incidentId").asText(null);
            if (missionId == null || missionId.isBlank()) {
                log.warn("IncidentEscalatedToDispute event missing missionId — skipping hold");
                return;
            }
            holds.holdSettlement(REFERENCE_TYPE_DELIVERY, UUID.fromString(missionId),
                            "DISPUTE_HOLD: incident=" + incidentId + " escalated to dispute")
                    .subscribe(
                            h -> log.info("Payment hold placed: mission={} incident={} hold={}",
                                    missionId, incidentId, h.getId()),
                            err -> log.error("Failed to place payment hold for mission={}: {}",
                                    missionId, err.getMessage(), err));
        } catch (Exception e) {
            log.error("Failed to process IncidentEscalatedToDispute event: {}", e.getMessage(), e);
        }
    }

    // ── Dispute → compensation: not supported without a Kernel server-to-server credit ──

    @KafkaListener(
            topics = TntTopics.BILLING_COMPENSATION_INITIATED,
            groupId = "tnt-billing-wallet-compensation",
            containerFactory = "walletKafkaListenerContainerFactory"
    )
    public void onCompensationInitiated(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            log.error("COMPENSATION_NOT_SUPPORTED dispute={} paymentRef={} beneficiary={} amount={} {} — the Kernel "
                            + "wallet cannot credit a user without their confirmation code (Kernel request R1/R6); "
                            + "compensation.paid is NOT emitted, settle manually",
                    node.path("disputeId").asText("?"), node.path("paymentRef").asText("?"),
                    node.path("beneficiaryId").asText("?"), node.path("amount").asText("?"),
                    node.path("currency").asText("?"));
        } catch (Exception e) {
            log.error("Failed to read CompensationInitiated event: {}", e.getMessage(), e);
        }
    }
}
