package com.yowyob.tiibntick.core.accounting.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.accounting.application.port.in.JournalEntryLineCommand;
import com.yowyob.tiibntick.core.accounting.application.port.in.PostJournalEntryCommand;
import com.yowyob.tiibntick.core.accounting.application.service.AccountingApplicationService;
import com.yowyob.tiibntick.core.accounting.domain.model.JournalType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Inbound Kafka consumer that listens to billing events and auto-generates
 * OHADA journal entries in tnt-accounting-core.
 *
 * Topics consumed:
 *   - tnt.billing.invoice.paid        → Debit 411000 (Client), Credit 704000 (Livraison revenue)
 *   - tnt.billing.wallet.commission.calculated → Debit 651000 (Commission charge), Credit 481000 (Commission payable)
 *   - tnt.billing.wallet.payment.confirmed   → Debit 521100/521200 (MoMo), Credit 411000 (Client)
 *
 * Author: MANFOUO Braun
 */
@Component
public class BillingEventAccountingConsumer {

    private static final String SYSTEM_USER = "SYSTEM_ACCOUNTING";
    private static final String DEFAULT_CURRENCY = "XAF";

    private final AccountingApplicationService accountingService;
    private final ObjectMapper objectMapper;

    public BillingEventAccountingConsumer(AccountingApplicationService accountingService,
                                          @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.accountingService = accountingService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TntTopics.BILLING_INVOICE_PAID, groupId = "tnt-accounting-core", containerFactory = "accountingKafkaListenerContainerFactory")
    public void onInvoicePaid(ConsumerRecord<String, String> record) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            UUID tenantId = UUID.fromString(node.get("tenantId").asText());
            UUID organizationId = UUID.fromString(node.get("organizationId").asText());
            String invoiceId = node.get("invoiceId").asText();
            BigDecimal amount = new BigDecimal(node.get("amount").asText());

            // Debit 411000 (Client AR), Credit 704000 (Revenue from delivery services)
            PostJournalEntryCommand cmd = new PostJournalEntryCommand(
                    tenantId, organizationId, JournalType.SALES,
                    "INVOICE", invoiceId,
                    List.of(
                            new JournalEntryLineCommand(null, "411000",
                                    "Client — facture " + invoiceId, amount, BigDecimal.ZERO, DEFAULT_CURRENCY),
                            new JournalEntryLineCommand(null, "704000",
                                    "Prestation livraison — facture " + invoiceId, BigDecimal.ZERO, amount, DEFAULT_CURRENCY)
                    ),
                    "Auto-entry: invoice paid " + invoiceId,
                    SYSTEM_USER
            );
            accountingService.postJournalEntry(cmd).subscribe();

        } catch (Exception e) {
            // Log and skip — idempotency handled via invoiceId referenceId deduplication
        }
    }

    @KafkaListener(topics = TntTopics.BILLING_WALLET_COMMISSION_CALCULATED, groupId = "tnt-accounting-core", containerFactory = "accountingKafkaListenerContainerFactory")
    public void onCommissionCalculated(ConsumerRecord<String, String> record) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            UUID tenantId = UUID.fromString(node.get("tenantId").asText());
            UUID organizationId = UUID.fromString(node.get("organizationId").asText());
            String missionId = node.get("missionId").asText();
            BigDecimal commission = new BigDecimal(node.get("commission").asText());

            // Debit 651000 (Commission expense), Credit 481000 (Commission payable to courier)
            PostJournalEntryCommand cmd = new PostJournalEntryCommand(
                    tenantId, organizationId, JournalType.COMMISSION,
                    "MISSION", missionId,
                    List.of(
                            new JournalEntryLineCommand(null, "651000",
                                    "Commission livreur — mission " + missionId, commission, BigDecimal.ZERO, DEFAULT_CURRENCY),
                            new JournalEntryLineCommand(null, "481000",
                                    "Commission à payer — mission " + missionId, BigDecimal.ZERO, commission, DEFAULT_CURRENCY)
                    ),
                    "Auto-entry: commission calculated for mission " + missionId,
                    SYSTEM_USER
            );
            accountingService.postJournalEntry(cmd).subscribe();

        } catch (Exception e) {
            // Log and skip
        }
    }

    @KafkaListener(topics = TntTopics.BILLING_WALLET_PAYMENT_CONFIRMED, groupId = "tnt-accounting-core", containerFactory = "accountingKafkaListenerContainerFactory")
    public void onPaymentConfirmed(ConsumerRecord<String, String> record) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            UUID tenantId = UUID.fromString(node.get("tenantId").asText());
            UUID organizationId = UUID.fromString(node.get("organizationId").asText());
            String paymentId = node.get("paymentId").asText();
            BigDecimal amount = new BigDecimal(node.get("amount").asText());
            String method = node.has("method") ? node.get("method").asText("MOMO_MTN") : "MOMO_MTN";

            // Select bank account based on payment method
            String bankCode = "ORANGE_MONEY".equalsIgnoreCase(method) ? "521200" : "521100";
            String bankLabel = "ORANGE_MONEY".equalsIgnoreCase(method)
                    ? "Orange Money" : "MTN MoMo";

            // Debit 521100/521200 (Mobile Money received), Credit 411000 (Client AR cleared)
            PostJournalEntryCommand cmd = new PostJournalEntryCommand(
                    tenantId, organizationId, JournalType.BANK,
                    "PAYMENT", paymentId,
                    List.of(
                            new JournalEntryLineCommand(null, bankCode,
                                    bankLabel + " — paiement " + paymentId, amount, BigDecimal.ZERO, DEFAULT_CURRENCY),
                            new JournalEntryLineCommand(null, "411000",
                                    "Règlement client — paiement " + paymentId, BigDecimal.ZERO, amount, DEFAULT_CURRENCY)
                    ),
                    "Auto-entry: payment confirmed " + paymentId,
                    SYSTEM_USER
            );
            accountingService.postJournalEntry(cmd).subscribe();

        } catch (Exception e) {
            // Log and skip
        }
    }
    // ── : FreelancerOrg billing events ─────────────────────────────────────

    /**
     * Handles mission assignment to a FreelancerOrg.
     * When a delivery is assigned to a FreelancerOrg, pre-records a suspense entry
     * on account 706-FRL-{orgId} (Revenue from services — pending confirmation).
     */
    @KafkaListener(topics = TntTopics.DELIVERY_FREELANCER_ORG_ASSIGNED, groupId = "tnt-accounting-core", containerFactory = "accountingKafkaListenerContainerFactory")
    public void onFreelancerOrgAssigned(ConsumerRecord<String, String> record) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            UUID tenantId = UUID.fromString(node.path("tenantId").asText(""));
            UUID organizationId = UUID.fromString(node.path("tenantId").asText("00000000-0000-0000-0000-000000000001"));
            String freelancerOrgId = node.path("freelancerOrgId").asText();
            String deliveryId = node.path("deliveryId").asText("");
            if (tenantId.toString().isBlank() || freelancerOrgId.isBlank()) return;

            // Derive short ID for account code lookup
            String shortId = freelancerOrgId.replace("-", "").substring(0, Math.min(8, freelancerOrgId.replace("-", "").length()));
            String revenueAccount = "706-FRL-" + shortId;
            String transitAccount = "471000"; // Compte de régularisation transitoire

            // Transitoire debit 471000, credit 706-FRL-{id} (revenue recognized on assignment)
            PostJournalEntryCommand cmd = new PostJournalEntryCommand(
                    tenantId, organizationId, JournalType.SALES,
                    "DELIVERY", deliveryId,
                    List.of(
                            new JournalEntryLineCommand(null, transitAccount,
                                    "Mission assignée FreelancerOrg — " + deliveryId,
                                    java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, DEFAULT_CURRENCY),
                            new JournalEntryLineCommand(null, revenueAccount,
                                    "Revenu prestation FreelancerOrg " + freelancerOrgId,
                                    java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO, DEFAULT_CURRENCY)
                    ),
                    "Auto-entry: FreelancerOrg assigned to delivery " + deliveryId,
                    SYSTEM_USER
            );
            // Note: Amount=1 is symbolic; real amounts come from tnt.billing.wallet.split_executed
            accountingService.postJournalEntry(cmd)
                    .onErrorResume(e -> reactor.core.publisher.Mono.empty())
                    .subscribe();
        } catch (Exception e) {
            // Log and skip — best-effort accounting entry
        }
    }

    /**
     * Handles wallet payment split execution for FreelancerOrg revenue distribution.
     * Records the actual split amounts:
     * - Debit 411-FRL-{orgId} (Client AR), Credit 706-FRL-{orgId} (Revenue)
     * - Debit 706-FRL-{orgId}, Credit 421-FRL-{orgId} (Sub-deliverer payable)
     */
    @KafkaListener(topics = TntTopics.BILLING_WALLET_SPLIT_EXECUTED, groupId = "tnt-accounting-core", containerFactory = "accountingKafkaListenerContainerFactory")
    public void onWalletSplitExecuted(ConsumerRecord<String, String> record) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            UUID tenantId = UUID.fromString(node.path("tenantId").asText("00000000-0000-0000-0000-000000000001"));
            UUID organizationId = UUID.fromString(node.path("tenantId").asText("00000000-0000-0000-0000-000000000001"));
            String freelancerOrgId = node.path("freelancerOrgId").asText();
            String missionId = node.path("missionId").asText("");
            java.math.BigDecimal orgRevenue = new java.math.BigDecimal(node.path("orgRevenue").asText("0"));
            java.math.BigDecimal subDelivererComm = new java.math.BigDecimal(node.path("subDelivererCommission").asText("0"));

            if (freelancerOrgId.isBlank() || orgRevenue.signum() == 0) return;

            String shortId = freelancerOrgId.replace("-", "").substring(0, Math.min(8, freelancerOrgId.replace("-", "").length()));
            String clientAccount    = "411-FRL-" + shortId;
            String revenueAccount   = "706-FRL-" + shortId;
            String subPayAccount    = "421-FRL-" + shortId;
            //String transitAccount   = "471000";

            List<JournalEntryLineCommand> lines = new java.util.ArrayList<>(List.of(
                    new JournalEntryLineCommand(null, clientAccount,
                            "Règlement client mission " + missionId, orgRevenue, java.math.BigDecimal.ZERO, DEFAULT_CURRENCY),
                    new JournalEntryLineCommand(null, revenueAccount,
                            "Produit prestation FreelancerOrg mission " + missionId, java.math.BigDecimal.ZERO, orgRevenue, DEFAULT_CURRENCY)
            ));

            // If sub-deliverer commission, add payable entry
            if (subDelivererComm.signum() > 0) {
                lines.add(new JournalEntryLineCommand(null, revenueAccount,
                        "Quote-part commission sub-livreur mission " + missionId, subDelivererComm, java.math.BigDecimal.ZERO, DEFAULT_CURRENCY));
                lines.add(new JournalEntryLineCommand(null, subPayAccount,
                        "Commission à payer sub-livreur mission " + missionId, java.math.BigDecimal.ZERO, subDelivererComm, DEFAULT_CURRENCY));
            }

            PostJournalEntryCommand cmd = new PostJournalEntryCommand(
                    tenantId, organizationId, JournalType.SALES,
                    "WALLET_SPLIT", missionId,
                    lines,
                    "Auto-entry: wallet split FreelancerOrg " + freelancerOrgId + " mission " + missionId,
                    SYSTEM_USER
            );
            accountingService.postJournalEntry(cmd)
                    .onErrorResume(e -> reactor.core.publisher.Mono.empty())
                    .subscribe();
        } catch (Exception e) {
            // Log and skip
        }
    }

}