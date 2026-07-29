package com.yowyob.tiibntick.core.gofreelancer.domain.port.out;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.AnnouncementPublishedEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.FreelancerCreatedEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.FreelancerValidatedEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.MatchingNotificationEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.SubscriptionAttemptEvent;

/**
 * Outbound port for publishing domain events.
 * Decouples application logic from the Kafka infrastructure.
 */
public interface EventPublisher {

    void publishFreelancerCreated(FreelancerCreatedEvent event);

    void publishFreelancerValidated(FreelancerValidatedEvent event);

    void publishAnnouncementPublished(AnnouncementPublishedEvent event);

    void publishSubscriptionAttempt(SubscriptionAttemptEvent event);

    void publishMatchingNotification(MatchingNotificationEvent event);
}
