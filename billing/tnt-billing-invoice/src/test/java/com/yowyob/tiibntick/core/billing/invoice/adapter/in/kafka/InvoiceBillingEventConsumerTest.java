package com.yowyob.tiibntick.core.billing.invoice.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.SystemInvoicingUseCase;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.GenerateInvoiceCommand;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L8: the delivery invoice is generated from {@code DeliveryDelivered} of {@code tnt.delivery.events}. */
class InvoiceBillingEventConsumerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT = UUID.randomUUID().toString();

    private final SystemInvoicingUseCase invoices = mock(SystemInvoicingUseCase.class);
    private final InvoiceBillingEventConsumer consumer = new InvoiceBillingEventConsumer(invoices, MAPPER);

    private static String delivered(String type, String payload) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"" + type + "\",\"schemaVersion\":1,\"aggregateType\":\"DELIVERY\","
                + "\"aggregateId\":\"d-1\",\"tenantId\":\"" + TENANT + "\","
                + "\"previousState\":{\"status\":\"OUT_FOR_DELIVERY\"},\"payload\":" + payload + "}";
    }

    @Test
    @DisplayName("DeliveryDelivered of an agency delivery: invoice at the agreed price, client = sender, issuer AGENCY")
    void agencyDelivery() throws Exception {
        when(invoices.generateOnceAsSystem(any())).thenReturn(Mono.empty());
        consumer.onDeliveryEvent(new ConsumerRecord<>(TntTopics.DELIVERY_EVENTS, 0, 0L, "d-1", delivered(
                "DeliveryDelivered", "{\"status\":\"DELIVERED\",\"senderId\":\"s-1\",\"agreedAmount\":\"2500.00\","
                        + "\"currency\":\"XAF\",\"agencyId\":\"a-1\"}")));

        ArgumentCaptor<GenerateInvoiceCommand> captor = ArgumentCaptor.forClass(GenerateInvoiceCommand.class);
        verify(invoices).generateOnceAsSystem(captor.capture());
        GenerateInvoiceCommand command = captor.getValue();
        assertThat(command.tenantId()).hasToString(TENANT);
        assertThat(command.deliveryId()).isEqualTo("d-1");
        assertThat(command.clientId()).isEqualTo("s-1");
        assertThat(command.currency()).isEqualTo("XAF");
        assertThat(command.lines()).singleElement()
                .satisfies(l -> assertThat(l.unitPrice().amount().doubleValue()).isEqualTo(2500.0));
        assertThat(command.issuerOrgType()).isEqualTo("AGENCY");
        assertThat(command.issuerOrgId()).isEqualTo("a-1");
    }

    @Test
    @DisplayName("freelancer delivery: issuer FREELANCER_ORG")
    void freelancerDelivery() throws Exception {
        GenerateInvoiceCommand command = InvoiceBillingEventConsumer.deliveryInvoice(MAPPER.readTree(delivered(
                "DeliveryDelivered", "{\"senderId\":\"s-1\",\"agreedAmount\":\"1000\",\"freelancerOrgId\":\"f-1\"}")));
        assertThat(command.issuerOrgType()).isEqualTo("FREELANCER_ORG");
        assertThat(command.issuerOrgId()).isEqualTo("f-1");
        assertThat(command.currency()).isEqualTo("XAF");
    }

    @Test
    @DisplayName("no agreed price or no sender: no invoice; other event types and legacy records: ignored")
    void skipped() throws Exception {
        assertThat(InvoiceBillingEventConsumer.deliveryInvoice(MAPPER.readTree(delivered("DeliveryDelivered",
                "{\"senderId\":\"s-1\",\"agreedAmount\":null}")))).isNull();
        assertThat(InvoiceBillingEventConsumer.deliveryInvoice(MAPPER.readTree(delivered("DeliveryDelivered",
                "{\"agreedAmount\":\"10\"}")))).isNull();
        consumer.onDeliveryEvent(new ConsumerRecord<>(TntTopics.DELIVERY_EVENTS, 0, 0L, "d-1", delivered(
                "DeliveryPickedUp", "{\"senderId\":\"s-1\",\"agreedAmount\":\"10\"}")));
        consumer.onDeliveryEvent(new ConsumerRecord<>(TntTopics.DELIVERY_EVENTS, 0, 0L, "d-1",
                "{\"eventType\":\"DeliveryDelivered\",\"payload\":{\"senderId\":\"s-1\",\"agreedAmount\":\"10\"}}"));
        verify(invoices, never()).generateOnceAsSystem(any());
    }
}
