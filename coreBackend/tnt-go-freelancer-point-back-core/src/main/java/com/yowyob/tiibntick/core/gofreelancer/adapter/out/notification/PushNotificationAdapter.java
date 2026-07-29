package com.yowyob.tiibntick.core.gofreelancer.adapter.out.notification;

import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.PushNotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: implements the PushNotificationPort.
 * Currently simulates push notifications via logs.
 * Designed to integrate with Firebase Admin SDK.
 */
@Slf4j
@Component
public class PushNotificationAdapter implements PushNotificationPort {

    @Override
    public Mono<Void> sendPushNotification(UUID userId, String title, String message) {
        return Mono.fromRunnable(() -> {
            // TODO: Integrate Firebase Admin SDK here
            log.info("----------------------------------------------------------------");
            log.info("[SIMULATION] PUSH NOTIFICATION SENT");
            log.info("To UserID : {}", userId);
            log.info("Title     : {}", title);
            log.info("Message   : {}", message);
            log.info("----------------------------------------------------------------");
        });
    }
}
