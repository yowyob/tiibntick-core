package com.yowyob.tiibntick.core.billing.invoice.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.GenerateInvoiceCommand;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.MarkInvoicePaidCommand;
import com.yowyob.tiibntick.core.billing.invoice.application.service.InvoiceService;
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
 *   <li>{@code tnt.delivery.mission.completed} → generate invoice</li>
 *   <li>{@code tnt.billing.wallet.payment.confirmed} → mark invoice paid</li>
 * </ul>
 * </p>
 *
 * @author MANFOUO Braun
 */
@Component
public class InvoiceBillingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InvoiceBillingEventConsumer.class);

    private final InvoiceService invoiceService;
    private final ObjectMapper objectMapper;

    public InvoiceBillingEventConsumer(InvoiceService invoiceService,
            @Qualifier("invoiceObjectMapper") ObjectMapper objectMapper) {
        this.invoiceService = invoiceService;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles DeliveryCompleted events from tnt-delivery-core.
     * Generates an invoice for the completed mission.
     */
    @KafkaListener(
            topics = "${tnt.kafka.topics.delivery-mission-completed:" + TntTopics.DELIVERY_MISSION_COMPLETED + "}",
            groupId = "${tnt.kafka.consumer.groups.billing-invoice:tnt-billing-invoice-group}",
            containerFactory = "invoiceKafkaListenerContainerFactory"
    )
    public void onDeliveryCompleted(ConsumerRecord<String, String> record) {
        log.info("InvoiceBillingEventConsumer received DeliveryCompleted, offset={}", record.offset());
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) objectMapper.readValue(record.value(), Map.class);

            UUID tenantId    = UUID.fromString((String) payload.get("tenantId"));
            String tenantCode = (String) payload.getOrDefault("tenantCode", "DEFAULT");
            String countryCode = (String) payload.getOrDefault("countryCode", "CM");
            String missionId  = (String) payload.get("missionId");
            String clientId   = (String) payload.get("senderThirdPartyId");
            String currency   = (String) payload.getOrDefault("currency", "XAF");
            Number sellingPriceRaw = (Number) payload.get("sellingPrice");

            if (missionId == null || clientId == null || sellingPriceRaw == null) {
                log.warn("Missing required fields in DeliveryCompleted event, skipping");
                return;
            }

            Money unitPrice = Money.of(sellingPriceRaw.doubleValue(), currency);
            InvoiceLine line = InvoiceLine.of(1, "Delivery Mission " + missionId,
                    1.0, unitPrice, BigDecimal.ZERO, LineItemType.DELIVERY_FEE);

            GenerateInvoiceCommand command = GenerateInvoiceCommand.simple(
                    tenantId, tenantCode, countryCode,
                    missionId, null, clientId,
                    List.of(line), List.of(), currency,
                    LocalDateTime.now().plusDays(7));

            invoiceService.generate(command)
                    .doOnSuccess(inv -> log.info("Invoice generated from DeliveryCompleted: {}", inv.getNumber()))
                    .doOnError(e -> log.error("Failed to generate invoice for mission={}: {}", missionId, e.getMessage()))
                    .subscribe();

        } catch (Exception e) {
            log.error("Failed processing DeliveryCompleted event at offset={}: {}", record.offset(), e.getMessage(), e);
        }
    }

    /**
     * Handles PaymentConfirmed events from tnt-billing-wallet.
     */
    @KafkaListener(
            topics = "${tnt.kafka.topics.payment-confirmed:" + TntTopics.BILLING_WALLET_PAYMENT_CONFIRMED + "}",
            groupId = "${tnt.kafka.consumer.groups.billing-invoice:tnt-billing-invoice-group}",
            containerFactory = "invoiceKafkaListenerContainerFactory"
    )
    public void onPaymentConfirmed(ConsumerRecord<String, String> record) {
        log.info("InvoiceBillingEventConsumer received PaymentConfirmed, offset={}", record.offset());
        try {
            Map<?, ?> payload = objectMapper.readValue(record.value(), Map.class);

            UUID tenantId   = UUID.fromString((String) payload.get("tenantId"));
            UUID invoiceId  = UUID.fromString((String) payload.get("invoiceId"));
            String paymentRef = (String) payload.get("paymentRef");

            if (paymentRef == null) {
                log.warn("Missing paymentRef in PaymentConfirmed event, skipping");
                return;
            }

            MarkInvoicePaidCommand command = new MarkInvoicePaidCommand(tenantId, invoiceId, paymentRef);
            invoiceService.markPaid(command)
                    .doOnSuccess(inv -> log.info("Invoice marked paid: {}", inv.getNumber()))
                    .doOnError(e -> log.error("Failed to mark invoice paid={}: {}", invoiceId, e.getMessage()))
                    .subscribe();

        } catch (Exception e) {
            log.error("Failed processing PaymentConfirmed event at offset={}: {}", record.offset(), e.getMessage(), e);
        }
    }
}
