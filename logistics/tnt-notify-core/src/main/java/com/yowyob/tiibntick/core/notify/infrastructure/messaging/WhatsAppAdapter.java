package com.yowyob.tiibntick.core.notify.infrastructure.messaging;

import com.yowyob.tiibntick.core.notify.application.port.out.IMessageProviderPort;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * WhatsApp Business Cloud API adapter.
 * Sends text messages via the Meta Graph API.
 * Requires a verified WhatsApp Business account.
 *
 * <p>Only active when {@code tnt.notify.kernel.enabled=false} — by default,
 * WhatsApp delivery is delegated to the Kernel notification engine instead
 * (see {@link com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.KernelDeliveryProviderAdapter}).
 *
 * @author MANFOUO Braun
 */
@Component
@ConditionalOnProperty(prefix = "tnt.notify.kernel", name = "enabled", havingValue = "false")
public class WhatsAppAdapter implements IMessageProviderPort {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppAdapter.class);

    private final WebClient webClient;
    private final NotifyProperties properties;

    public WhatsAppAdapter(WebClient.Builder builder, NotifyProperties properties) {
        this.properties = properties;
        this.webClient = builder
                .baseUrl(properties.getWhatsappApiBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getWhatsappAccessToken())
                .build();
    }

    @Override
    public boolean supports(NotificationChannel channel) {
        return NotificationChannel.WHATSAPP.equals(channel);
    }

    @Override
    public Mono<Void> sendMessage(NotificationChannel channel, String tenantId, String organizationId,
            String phoneNumber, String content) {
        log.info("Sending WhatsApp message to {}", phoneNumber);
        // Meta Graph API v19.0 — send text message
        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", phoneNumber,
                "type", "text",
                "text", Map.of("body", content));
        return webClient.post()
                .uri("/{phoneNumberId}/messages", properties.getWhatsappPhoneNumberId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.debug("WhatsApp message sent to {}", phoneNumber))
                .onErrorResume(e -> {
                    log.error("WhatsApp delivery failed to {}: {}", phoneNumber, e.getMessage());
                    return Mono.error(e);
                });
    }
}
