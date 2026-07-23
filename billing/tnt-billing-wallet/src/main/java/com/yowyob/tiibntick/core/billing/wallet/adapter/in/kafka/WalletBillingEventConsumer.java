package com.yowyob.tiibntick.core.billing.wallet.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.CreditCommissionCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.CreditWalletCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IPaymentIntentRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.WalletStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * BillingEventConsumer — consumes Kafka events relevant to wallet operations.
 *
 * <p>Listens to:</p>
 * <ul>
 *   <li>{@code tnt.billing.wallet.commission.calculated} — credits deliverer commission
 *       to their in-app wallet after a confirmed invoice payment.</li>
 *   <li>{@code tnt.incident.escalated.to.dispute} — permanently freezes the payment
 *       linked to the affected mission until the dispute is resolved by tnt-dispute-core.</li>
 *   <li>{@code tnt.billing.compensation.initiated} — pays out a {@code WALLET_CREDIT}
 *       dispute compensation and confirms back on {@code tnt.billing.compensation.paid}.</li>
 * </ul>
 *
 * <p>The {@code tnt.incident.escalated.to.dispute} handler implements the billing side
 * of the incident → dispute escalation flow. When an incident transitions to a formal
 * dispute (fraud confirmed, package lost, damage validated), this consumer applies a
 * definitive payment freeze so that neither the client's wallet nor the deliverer's
 * commission can be released until the dispute outcome is known.</p>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletBillingEventConsumer {

    private static final String COMPENSATION_AGGREGATE_TYPE = "Dispute";
    private static final String COMPENSATION_SOLUTION_CODE = "TNT";

    private final IWalletUseCase walletUseCase;
    private final IPaymentIntentRepository paymentIntentRepository;
    private final IWalletRepository walletRepository;
    private final PublishEventUseCase publishEventUseCase;
    @Qualifier("walletObjectMapper")
    private final ObjectMapper objectMapper;

    // ── Commission credit ─────────────────────────────────────────────────────

    /**
     * Consumes {@code CommissionCalculated} events and credits the deliverer's wallet.
     *
     * <p>Triggered when tnt-billing-invoice emits a commission event after a successful
     * invoice payment. The commission is credited immediately as an in-app wallet balance.</p>
     *
     * @param message the raw Kafka message (JSON)
     */
    @KafkaListener(
            topics = TntTopics.BILLING_WALLET_COMMISSION_CALCULATED,
            groupId = "tnt-billing-wallet-commission",
            containerFactory = "walletKafkaListenerContainerFactory"
    )
    public void onCommissionCalculated(String message) {
        log.info("CommissionCalculated event received");
        try {
            JsonNode node = objectMapper.readTree(message);
            UUID delivererId = UUID.fromString(node.get("delivererId").asText());
            UUID tenantId    = UUID.fromString(node.get("tenantId").asText());
            String missionId  = node.get("missionId").asText();
            String invoiceId  = node.get("invoiceId").asText();
            BigDecimal amount = new BigDecimal(node.get("commissionAmount").get("amount").asText());
            String currency   = node.get("commissionAmount").get("currency").get("currencyCode").asText();

            walletUseCase.creditCommission(new CreditCommissionCommand(
                    delivererId, tenantId,
                    Money.of(amount, currency),
                    missionId, invoiceId))
                    .subscribe(
                            tx  -> log.info("Commission credited: deliverer={} amount={} {}", delivererId, amount, currency),
                            err -> log.error("Failed to credit commission for deliverer={}: {}",
                                    delivererId, err.getMessage())
                    );
        } catch (Exception e) {
            log.error("Failed to process CommissionCalculated event: {}", e.getMessage(), e);
        }
    }

    // ── Incident → Dispute escalation: definitive payment freeze ─────────────

    /**
     * Consumes {@code tnt.incident.escalated.to.dispute} events and applies a
     * <b>definitive payment freeze</b> on the mission's wallet.
     *
     * <p>When an incident escalates to a formal dispute (e.g. the incident engine
     * determines the situation involves confirmed fraud, package loss, or serious damage),
     * the billing module must lock the funds until tnt-dispute-core rules on the outcome.</p>
     *
     * <p>Processing logic:</p>
     * <ol>
     *   <li>Extract {@code missionId}, {@code incidentId}, {@code fraudReason} from payload.</li>
     *   <li>Find the PaymentIntent linked to this mission's invoice.</li>
     *   <li>Find the associated Wallet and set its status to {@code FROZEN}.</li>
     *   <li>Persist the updated wallet with a freeze audit reason.</li>
     * </ol>
     *
     * <p>This freeze is <em>definitive</em> (unlike the temporary freeze applied when
     * the incident is first reported). It will only be lifted by tnt-dispute-core
     * when the dispute is closed and a resolution is reached.</p>
     *
     * @param message the raw Kafka message (JSON) from tnt-incident-core
     */
    @KafkaListener(
            topics = TntTopics.INCIDENT_ESCALATED_TO_DISPUTE,
            groupId = "tnt-billing-wallet-dispute-freeze",
            containerFactory = "walletKafkaListenerContainerFactory"
    )
    public void onIncidentEscalatedToDispute(String message) {
        log.info("IncidentEscalatedToDispute event received — applying definitive payment freeze");
        try {
            JsonNode node = objectMapper.readTree(message);

            String missionId  = node.path("missionId").asText(null);
            String incidentId = node.path("incidentId").asText(null);
            //String fraudReason = node.path("fraudReason").asText("Incident escalated to dispute");

            if (missionId == null || missionId.isBlank()) {
                log.warn("IncidentEscalatedToDispute event missing missionId — skipping freeze");
                return;
            }

            //String freezeReason = String.format(
            //        "DEFINITIVE_FREEZE: incident=%s escalated to dispute. Reason: %s",
            //        incidentId, fraudReason);

            log.info("Applying definitive freeze for mission={} incidentId={}", missionId, incidentId);

            // Find the PaymentIntent by invoice/mission ID then freeze the associated wallet
            paymentIntentRepository.findByInvoiceId(missionId)
                    .flatMap(intent -> walletRepository.findById(intent.getWalletId())
                            .flatMap(wallet -> {
                                if (wallet.getStatus() == WalletStatus.FROZEN) {
                                    log.debug("Wallet {} already frozen — updating freeze reason", wallet.getId());
                                }
                                // Apply / refresh definitive freeze
                                wallet.freeze();
                                return walletRepository.save(wallet);
                            })
                            .doOnSuccess(w -> log.info(
                                    "Definitive freeze applied: wallet={} mission={} incident={}",
                                    w.getId(), missionId, incidentId))
                    )
                    .switchIfEmpty(
                            reactor.core.publisher.Mono.fromRunnable(() ->
                                    log.warn("No PaymentIntent for missionId={} — cannot apply definitive freeze",
                                            missionId))
                    )
                    .subscribe(
                            ok  -> log.debug("Definitive freeze subscription completed for mission={}", missionId),
                            err -> log.error("Failed to apply definitive freeze for mission={}: {}",
                                    missionId, err.getMessage())
                    );

        } catch (Exception e) {
            log.error("Failed to process IncidentEscalatedToDispute event: {}", e.getMessage(), e);
        }
    }

    // ── Dispute → Wallet compensation loop ───────────────────────────────────

    /**
     * Consumes {@code tnt.billing.compensation.initiated} events from tnt-dispute-core and,
     * for {@code WALLET_CREDIT} compensations, credits the beneficiary's in-app wallet and
     * confirms back on {@code tnt.billing.compensation.paid} so the dispute can transition to
     * {@code COMPENSATED}.
     *
     * <p>Audit n°5 P-01, resolved 2026-07-23: this loop used to be only half-wired — dispute
     * emitted {@code compensation.initiated} that nobody consumed, and waited forever for
     * {@code compensation.paid} that nobody emitted. Other compensation methods
     * ({@code MOBILE_MONEY_*}, {@code BANK_TRANSFER}, {@code SERVICE_CREDIT}, {@code REDELIVERY})
     * have no payment gateway or fulfillment integration wired anywhere in this repo — rather
     * than fabricate one, those are logged for manual settlement instead of silently claiming
     * an automated payout that doesn't exist.
     *
     * @param message the raw Kafka message (JSON) from tnt-dispute-core
     */
    @KafkaListener(
            topics = TntTopics.BILLING_COMPENSATION_INITIATED,
            groupId = "tnt-billing-wallet-compensation",
            containerFactory = "walletKafkaListenerContainerFactory"
    )
    public void onCompensationInitiated(String message) {
        log.info("CompensationInitiated event received");
        try {
            JsonNode node = objectMapper.readTree(message);
            String paymentRef    = node.path("paymentRef").asText(null);
            String disputeId     = node.path("disputeId").asText(null);
            String tenantIdStr   = node.path("tenantId").asText(null);
            String method        = node.path("method").asText(null);
            String beneficiaryId = node.path("beneficiaryId").asText(null);
            BigDecimal amount    = new BigDecimal(node.path("amount").asText("0"));
            String currency      = node.path("currency").asText(null);

            if (disputeId == null || tenantIdStr == null || paymentRef == null) {
                log.warn("Incomplete compensation.initiated event: disputeId={} paymentRef={}",
                        disputeId, paymentRef);
                return;
            }

            if (!"WALLET_CREDIT".equals(method)) {
                log.warn("Compensation method={} for disputeId={} has no automated payout in this "
                        + "repo — requires manual settlement, compensation.paid will not be emitted",
                        method, disputeId);
                return;
            }

            UUID tenantId = UUID.fromString(tenantIdStr);
            UUID beneficiaryUserId = UUID.fromString(beneficiaryId);

            walletUseCase.creditWallet(new CreditWalletCommand(
                            beneficiaryUserId, tenantId, Money.of(amount, currency),
                            paymentRef, "Dispute compensation payout ref=" + paymentRef))
                    .flatMap(tx -> publishCompensationPaid(disputeId, tenantIdStr, paymentRef))
                    .subscribe(
                            v   -> log.info("Compensation paid: disputeId={} paymentRef={} beneficiary={}",
                                    disputeId, paymentRef, beneficiaryUserId),
                            err -> log.error("Failed to pay compensation for disputeId={}: {}",
                                    disputeId, err.getMessage())
                    );
        } catch (Exception e) {
            log.error("Failed to process CompensationInitiated event: {}", e.getMessage(), e);
        }
    }

    private Mono<Void> publishCompensationPaid(String disputeId, String tenantId, String paymentReference) {
        Map<String, Object> payload = Map.of(
                "disputeId", disputeId,
                "tenantId", tenantId,
                "paymentReference", paymentReference,
                "paidAt", LocalDateTime.now().toString());
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .map(json -> DomainEventEnvelope.wrap()
                        .correlationId(UUID.randomUUID().toString())
                        .eventType("CompensationPaid")
                        .aggregateId(disputeId)
                        .aggregateType(COMPENSATION_AGGREGATE_TYPE)
                        .tenantId(tenantId)
                        .solutionCode(COMPENSATION_SOLUTION_CODE)
                        .payload(json)
                        .kafkaTopic(TntTopics.BILLING_COMPENSATION_PAID)
                        .kafkaPartitionKey(disputeId)
                        .occurredAt(LocalDateTime.now(ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish);
    }
}
