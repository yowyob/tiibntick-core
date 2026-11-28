package com.yowyob.tiibntick.core.delivery.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L7: the vehicle listeners await their work; invalid records and failures reach the container. */
class FreelancerVehicleEventConsumerTest {

    private final DeliveryLifecycleUseCase lifecycle = mock(DeliveryLifecycleUseCase.class);
    private final FreelancerVehicleEventConsumer consumer = new FreelancerVehicleEventConsumer(lifecycle,
            new ObjectMapper(), ReactiveListenerSupport.withDefaultTimeout());

    private static ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("tnt.vehicle.assigned_to_mission", 0, 0L, "k", json);
    }

    @Test
    void recordsTheVehicleOnTheDelivery() throws Exception {
        UUID delivery = UUID.randomUUID();
        when(lifecycle.recordFreelancerVehicleAssigned(delivery, "V1", List.of("E1"))).thenReturn(Mono.empty());

        consumer.handleVehicleAssignedToMission(record("{\"missionId\":\"" + delivery
                + "\",\"vehicleId\":\"V1\",\"activeEquipmentIds\":[\"E1\"]}"));

        verify(lifecycle).recordFreelancerVehicleAssigned(delivery, "V1", List.of("E1"));
    }

    @Test
    void aMissingMissionAnUnreadableRecordOrAFailureReachesTheContainer() {
        assertThatThrownBy(() -> consumer.handleVehicleAssignedToMission(record("{\"vehicleId\":\"V1\"}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> consumer.handleVehicleAssignedToMission(record("not json"))).isNotNull();
        assertThatThrownBy(() -> consumer.handleVehicleReleasedFromMission(record("not json"))).isNotNull();
        verify(lifecycle, never()).recordFreelancerVehicleAssigned(any(), anyString(), anyList());

        UUID delivery = UUID.randomUUID();
        when(lifecycle.recordFreelancerVehicleAssigned(any(), any(), anyList()))
                .thenReturn(Mono.error(new IllegalStateException("unknown delivery")));
        assertThatThrownBy(() -> consumer.handleVehicleAssignedToMission(
                record("{\"missionId\":\"" + delivery + "\",\"vehicleId\":\"V1\"}")))
                .hasMessageContaining("unknown delivery");
    }
}
