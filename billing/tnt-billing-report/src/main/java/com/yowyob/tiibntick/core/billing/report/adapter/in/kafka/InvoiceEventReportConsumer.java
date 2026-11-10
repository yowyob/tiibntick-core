package com.yowyob.tiibntick.core.billing.report.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus;
import com.yowyob.tiibntick.core.billing.report.application.service.InvoiceProjectionService;
import com.yowyob.tiibntick.core.billing.report.domain.model.InvoiceReportEntry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for invoice domain events.
 * Projects InvoiceGenerated/InvoicePaid/InvoiceCancelled into the report projection table.
 *
 * @author MANFOUO Braun
 */
@Component
public class InvoiceEventReportConsumer {

    private static final Logger log = LoggerFactory.getLogger(InvoiceEventReportConsumer.class);
    private static final double DEFAULT_PLATFORM_FEE_RATE = 2.5;
    /**
     * Projections are applied synchronously on the listener thread: a PAID event must not overtake the
     * ISSUED insert it updates (a fire-and-forget subscribe let the two race and lose the status change).
     */
    private static final java.time.Duration PROJECTION_TIMEOUT = java.time.Duration.ofSeconds(15);

    private final InvoiceProjectionService projectionService;
    private final ObjectMapper objectMapper;

    public InvoiceEventReportConsumer(
            InvoiceProjectionService projectionService,
            ObjectMapper objectMapper) {
        this.projectionService = projectionService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${tnt.kafka.topics.invoice-events:tnt.billing.invoice.events}",
            groupId = "${tnt.kafka.consumer.groups.billing-report:tnt-billing-report-group}",
            containerFactory = "reportKafkaListenerContainerFactory"
    )
    public void onInvoiceEvent(ConsumerRecord<String, String> record) {
        log.debug("Received invoice event for report projection, offset={}", record.offset());
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) objectMapper.readValue(record.value(), Map.class);

            String eventType = record.headers().lastHeader("eventType") != null
                    ? new String(record.headers().lastHeader("eventType").value()) : "UNKNOWN";

            UUID invoiceId = UUID.fromString((String) payload.get("invoiceId"));
            UUID tenantId  = payload.get("tenantId") != null
                    ? UUID.fromString((String) payload.get("tenantId")) : null;

            if (tenantId == null) { log.warn("Missing tenantId in invoice event, skipping"); return; }

            String invoiceNumber = (String) payload.getOrDefault("invoiceNumber", "");
            String countryCode   = (String) payload.getOrDefault("countryCode", "CM");
            String clientId      = (String) payload.getOrDefault("clientId", "");
            String missionId     = (String) payload.getOrDefault("missionId", "");
            String currency      = "XAF";

            Map<?, ?> amountMap  = (Map<?, ?>) payload.get("amount");
            BigDecimal net       = amountMap != null
                    ? new BigDecimal(amountMap.get("amount").toString()) : BigDecimal.ZERO;
            String cur           = amountMap != null ? (String) amountMap.get("currency") : currency;

            Money netMoney       = Money.of(net, cur);
            Money taxMoney       = netMoney.percentage(BigDecimal.valueOf(19.25));
            Money grossMoney     = netMoney.add(taxMoney);
            Money platformFee    = netMoney.percentage(DEFAULT_PLATFORM_FEE_RATE);

            InvoiceStatus status = switch (eventType) {
                case "InvoiceGenerated"  -> InvoiceStatus.ISSUED;
                case "InvoicePaid"       -> InvoiceStatus.PAID;
                case "InvoiceCancelled"  -> InvoiceStatus.CANCELLED;
                default                  -> InvoiceStatus.ISSUED;
            };

            LocalDate today = LocalDate.now();

            // PAID / CANCELLED carry no party data: they only move the row the ISSUED event wrote.
            if (status != InvoiceStatus.ISSUED) {
                projectionService.updateStatus(tenantId, invoiceId, status,
                                status == InvoiceStatus.PAID ? today : null)
                        .doOnSuccess(v -> log.debug("Invoice {} projected as {}", invoiceId, status))
                        .doOnError(e -> log.error("Status projection failed for invoice {}: {}", invoiceId, e.getMessage()))
                        .block(PROJECTION_TIMEOUT);
                return;
            }

            InvoiceReportEntry entry = new InvoiceReportEntry(
                    invoiceId, invoiceNumber, tenantId, countryCode, clientId, missionId,
                    grossMoney, taxMoney, netMoney, platformFee,
                    status, today, null,
                    blankToNull((String) payload.get("issuerOrgType")),
                    blankToNull((String) payload.get("issuerOrgId")),
                    blankToNull((String) payload.get("appliedTemplateName")),
                    Money.of(BigDecimal.ZERO, netMoney.currency())
                );

            projectionService.project(entry)
                    .doOnSuccess(v -> log.debug("Projected invoice {} status={}", invoiceId, status))
                    .doOnError(e -> log.error("Projection failed for invoice {}: {}", invoiceId, e.getMessage()))
                    .block(PROJECTION_TIMEOUT);

        } catch (Exception e) {
            log.error("Failed processing invoice event at offset={}: {}", record.offset(), e.getMessage(), e);
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }
}
