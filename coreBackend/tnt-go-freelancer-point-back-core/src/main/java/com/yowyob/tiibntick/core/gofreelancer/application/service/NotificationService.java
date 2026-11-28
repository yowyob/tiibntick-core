package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.MatchingNotificationEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.search.document.AnnouncementDocument;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.search.document.FreelancerDocument;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.EmailPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpNotificationTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends the Go-Freelancer notifications. Every message is written once per language in the i18n catalog and
 * rendered by tnt-notify-core in the language of the recipient's account ({@code sendLocalized}); the history of
 * what was sent is notify-core's, this service keeps no notification table of its own.
 *
 * @author François-Charles ATANGA
 * @date 03/02/2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /**
     * Title and message carried by the matching event of the live stream. They are the stream's own wire
     * contract with the mobile app; the e-mail and push texts are rendered from the catalog, not from these.
     */
    static final String MATCHING_STREAM_TITLE = "Nouvelle course disponible !";
    static final String MATCHING_STREAM_MESSAGE = "Une course correspond à votre position. Cliquez pour voir les détails.";

    private final EmailPort emailPort;
    private final PushNotificationPort pushNotificationPort;
    private final KafkaEventPublisher kafkaEventPublisher;

    /**
     * Notifies eligible delivery persons about a new announcement match: e-mail and push rendered from the catalog,
     * plus the Kafka matching event that feeds the live stream. Each recipient is best effort and independent.
     */
    public Mono<Void> notifyEligibleFreelancers(List<FreelancerDocument> freelancers, AnnouncementDocument announcement) {
        log.info("Notifying {} delivery persons for Announcement {}", freelancers.size(), announcement.getId());
        return reactor.core.publisher.Flux.fromIterable(freelancers)
                .flatMap(dp -> notifyMatch(dp, announcement))
                .then();
    }

    private Mono<Void> notifyMatch(FreelancerDocument dp, AnnouncementDocument announcement) {
        Map<String, Object> params = Map.of("announcementId", String.valueOf(announcement.getId()));
        Mono<Void> email = emailPort.sendLocalized(dp.getEmail(), dp.getId(),
                GofpNotificationTemplates.ANNOUNCEMENT_AVAILABLE, params).onErrorResume(e -> {
                    log.error("Error sending email to {}: {}", dp.getEmail(), e.getMessage());
                    return Mono.empty();
                });
        Mono<Void> push = pushNotificationPort.sendLocalized(dp.getId(),
                GofpNotificationTemplates.ANNOUNCEMENT_AVAILABLE, params).onErrorResume(e -> Mono.empty());

        try {
            kafkaEventPublisher.publishMatchingNotification(MatchingNotificationEvent.builder()
                    .freelancerId(dp.getId())
                    .announcementId(announcement.getId())
                    .title(MATCHING_STREAM_TITLE)
                    .message(MATCHING_STREAM_MESSAGE)
                    .build());
        } catch (Exception e) {
            log.error("Error sending Kafka notification for delivery person {}: {}", dp.getId(), e.getMessage());
        }
        return Mono.when(email, push);
    }

    /**
     * Asks a person to rate a delivery: an e-mail and a push, both rendered from the catalog in the language of
     * the person's account.
     */
    public Mono<Void> sendEvaluationRequest(UUID personId, String personEmail, String templateKey,
            Map<String, Object> params) {
        Mono<Void> email = emailPort.sendLocalized(personEmail, personId, templateKey, params)
                .onErrorResume(e -> Mono.empty());
        Mono<Void> push = pushNotificationPort.sendLocalized(personId, templateKey, params)
                .onErrorResume(e -> Mono.empty());
        return Mono.when(email, push);
    }
}
