package com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.consumer;

import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.SubscriptionAttemptEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerVehicleRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.AnnouncementSubscriptionRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerQuotaService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerVehicleApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.TopsisRankingUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L7: the subscription listener awaits its work; only a duplicate subscription is absorbed. */
class SubscriptionConfirmationConsumerBlockingTest {

    private final IAnnouncementRepository announcements = mock(IAnnouncementRepository.class);
    private final AnnouncementSubscriptionRepository subscriptions = mock(AnnouncementSubscriptionRepository.class);
    private final FreelancerQuotaService quota = mock(FreelancerQuotaService.class);
    private final PushNotificationPort push = mock(PushNotificationPort.class);
    private final SubscriptionConfirmationConsumer consumer = new SubscriptionConfirmationConsumer(
            announcements, subscriptions, mock(GofpFreelancerRepository.class), mock(FreelancerVehicleRepository.class),
            mock(DeliveryRepository.class), mock(FreelancerVehicleApplicationService.class), quota,
            mock(TopsisRankingUseCase.class), push, ReactiveListenerSupport.withDefaultTimeout());

    private final UUID announcementId = UUID.randomUUID();
    private final UUID freelancerId = UUID.randomUUID();
    private final SubscriptionAttemptEvent event = new SubscriptionAttemptEvent(announcementId, freelancerId,
            Instant.now());

    private void publishedAnnouncementWithQuota() {
        Announcement announcement = mock(Announcement.class);
        when(announcement.getStatus()).thenReturn(AnnouncementStatus.PUBLISHED);
        when(announcements.findById(announcementId)).thenReturn(Mono.just(announcement));
        when(quota.hasRemainingQuota(freelancerId)).thenReturn(Mono.just(true));
        when(subscriptions.findByAnnouncementIdAndFreelancerId(announcementId, freelancerId)).thenReturn(Mono.empty());
    }

    @Test
    void anUnknownAnnouncementIsIgnored() {
        when(announcements.findById(announcementId)).thenReturn(Mono.empty());
        consumer.consumeSubscriptionAttempt(event);
        verify(subscriptions, never()).save(any());
    }

    @Test
    void aConcurrentDuplicateSubscriptionIsAbsorbedWithoutReRanking() {
        publishedAnnouncementWithQuota();
        when(subscriptions.save(any())).thenReturn(Mono.error(new DuplicateKeyException("uq")));

        consumer.consumeSubscriptionAttempt(event);

        verify(subscriptions, never()).findAllByAnnouncementId(any());
    }

    @Test
    void anyOtherFailureReachesTheContainer() {
        publishedAnnouncementWithQuota();
        when(subscriptions.save(any())).thenReturn(Mono.error(new IllegalStateException("db down")));

        assertThatThrownBy(() -> consumer.consumeSubscriptionAttempt(event)).hasMessageContaining("db down");
    }
}
