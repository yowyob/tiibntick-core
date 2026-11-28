package com.yowyob.tiibntick.core.billing.invoice.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.GenerateInvoiceCommand;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.MarkInvoicePaidCommand;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.SystemInvoicingUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.common.kafka.TntAggregateEvent;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.InvoiceLine;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.LineItemType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for invoice-triggering events.
 *
 * <p>Listens to:
 * <ul>
 *   <li>{@code DeliveryDelivered} of {@code tnt.delivery.events} (lot L8, was {@code tnt.delivery.mission.completed})
 *       → the delivery's invoice, once (none when the payment flow already issued it);</li>
 *   <li>{@code tnt.billing.wallet.payment.confirmed} → mark invoice paid</li>
 * </ul>
 * Both await their work (lot L7 rule): a failure is retried, then dead-lettered to {@code tnt.dlq}.
 *
 * @author MANFOUO Braun
 */
@Component
public class InvoiceBillingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InvoiceBillingEventConsumer.class);
    static final String DELIVERED = "DeliveryDelivered";
    private static final java.time.Duration TIMEOUT = java.time.Duration.ofSeconds(30);

    private final SystemInvoicingUseCase invoiceService;
    private final ObjectMapper objectMapper;

    public InvoiceBillingEventConsumer(SystemInvoicingUseCase invoiceService,
            @Qualifier("invoiceObjectMapper") ObjectMapper objectMapper) {
        this.invoiceService = invoiceService;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code DeliveryDelivered}: invoice of the delivered delivery at its agreed price, the sender being the client;
     * issuer = the agency or the freelancer organisation of the delivery when there is one. Skipped (logged) without
     * agreed price or sender.
     */
    @KafkaListener(
            topics = TntTopics.DELIVERY_EVENTS,
            groupId = "${tnt.kafka.consumer.groups.billing-invoice:tnt-billing-invoice-group}",
            containerFactory = "invoiceKafkaListenerContainerFactory"
    )
    public void onDeliveryEvent(ConsumerRecord<String, String> record) throws Exception {
        JsonNode event = objectMapper.readTree(record.value());
        if (!TntAggregateEvent.isAggregateEvent(event) || !DELIVERED.equals(event.path("eventType").asText())) {
            return;
        }
        GenerateInvoiceCommand command = deliveryInvoice(event);
        if (command == null) {
            log.warn("INVOICE_NOT_GENERATED delivery={} — no agreed price, sender or tenant",
                    event.path("aggregateId").asText());
            return;
        }
        var invoice = ReactiveListenerSupport.awaitBounded(invoiceService.generateOnceAsSystem(command), TIMEOUT);
        log.info("Invoice {} for delivered delivery {}", invoice != null ? invoice.getNumber() : null,
                command.deliveryId());
    }

    static GenerateInvoiceCommand deliveryInvoice(JsonNode event) {
        JsonNode p = event.path("payload");
        String deliveryId = event.path("aggregateId").asText(null);
        String tenant = event.path("tenantId").asText(null);
        String sender = p.path("senderId").asText(null);
        String amount = p.path("agreedAmount").asText(null);
        if (deliveryId == null || tenant == null || sender == null || amount == null || p.path("agreedAmount").isNull()
                || new BigDecimal(amount).signum() <= 0) {
            return null;
        }
        String currency = p.hasNonNull("currency") ? p.path("currency").asText() : "XAF";
        InvoiceLine line = InvoiceLine.of(1, "Livraison " + deliveryId, 1.0,
                Money.of(new BigDecimal(amount).doubleValue(), currency), BigDecimal.ZERO, LineItemType.DELIVERY_FEE);
        String issuerType = null;
        String issuerId = null;
        if (p.hasNonNull("agencyId")) {
            issuerType = "AGENCY";
            issuerId = p.path("agencyId").asText();
        } else if (p.hasNonNull("freelancerOrgId")) {
            issuerType = "FREELANCER_ORG";
            issuerId = p.path("freelancerOrgId").asText();
        }
        return new GenerateInvoiceCommand(UUID.fromString(tenant), "DEFAULT", "CM", deliveryId, null, sender,
                List.of(line), List.of(), currency, LocalDateTime.now().plusDays(7), issuerType, issuerId, null,
                null, List.of(), null, null);
    }

    /**
     * Handles PaymentConfirmed events from tnt-billing-wallet.
     */
    @KafkaListener(
            topics = "${tnt.kafka.topics.payment-confirmed:" + TntTopics.BILLING_WALLET_PAYMENT_CONFIRMED + "}",
            groupId = "${tnt.kafka.consumer.groups.billing-invoice:tnt-billing-invoice-group}",
            containerFactory = "invoiceKafkaListenerContainerFactory"
    )
    public void onPaymentConfirmed(ConsumerRecord<String, String> record) throws Exception {
        Map<?, ?> payload = objectMapper.readValue(record.value(), Map.class);
        if (payload.get("invoiceId") == null) {
            // Settlement-level confirmations (Core SettlementService, R1 2026-11-26) carry no invoice:
            // their caller issues the invoice already paid. Nothing to mark here.
            log.debug("PaymentConfirmed without invoiceId (settlement={}), nothing to mark paid",
                    payload.get("settlementId"));
            return;
        }
        String paymentRef = (String) payload.get("paymentRef");
        if (paymentRef == null) {
            log.warn("Missing paymentRef in PaymentConfirmed event, skipping");
            return;
        }
        UUID tenantId = UUID.fromString((String) payload.get("tenantId"));
        UUID invoiceId = UUID.fromString((String) payload.get("invoiceId"));
        var invoice = ReactiveListenerSupport.awaitBounded(
                invoiceService.markPaidAsSystem(new MarkInvoicePaidCommand(tenantId, invoiceId, paymentRef)), TIMEOUT);
        log.info("Invoice marked paid: {}", invoice != null ? invoice.getNumber() : invoiceId);
    }
}
