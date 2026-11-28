package com.yowyob.tiibntick.core.actor.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.common.tenant.SystemTenant;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L8: the courier's incident count is incremented on {@code IncidentClosed} of {@code tnt.incident.events}. */
class IncidentEventConsumerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID TENANT = UUID.randomUUID();

    private final IDelivererRepository deliverers = mock(IDelivererRepository.class);
    private final IFreelancerRepository freelancers = mock(IFreelancerRepository.class);
    private final IncidentEventConsumer consumer = new IncidentEventConsumer(deliverers, freelancers);

    private static String incident(String type, Object reporter, String role) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"" + type + "\",\"schemaVersion\":1,\"aggregateType\":\"INCIDENT\","
                + "\"aggregateId\":\"" + UUID.randomUUID() + "\",\"tenantId\":\"" + TENANT + "\","
                + "\"payload\":{\"status\":\"CLOSED\",\"platform\":\"AGENCY\",\"reportedByActorId\":\"" + reporter
                + "\",\"reportedByRole\":\"" + role + "\"}}";
    }

    @Test
    @DisplayName("IncidentClosed reported by a courier increments that courier's count")
    void courierCounted() throws Exception {
        when(deliverers.incrementIncidentHistoryCount(any(), any())).thenReturn(Mono.empty());
        when(freelancers.incrementIncidentHistoryCount(any(), any())).thenReturn(Mono.empty());
        UUID courier = UUID.randomUUID();
        consumer.onIncidentClosed(new ConsumerRecord<>(TntTopics.INCIDENT_EVENTS, 0, 0L, "k",
                incident("IncidentClosed", courier, "AGENCY_DRIVER")));
        verify(deliverers).incrementIncidentHistoryCount(courier, TENANT);
    }

    @Test
    @DisplayName("another event type, a client reporter, the system actor or a legacy record count nothing")
    void notCounted() throws Exception {
        consumer.onIncidentClosed(new ConsumerRecord<>(TntTopics.INCIDENT_EVENTS, 0, 0L, "k",
                incident("IncidentResolved", UUID.randomUUID(), "AGENCY_DRIVER")));
        consumer.onIncidentClosed(new ConsumerRecord<>(TntTopics.INCIDENT_EVENTS, 0, 0L, "k",
                incident("IncidentClosed", UUID.randomUUID(), "CLIENT")));
        consumer.onIncidentClosed(new ConsumerRecord<>(TntTopics.INCIDENT_EVENTS, 0, 0L, "k",
                incident("IncidentClosed", SystemTenant.systemActorId(), "SYSTEM")));
        consumer.onIncidentClosed(new ConsumerRecord<>(TntTopics.INCIDENT_EVENTS, 0, 0L, "k",
                "{\"eventType\":\"IncidentClosed\",\"tenantId\":\"" + TENANT + "\"}"));
        verify(deliverers, never()).incrementIncidentHistoryCount(any(), any());
        verify(freelancers, never()).incrementIncidentHistoryCount(any(), any());
    }

    @Test
    @DisplayName("an automatic detection (role SYSTEM) names the driver recorded as reporter")
    void automaticDetection() throws Exception {
        UUID driver = UUID.randomUUID();
        assertThat(IncidentEventConsumer.courierInvolved(MAPPER.readTree(
                "{\"reportedByActorId\":\"" + driver + "\",\"reportedByRole\":\"SYSTEM\"}"))).isEqualTo(driver);
        assertThat(IncidentEventConsumer.courierInvolved(MAPPER.readTree(
                "{\"reportedByActorId\":\"" + driver + "\",\"reportedByRole\":\"FREELANCER_DRIVER\"}"))).isEqualTo(driver);
        assertThat(IncidentEventConsumer.courierInvolved(MAPPER.readTree("{\"reportedByRole\":\"GO_BIKER\"}")))
                .isNull();
    }
}
