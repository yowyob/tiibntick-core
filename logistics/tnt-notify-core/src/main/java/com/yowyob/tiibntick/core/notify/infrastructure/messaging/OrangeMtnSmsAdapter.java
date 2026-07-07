package com.yowyob.tiibntick.core.notify.infrastructure.messaging;

import com.yowyob.tiibntick.core.notify.application.port.out.IMessageProviderPort;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * SMS adapter for MTN Mobile Money and Orange Cameroun APIs.
 * Sends transactional SMS notifications for delivery status updates.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
@Component
public class OrangeMtnSmsAdapter implements IMessageProviderPort {

    private static final Logger log = LoggerFactory.getLogger(OrangeMtnSmsAdapter.class);

    private final WebClient webClient;
    private final NotifyProperties properties;

    public OrangeMtnSmsAdapter(WebClient.Builder webClientBuilder, NotifyProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.getSmsApiBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getSmsApiToken())
                .build();
    }

    @Override
    public boolean supports(NotificationChannel channel) {
        return NotificationChannel.SMS_LOCAL.equals(channel);
    }

    @Override
    public Mono<Void> sendMessage(String destination, String content) {
        log.info("Sending SMS to {}", destination);
        // Production: POST to MTN/Orange API endpoint
        // Using a structured request body matching their SMS gateway API
        return webClient.post()
                .uri("/send")
                .bodyValue(new SmsRequest(destination, content))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.debug("SMS sent successfully to {}", destination))
                .onErrorResume(e -> {
                    log.error("SMS delivery failed to {}: {}", destination, e.getMessage());
                    return Mono.error(e);
                });
    }

    /**
     * Internal DTO for the SMS gateway API payload.
     */
    private record SmsRequest(String to, String message) {
    }
}
