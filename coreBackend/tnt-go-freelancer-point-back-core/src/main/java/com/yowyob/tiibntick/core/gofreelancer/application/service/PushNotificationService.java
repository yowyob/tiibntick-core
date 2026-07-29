package com.yowyob.tiibntick.core.gofreelancer.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service for sending push notifications.
 * Currently simulates sending notifications via logs.
 * Designed to be integrated with Firebase Admin SDK.
 *
 * @author François-Charles ATANGA
 * @date 03/02/2026
 */
@Slf4j
@Service
public class PushNotificationService {

    /**
     * Sends a push notification to a specific user.
     *
     * @param userId  The UUID of the recipient user.
     * @param title   The title of the notification.
     * @param message The body message of the notification.
     * @return A Mono<Void> signaling completion.
     */
    public Mono<Void> sendPushNotification(UUID userId, String title, String message) {
        return Mono.fromRunnable(() -> {
            // TODO: Integrate Firebase Admin SDK here
            // String fcmToken = tokenService.getFcmToken(userId);
            // Message msg = Message.builder().setToken(fcmToken)...build();
            // FirebaseMessaging.getInstance().send(msg);

            log.info("----------------------------------------------------------------");
            log.info("[SIMULATION] PUSH NOTIFICATION SENT");
            log.info("To UserID : {}", userId);
            log.info("Title     : {}", title);
            log.info("Message   : {}", message);
            log.info("----------------------------------------------------------------");
        });
    }
}
