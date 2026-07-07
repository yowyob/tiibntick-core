package com.yowyob.tiibntick.core.billing.wallet.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.CreditCommissionCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IPaymentIntentRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.WalletStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * BillingEventConsumer — consumes Kafka events relevant to wallet operations.
 *
 * <p>Listens to:</p>
 * <ul>
 *   <li>{@code tnt.billing.wallet.commission-calculated} — credits deliverer commission
 *       to their in-app wallet after a confirmed invoice payment.</li>
 *   <li>{@code tnt.incident.escalated.to.dispute} — permanently freezes the payment
 *       linked to the affected mission until the dispute is resolved by tnt-dispute-core.</li>
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

    private final IWalletUseCase walletUseCase;
    private final IPaymentIntentRepository paymentIntentRepository;
    private final IWalletRepository walletRepository;
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
            topics = "tnt.billing.wallet.commission-calculated",
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
            topics = "tnt.incident.escalated.to.dispute",
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
}
