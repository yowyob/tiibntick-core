package com.yowyob.tiibntick.core.billing.wallet.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.CommissionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementMethod;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SettlementOutboxEventPublisher — payloads in the shape the existing consumers read")
class SettlementOutboxEventPublisherTest {

    private final PublishEventUseCase publish = mock(PublishEventUseCase.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final SettlementOutboxEventPublisher publisher = new SettlementOutboxEventPublisher(publish, mapper);

    private final WalletSettlement settlement = WalletSettlement.builder()
            .id(UUID.randomUUID()).tenantId(UUID.randomUUID())
            .referenceType("DELIVERY").referenceId(UUID.randomUUID())
            .payerUserId(UUID.randomUUID()).payeeUserId(UUID.randomUUID())
            .amount(new BigDecimal("5000.00")).currency("XAF")
            .method(SettlementMethod.WALLET_TRANSFER).status(SettlementStatus.PAID).kernelReference("ktx-9")
            .commissionRate(new BigDecimal("0.05")).commissionAmount(new BigDecimal("250.00"))
            .commissionStatus(CommissionStatus.DUE)
            .createdAt(LocalDateTime.now()).paidAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

    private DomainEventEnvelope captured() {
        ArgumentCaptor<DomainEventEnvelope> envelope = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(publish).publish(envelope.capture());
        return envelope.getValue();
    }

    @Test
    @DisplayName("payment.confirmed: accounting fields at the root, no invoiceId / orderId")
    void paymentConfirmed() throws Exception {
        when(publish.publish(any())).thenReturn(Mono.empty());
        publisher.paymentConfirmed(settlement).block();

        DomainEventEnvelope e = captured();
        assertThat(e.getKafkaTopic()).isEqualTo(TntTopics.BILLING_WALLET_PAYMENT_CONFIRMED);
        assertThat(e.getKafkaPartitionKey()).isEqualTo(settlement.getReferenceId().toString());
        assertThat(e.getTenantId()).isEqualTo(settlement.getTenantId().toString());
        JsonNode p = mapper.readTree(e.getPayload());
        assertThat(p.get("tenantId").asText()).isEqualTo(settlement.getTenantId().toString());
        assertThat(p.get("organizationId").asText()).isEqualTo(settlement.getTenantId().toString());
        assertThat(p.get("paymentId").asText()).isEqualTo(settlement.getId().toString());
        assertThat(new BigDecimal(p.get("amount").asText())).isEqualByComparingTo("5000");
        assertThat(p.get("method").asText()).isEqualTo("WALLET_TRANSFER");
        assertThat(p.get("paymentRef").asText()).isEqualTo("ktx-9");
        assertThat(p.get("missionId").asText()).isEqualTo(settlement.getReferenceId().toString());
        assertThat(p.hasNonNull("eventId")).isTrue();
        assertThat(p.has("invoiceId")).as("would be read as an agency commission id by WalletPayoutConsumer").isFalse();
        assertThat(p.has("orderId")).as("would make the market re-confirm a paid order").isFalse();
    }

    @Test
    @DisplayName("commission.calculated: missionId + commission + PLATFORM_FEE type")
    void commissionCalculated() throws Exception {
        when(publish.publish(any())).thenReturn(Mono.empty());
        publisher.commissionCalculated(settlement).block();

        DomainEventEnvelope e = captured();
        assertThat(e.getKafkaTopic()).isEqualTo(TntTopics.BILLING_WALLET_COMMISSION_CALCULATED);
        JsonNode p = mapper.readTree(e.getPayload());
        assertThat(p.get("missionId").asText()).isEqualTo(settlement.getReferenceId().toString());
        assertThat(new BigDecimal(p.get("commission").asText())).isEqualByComparingTo("250");
        assertThat(p.get("commissionType").asText()).isEqualTo("PLATFORM_FEE");
        assertThat(p.get("organizationId").asText()).isEqualTo(settlement.getTenantId().toString());
        assertThat(p.get("delivererId").asText()).isEqualTo(settlement.getPayeeUserId().toString());
    }
}
