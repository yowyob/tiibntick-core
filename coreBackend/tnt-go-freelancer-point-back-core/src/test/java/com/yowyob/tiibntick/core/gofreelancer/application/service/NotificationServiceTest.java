package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.MatchingNotificationEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.search.document.AnnouncementDocument;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.search.document.FreelancerDocument;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.EmailPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpNotificationTemplates;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Notifications go through the catalog (sendLocalized) and the matching event; no local table is written. */
class NotificationServiceTest {

    private final EmailPort email = mock(EmailPort.class);
    private final PushNotificationPort push = mock(PushNotificationPort.class);
    private final KafkaEventPublisher kafka = mock(KafkaEventPublisher.class);
    private final NotificationService service = new NotificationService(email, push, kafka);

    NotificationServiceTest() {
        when(email.sendLocalized(any(), any(), anyString(), anyMap())).thenReturn(Mono.empty());
        when(push.sendLocalized(any(), anyString(), anyMap())).thenReturn(Mono.empty());
    }

    @Test
    void aMatchSendsLocalizedEmailAndPushAndTheMatchingEvent() {
        UUID dp = UUID.randomUUID();
        UUID ann = UUID.randomUUID();
        FreelancerDocument doc = FreelancerDocument.builder().id(dp).personId(dp).email("d@x.test").build();
        AnnouncementDocument announcement = AnnouncementDocument.builder().id(ann).build();

        StepVerifier.create(service.notifyEligibleFreelancers(List.of(doc), announcement)).verifyComplete();

        verify(email).sendLocalized(eq("d@x.test"), eq(dp), eq(GofpNotificationTemplates.ANNOUNCEMENT_AVAILABLE),
                eq(Map.of("announcementId", ann.toString())));
        verify(push).sendLocalized(eq(dp), eq(GofpNotificationTemplates.ANNOUNCEMENT_AVAILABLE),
                eq(Map.of("announcementId", ann.toString())));
        ArgumentCaptor<MatchingNotificationEvent> event = ArgumentCaptor.forClass(MatchingNotificationEvent.class);
        verify(kafka).publishMatchingNotification(event.capture());
        assertThat(event.getValue().getFreelancerId()).isEqualTo(dp);
        assertThat(event.getValue().getAnnouncementId()).isEqualTo(ann);
    }

    @Test
    void aFailingChannelDoesNotFailTheMatch() {
        UUID dp = UUID.randomUUID();
        when(email.sendLocalized(any(), any(), anyString(), anyMap())).thenReturn(Mono.error(new RuntimeException("smtp")));
        when(push.sendLocalized(any(), anyString(), anyMap())).thenReturn(Mono.error(new RuntimeException("fcm")));
        FreelancerDocument doc = FreelancerDocument.builder().id(dp).personId(dp).email("d@x.test").build();

        StepVerifier.create(service.notifyEligibleFreelancers(List.of(doc),
                AnnouncementDocument.builder().id(UUID.randomUUID()).build())).verifyComplete();
    }

    @Test
    void anEvaluationRequestIsRenderedFromTheCatalogOnBothChannels() {
        UUID person = UUID.randomUUID();
        Map<String, Object> params = Map.of("relayName", "Relais Bastos");

        StepVerifier.create(service.sendEvaluationRequest(person, "c@x.test",
                GofpNotificationTemplates.EVALUATION_CLIENT_PICKUP, params)).verifyComplete();

        verify(email).sendLocalized("c@x.test", person, GofpNotificationTemplates.EVALUATION_CLIENT_PICKUP, params);
        verify(push).sendLocalized(person, GofpNotificationTemplates.EVALUATION_CLIENT_PICKUP, params);
    }
}
