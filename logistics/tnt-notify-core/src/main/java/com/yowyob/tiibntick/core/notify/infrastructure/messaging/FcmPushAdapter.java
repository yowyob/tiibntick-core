package com.yowyob.tiibntick.core.notify.infrastructure.messaging;

import com.yowyob.tiibntick.core.notify.application.port.out.IMessageProviderPort;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Firebase Cloud Messaging (FCM) adapter for mobile push notifications.
 * Integrates with FCM v1 API (OAuth2 bearer token).
 *
 * @author MANFOUO Braun
 */
@Component
public class FcmPushAdapter implements IMessageProviderPort {

    private static final Logger log = LoggerFactory.getLogger(FcmPushAdapter.class);
    private static final String FCM_API_URL = "https://fcm.googleapis.com/fcm/send";

    private final WebClient webClient;
    private final NotifyProperties properties;

    public FcmPushAdapter(WebClient.Builder builder, NotifyProperties properties) {
        this.properties = properties;
        this.webClient = builder
                .baseUrl(FCM_API_URL)
                .defaultHeader("Authorization", "key=" + properties.getFcmServerKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public boolean supports(NotificationChannel channel) {
        return NotificationChannel.PUSH_FCM.equals(channel);
    }

    @Override
    public Mono<Void> sendMessage(String fcmToken, String content) {
        log.info("Sending FCM push to token: {}...", fcmToken.substring(0, Math.min(8, fcmToken.length())));
        Map<String, Object> payload = Map.of(
                "to", fcmToken,
                "notification", Map.of(
                        "title", "TiiBnTick",
                        "body", content),
                "priority", "high");
        return webClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.debug("FCM push delivered successfully"))
                .onErrorResume(e -> {
                    log.error("FCM push failed: {}", e.getMessage());
                    return Mono.error(e);
                });
    }
}
