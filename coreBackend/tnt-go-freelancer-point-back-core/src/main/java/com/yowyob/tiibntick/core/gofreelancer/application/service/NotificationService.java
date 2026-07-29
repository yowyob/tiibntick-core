package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.search.document.AnnouncementDocument;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.search.document.FreelancerDocument;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Notification;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.notification.NotificationStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.notification.NotificationType;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.NotificationRepository;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.MatchingNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service responsible for managing and sending notifications.
 * Orchestrates Email, Push, and DB persistence.
 *
 * @author François-Charles ATANGA
 * @date 03/02/2026
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final PushNotificationService pushNotificationService;
    private final KafkaEventPublisher kafkaEventPublisher;

    /**
     * Notifies eligible delivery persons about a new announcement match.
     *
     * @param freelancers List of eligible delivery persons.
     * @param announcement    The matched announcement.
     * @return A Flux of saved Notifications.
     */
    @Transactional("connectionFactoryTransactionManager")
    public Flux<Notification> notifyEligibleFreelancers(List<FreelancerDocument> freelancers,
            AnnouncementDocument announcement) {
        log.info("Notifying {} delivery persons for Announcement {}", freelancers.size(), announcement.getId());

        return Flux.fromIterable(freelancers)
                .flatMap(dp -> sendNotification(dp, announcement));
    }

    private Mono<Notification> sendNotification(FreelancerDocument dp, AnnouncementDocument announcement) {
        String title = "Nouvelle course disponible !";
        String message = "Une course correspond à votre position. Cliquez pour voir les détails.";

        // 1. Create Notification Entity
        Notification notification = new Notification();
        notification.setPersonId(dp.getPersonId());
        notification.setNotificationType(NotificationType.NEW_ANNOUNCEMENT);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setNotificationStatus(NotificationStatus.SENT);

        // 2. Persist to DB
        return notificationRepository.save(notification)
                .flatMap(savedNotification -> {
                    // 3. Send Email
                    Mono<Void> emailMono = emailService.sendSimpleMessageReactive(
                            dp.getEmail(),
                            title,
                            message + "\n\nAnnonce ID: " + announcement.getId()).onErrorResume(e -> {
                                log.error("Error sending email to {}: {}", dp.getEmail(), e.getMessage());
                                return Mono.empty();
                            });

                    // 4. Send Push Notification
                    Mono<Void> pushMono = pushNotificationService.sendPushNotification(
                            dp.getId(),
                            title,
                            message).onErrorResume(e -> {
                                return Mono.empty();
                            });

                    // 5. Send Kafka Notification
                    MatchingNotificationEvent kafkaEvent = MatchingNotificationEvent.builder()
                            .freelancerId(dp.getId())
                            .announcementId(announcement.getId())
                            .title(title)
                            .message(message)
                            .build();

                    try {
                        kafkaEventPublisher.publishMatchingNotification(kafkaEvent);
                    } catch (Exception e) {
                        log.error("Error sending Kafka notification for delivery person {}: {}", dp.getId(),
                                e.getMessage());
                    }

                    // execute side effects without blocking the return of the saved notification
                    return Mono.when(emailMono, pushMono)
                            .thenReturn(savedNotification);
                });
    }

    public Mono<Void> sendEvaluationRequest(java.util.UUID personId, java.util.UUID deliveryId, String personEmail, String title, String message) {
        Notification notification = new Notification();
        notification.setPersonId(personId);
        notification.setNotificationType(NotificationType.EVALUATION_REQUEST);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setNotificationStatus(NotificationStatus.SENT);

        return notificationRepository.save(notification)
                .flatMap(saved -> {
                    Mono<Void> emailMono = emailService.sendSimpleMessageReactive(
                            personEmail, title, message + "\n\nDelivery ID: " + deliveryId)
                            .onErrorResume(e -> Mono.empty());
                    Mono<Void> pushMono = pushNotificationService.sendPushNotification(personId, title, message)
                            .onErrorResume(e -> Mono.empty());
                    return Mono.when(emailMono, pushMono);
                });
    }
}
