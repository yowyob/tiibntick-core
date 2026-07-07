package com.yowyob.tiibntick.core.realtime.domain.service;

import com.yowyob.tiibntick.core.realtime.application.port.out.IRealtimeEventPublisher;
import com.yowyob.tiibntick.core.realtime.application.port.out.IWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic;
import com.yowyob.tiibntick.core.realtime.domain.model.ETAInterval;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.LiveETAUpdate;
import com.yowyob.tiibntick.core.realtime.domain.model.ReroutingAlert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EtaBroadcastDomainServiceTest {

    @Mock private IWebSocketBroadcaster broadcaster;
    @Mock private IRealtimeEventPublisher eventPublisher;

    private EtaBroadcastDomainService service;

    @BeforeEach
    void setUp() {
        service = new EtaBroadcastDomainService(broadcaster, eventPublisher);
    }

    private LiveETAUpdate buildEtaUpdate(String missionId, String trackingCode) {
        return LiveETAUpdate.of(
                missionId, "d-001", "tenant-A", trackingCode,
                GeoCoordinates.of(3.848, 11.502),
                ETAInterval.of(LocalDateTime.now().plusMinutes(25), LocalDateTime.now().plusMinutes(35)),
                12.0, 30, 0.91
        );
    }

    @Test
    @DisplayName("broadcastEta() broadcasts to delivery topic and tracking topic when trackingCode present")
    void broadcastEtaBroadcastsToBothTopics() {
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        LiveETAUpdate update = buildEtaUpdate("M-001", "TNT-DEL-000042");

        StepVerifier.create(service.broadcastEta(update))
                .verifyComplete();

        ArgumentCaptor<BroadcastTopic> topicCaptor = ArgumentCaptor.forClass(BroadcastTopic.class);
        verify(broadcaster, times(2)).broadcast(topicCaptor.capture(), any());

        List<String> topics = topicCaptor.getAllValues().stream()
                .map(BroadcastTopic::path)
                .toList();
        assertThat(topics).contains("/topic/delivery/M-001", "/topic/tracking/TNT-DEL-000042");
    }

    @Test
    @DisplayName("broadcastEta() only broadcasts to delivery topic when no tracking code")
    void broadcastEtaWithoutTrackingCode() {
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        LiveETAUpdate update = buildEtaUpdate("M-002", null);

        StepVerifier.create(service.broadcastEta(update))
                .verifyComplete();

        ArgumentCaptor<BroadcastTopic> topicCaptor = ArgumentCaptor.forClass(BroadcastTopic.class);
        verify(broadcaster, times(1)).broadcast(topicCaptor.capture(), any());
        assertThat(topicCaptor.getValue().path()).isEqualTo("/topic/delivery/M-002");
    }

    @Test
    @DisplayName("broadcastEta() publishes ETAUpdatedEvent to Kafka")
    void broadcastEtaPublishesKafkaEvent() {
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        LiveETAUpdate update = buildEtaUpdate("M-003", null);

        StepVerifier.create(service.broadcastEta(update))
                .verifyComplete();

        verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("broadcastRerouting() broadcasts to reroute topic and deliverer user topic")
    void broadcastReroutingBroadcastsToCorrectTopics() {
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());

        ReroutingAlert alert = ReroutingAlert.of(
                "M-001", "d-001", "tenant-A",
                "route-old", "route-new",
                0.20, 3.5, 8, "Traffic on main road"
        );

        StepVerifier.create(service.broadcastRerouting(alert))
                .verifyComplete();

        ArgumentCaptor<BroadcastTopic> topicCaptor = ArgumentCaptor.forClass(BroadcastTopic.class);
        verify(broadcaster, times(2)).broadcast(topicCaptor.capture(), any());

        List<String> topics = topicCaptor.getAllValues().stream()
                .map(BroadcastTopic::path)
                .toList();
        assertThat(topics).contains("/topic/reroute/M-001", "/topic/user/d-001");
    }

    @Test
    @DisplayName("broadcastEta() completes even when Kafka publish fails")
    void broadcastEtaCompletesOnKafkaFailure() {
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.error(new RuntimeException("Kafka unavailable")));

        LiveETAUpdate update = buildEtaUpdate("M-004", null);

        StepVerifier.create(service.broadcastEta(update))
                .verifyComplete();
    }
}
