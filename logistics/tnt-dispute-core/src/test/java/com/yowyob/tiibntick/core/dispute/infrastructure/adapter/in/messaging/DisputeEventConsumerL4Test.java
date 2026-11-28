package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.dispute.application.command.OpenDisputeCommand;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeSystemUseCase;
import com.yowyob.tiibntick.core.dispute.domain.enums.ClaimantType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Lot L4 (C15): the dispute consumer no longer swallows failures — the error goes back to the container (retries, then
 * DLQ) and the offset is not acknowledged; the automatic opening goes through the system entry point with the
 * incident and its first parcel.
 */
class DisputeEventConsumerL4Test {

    private final IDisputeSystemUseCase system = mock(IDisputeSystemUseCase.class);
    private final DisputeEventConsumer consumer = new DisputeEventConsumer(system, new ObjectMapper());
    private final Acknowledgment ack = mock(Acknowledgment.class);

    private static ConsumerRecord<String, String> record(String topic, String value) {
        return new ConsumerRecord<>(topic, 0, 0L, "k", value);
    }

    @Test
    @DisplayName("an escalation opens through the system entry point, with incidentId and the first parcel, then acks")
    void escalationOpensTheDispute() {
        when(system.openFromIncident(any())).thenReturn(Mono.empty());
        String incident = UUID.randomUUID().toString();
        String parcel = UUID.randomUUID().toString();
        String tenant = UUID.randomUUID().toString();
        String delivery = UUID.randomUUID().toString();
        // lot L8: IncidentEscalatedToDispute of tnt.incident.events; other types and legacy records are ignored
        consumer.listen(record("tnt.incident.events", "{\"eventId\":\"" + UUID.randomUUID()
                + "\",\"eventType\":\"IncidentResolved\",\"schemaVersion\":1,\"aggregateId\":\"" + incident
                + "\",\"payload\":{}}"), ack);
        consumer.listen(record("tnt.incident.events", "{\"incidentId\":\"" + incident + "\"}"), ack);
        verifyNoInteractions(system);
        consumer.listen(record("tnt.incident.events", "{\"eventId\":\"" + UUID.randomUUID()
                + "\",\"eventType\":\"IncidentEscalatedToDispute\",\"schemaVersion\":1,\"aggregateType\":\"INCIDENT\","
                + "\"aggregateId\":\"" + incident + "\",\"tenantId\":\"" + tenant + "\",\"payload\":{\"deliveryId\":\""
                + delivery + "\",\"affectedParcelIds\":[\"" + parcel + "\"],\"type\":\"PARCEL_TOTAL_LOSS\","
                + "\"trackingCode\":\"TNT-1\",\"agencyId\":null},\"details\":{\"reason\":\"transféré en litige\","
                + "\"fraudReason\":\"lost\"}}"), ack);

        ArgumentCaptor<OpenDisputeCommand> cmd = ArgumentCaptor.forClass(OpenDisputeCommand.class);
        verify(system).openFromIncident(cmd.capture());
        assertThat(cmd.getValue().incidentId()).isEqualTo(incident);
        assertThat(cmd.getValue().packageId()).isEqualTo(parcel);
        assertThat(cmd.getValue().claimantType()).isEqualTo(ClaimantType.SYSTEM);
        assertThat(cmd.getValue().tenantId()).isEqualTo(tenant);
        assertThat(cmd.getValue().missionId()).isEqualTo(delivery);
        assertThat(cmd.getValue().trackingCode()).isEqualTo("TNT-1");
        assertThat(cmd.getValue().description()).contains("PARCEL_TOTAL_LOSS", "lost");
        verify(ack, times(3)).acknowledge();
    }

    @Test
    @DisplayName("C15: a failure is rethrown and the record is not acknowledged; an unreadable record too")
    void failuresAreNotSwallowed() {
        when(system.confirmCompensationPaid(any())).thenReturn(Mono.error(new IllegalStateException("db down")));
        assertThatThrownBy(() -> consumer.listen(record("tnt.billing.compensation.paid",
                "{\"disputeId\":\"" + UUID.randomUUID() + "\",\"tenantId\":\"t\",\"paymentReference\":\"p\"}"), ack))
                .hasMessageContaining("db down");
        assertThatThrownBy(() -> consumer.listen(record("tnt.billing.compensation.paid", "not json"), ack))
                .isInstanceOf(IllegalArgumentException.class);
        verify(ack, never()).acknowledge();
    }
}
