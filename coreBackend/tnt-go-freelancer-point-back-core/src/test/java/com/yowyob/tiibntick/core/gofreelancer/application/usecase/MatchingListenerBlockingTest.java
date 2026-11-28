package com.yowyob.tiibntick.core.gofreelancer.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerVehicleRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IEventPublisherPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IFreelancerProviderPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IGeolocationPort;
import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerVehicleApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The matching listener awaits its work (lot L7 rule, ArchUnit {@code kafkaListenersMustAwaitTheirWorkAndNeverSwallowErrors}):
 * a failure reaches the container, which retries then dead-letters the record on {@code tnt.dlq}.
 */
class MatchingListenerBlockingTest {

    private final IDeliveryAnnouncementPort announcements = mock(IDeliveryAnnouncementPort.class);
    private MatchingUseCase matching;

    @BeforeEach
    void setUp() {
        matching = new MatchingUseCase(mock(TopsisRankingUseCase.class), announcements, mock(IGeolocationPort.class),
                mock(FreelancerVehicleRepository.class), mock(DeliveryRepository.class),
                mock(FreelancerVehicleApplicationService.class), mock(GofpFreelancerRepository.class),
                mock(IFreelancerProviderPort.class), mock(IEventPublisherPort.class), mock(NotificationService.class),
                new ObjectMapper());
    }

    private static ConsumerRecord<String, String> record(String eventType, UUID announcementId) {
        String body = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"schemaVersion\":1,\"aggregateType\":\"ANNOUNCEMENT\","
                + "\"eventType\":\"" + eventType + "\",\"aggregateId\":\"" + announcementId + "\"}";
        return new ConsumerRecord<>(TntTopics.ANNOUNCEMENT_EVENTS, 0, 0L, announcementId.toString(), body);
    }

    @Test
    @DisplayName("a failing matching propagates to the container instead of being lost in a subscribe()")
    void failurePropagates() {
        UUID id = UUID.randomUUID();
        when(announcements.findById(any(), any())).thenReturn(Mono.error(new IllegalStateException("db down")));

        assertThatThrownBy(() -> matching.onAnnouncementEvent(record("AnnouncementPublished", id)))
                .hasMessage("db down");
    }

    @Test
    @DisplayName("an unknown announcement fails the record (dead-lettered after the retries)")
    void unknownAnnouncementFails() {
        when(announcements.findById(any(), any())).thenReturn(Mono.empty());

        assertThatThrownBy(() -> matching.onAnnouncementEvent(record("AnnouncementPublished", UUID.randomUUID())))
                .hasMessageContaining("Core Announcement not found");
    }

    @Test
    @DisplayName("other announcement events are ignored without any lookup")
    void otherEventsIgnored() {
        assertThatCode(() -> matching.onAnnouncementEvent(record("AnnouncementAssigned", UUID.randomUUID())))
                .doesNotThrowAnyException();
        verify(announcements, never()).findById(any(), any());
    }
}
