package com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L8: the agency hub projection reads the relay-hub movements of {@code tnt.parcel.events}. */
class InventoryHubConsumerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Map<String, Object> projection(String previous, String payload) throws Exception {
        return InventoryHubConsumer.projection(MAPPER.readTree("{\"eventId\":\"e\",\"eventType\":\"X\","
                + "\"schemaVersion\":1,\"aggregateType\":\"PARCEL\",\"aggregateId\":\"parcel-1\",\"tenantId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"previousState\":" + previous + ",\"payload\":" + payload + "}"));
    }

    @Test
    @DisplayName("entering AT_RELAY_HUB is a deposit at the hub of the new custody")
    void deposit() throws Exception {
        Map<String, Object> p = projection("{\"status\":\"IN_TRANSIT\"}", "{\"status\":\"AT_RELAY_HUB\","
                + "\"trackingCode\":\"TNT-1\",\"currentDeliveryId\":\"d-1\",\"custody\":{\"hubId\":\"hub-1\"}}");
        assertThat(p).containsEntry("deposited", true).containsEntry("hubId", "hub-1")
                .containsEntry("trackingCode", "TNT-1").containsEntry("packageId", "parcel-1")
                .containsEntry("missionId", "d-1");
    }

    @Test
    @DisplayName("leaving AT_RELAY_HUB is a withdrawal from the hub of the previous custody")
    void withdrawal() throws Exception {
        Map<String, Object> p = projection("{\"status\":\"AT_RELAY_HUB\",\"custody\":{\"hubId\":\"hub-1\"}}",
                "{\"status\":\"DELIVERED\",\"trackingCode\":\"TNT-1\",\"custody\":{\"holderType\":\"RECIPIENT\"}}");
        assertThat(p).containsEntry("deposited", false).containsEntry("hubId", "hub-1");
    }

    @Test
    @DisplayName("other transitions, a creation and a legacy record are not hub movements")
    void notAMovement() throws Exception {
        assertThat(projection("{\"status\":\"ASSIGNED\"}", "{\"status\":\"PICKED_UP\",\"trackingCode\":\"T\"}")).isNull();
        assertThat(projection("null", "{\"status\":\"CREATED\",\"trackingCode\":\"T\"}")).isNull();
        assertThat(InventoryHubConsumer.projection(MAPPER.readTree(
                "{\"trackingCode\":\"T\",\"hubId\":\"h\",\"tenantId\":\"t\"}"))).isNull();
    }

    @Test
    @DisplayName("not collected at the end of storage (RETURN_REQUESTED, NOT_COLLECTED_AT_RELAY): expired, not withdrawn")
    void notCollectedIsExpired() throws Exception {
        Map<String, Object> p = projection("{\"status\":\"AT_RELAY_HUB\",\"custody\":{\"hubId\":\"hub-1\"}}",
                "{\"status\":\"RETURN_REQUESTED\",\"returnCause\":\"NOT_COLLECTED_AT_RELAY\",\"trackingCode\":\"TNT-1\"}");
        assertThat(p).containsEntry("expired", true).containsEntry("deposited", false);
        assertThat(projection("{\"status\":\"AT_RELAY_HUB\"}",
                "{\"status\":\"RETURN_REQUESTED\",\"returnCause\":\"DAMAGED\",\"trackingCode\":\"TNT-1\"}"))
                .containsEntry("expired", false);
    }

    @Test
    @DisplayName("listener: a parcel not collected at the end of storage is handed to the expiry service, never saved here")
    void listenerHandsTheExpiryToTheService() throws Exception {
        var hubs = mock(com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRelayHubR2dbcRepository.class);
        var parcels = mock(com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence.HubParcelRecordR2dbcRepository.class);
        var occupancy = mock(com.yowyob.tiibntick.core.agency.org.hubops.application.service.HubOccupancyService.class);
        var expiry = mock(com.yowyob.tiibntick.core.agency.org.hubops.application.service.HubParcelExpiryService.class);
        InventoryHubConsumer consumer = new InventoryHubConsumer(hubs, parcels, occupancy, expiry, MAPPER);
        java.util.UUID tenant = java.util.UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(expiry.expireDeposited(eq("TNT-1"), eq(tenant), any())).thenReturn(reactor.core.publisher.Mono.empty());
        var ack = mock(org.springframework.kafka.support.Acknowledgment.class);
        String event = "{\"eventId\":\"e\",\"eventType\":\"ParcelReturnRequested\",\"schemaVersion\":1,"
                + "\"aggregateType\":\"PARCEL\",\"aggregateId\":\"parcel-1\",\"tenantId\":\"" + tenant + "\","
                + "\"previousState\":{\"status\":\"AT_RELAY_HUB\",\"custody\":{\"hubId\":\"h\"}},\"payload\":"
                + "{\"status\":\"RETURN_REQUESTED\",\"returnCause\":\"NOT_COLLECTED_AT_RELAY\",\"trackingCode\":\"TNT-1\"}}";

        consumer.onParcelEvent(new org.apache.kafka.clients.consumer.ConsumerRecord<>("tnt.parcel.events", 0, 0L, "k",
                event), ack);

        verify(expiry).expireDeposited(eq("TNT-1"), eq(tenant), any());
        verify(ack).acknowledge();
        verify(parcels, never()).save(any());
        verify(parcels, never()).findByTrackingCodeAndTenantId(any(), any());
    }
}
