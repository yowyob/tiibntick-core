package com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka;

import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.EventPublisher;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.AnnouncementPublishedEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.FreelancerCreatedEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.FreelancerValidatedEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.MatchingNotificationEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.SubscriptionAttemptEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Outbound adapter: implements the EventPublisher port using Kafka.
 * Translates shared events to Kafka messages.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisherAdapter implements EventPublisher {

    private static final String TOPIC_DELIVERY_PERSON_CREATED = "delivery-person-created";
    private static final String TOPIC_DELIVERY_PERSON_VALIDATED = "delivery-person-validated";
    private static final String TOPIC_ANNOUNCEMENT_PUBLISHED = "announcement-published";
    private static final String TOPIC_SUBSCRIPTION_ATTEMPTS = "subscription-attempts";
    private static final String TOPIC_MATCHING_NOTIFICATIONS = "matching-notifications";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishFreelancerCreated(FreelancerCreatedEvent event) {
        log.info("Publishing FreelancerCreatedEvent for ID: {}", event.getFreelancerId());
        kafkaTemplate.send(TOPIC_DELIVERY_PERSON_CREATED, event.getFreelancerId().toString(), event);
    }

    @Override
    public void publishFreelancerValidated(FreelancerValidatedEvent event) {
        log.info("Publishing FreelancerValidatedEvent for ID: {}, approved: {}",
                event.getFreelancerId(), event.isApproved());
        kafkaTemplate.send(TOPIC_DELIVERY_PERSON_VALIDATED, event.getFreelancerId().toString(), event);
    }

    @Override
    public void publishAnnouncementPublished(AnnouncementPublishedEvent event) {
        log.info("Publishing AnnouncementPublishedEvent for announcement ID: {}",
                event.getAnnouncement().getId());
        kafkaTemplate.send(TOPIC_ANNOUNCEMENT_PUBLISHED, event.getAnnouncement().getId().toString(), event);
    }

    @Override
    public void publishSubscriptionAttempt(SubscriptionAttemptEvent event) {
        log.info("Publishing SubscriptionAttemptEvent for freelancer: {} and announcement: {}",
                event.getFreelancerId(), event.getAnnouncementId());
        kafkaTemplate.send(TOPIC_SUBSCRIPTION_ATTEMPTS, event.getAnnouncementId().toString(), event);
    }

    @Override
    public void publishMatchingNotification(MatchingNotificationEvent event) {
        log.info("Publishing MatchingNotificationEvent for freelancer: {} and announcement: {}",
                event.getFreelancerId(), event.getAnnouncementId());
        kafkaTemplate.send(TOPIC_MATCHING_NOTIFICATIONS, event.getFreelancerId().toString(), event);
    }
}
