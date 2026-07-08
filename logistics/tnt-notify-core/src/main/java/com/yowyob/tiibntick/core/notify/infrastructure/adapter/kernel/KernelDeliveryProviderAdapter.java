package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.yowyob.tiibntick.core.notify.application.port.out.IMessageProviderPort;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.SendNotificationRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Delegates physical notification delivery (email, SMS, WhatsApp, push) to
 * the Kernel's generic notification engine ({@code POST
 * /api/notifications/deliveries}) instead of calling SMTP/MTN-Orange/Meta/FCM
 * directly. The Kernel owns provider configuration
 * ({@code /api/notifications/providers}) and the physical send.
 *
 * <p>{@code IN_APP_WEBSOCKET} is intentionally NOT supported here — an
 * in-app push requires a live STOMP session on THIS instance and is handled
 * locally by {@link com.yowyob.tiibntick.core.notify.infrastructure.messaging.InAppWebSocketAdapter}.
 *
 * <p>Active by default; set {@code tnt.notify.kernel.enabled=false} to fall
 * back to the direct-vendor adapters instead (see
 * {@link com.yowyob.tiibntick.core.notify.config.NotifyCoreAutoConfiguration}).
 *
 * @author MANFOUO Braun
 */
public class KernelDeliveryProviderAdapter implements IMessageProviderPort {

    private static final Logger log = LoggerFactory.getLogger(KernelDeliveryProviderAdapter.class);

    private static final Set<NotificationChannel> SUPPORTED_CHANNELS = Set.of(
            NotificationChannel.EMAIL,
            NotificationChannel.SMS_LOCAL,
            NotificationChannel.WHATSAPP,
            NotificationChannel.PUSH_FCM);

    private final KernelNotificationClient client;

    public KernelDeliveryProviderAdapter(KernelNotificationClient client) {
        this.client = client;
    }

    @Override
    public boolean supports(NotificationChannel channel) {
        return SUPPORTED_CHANNELS.contains(channel);
    }

    @Override
    public Mono<Void> sendMessage(NotificationChannel channel, String tenantId, String organizationId,
            String destination, String content) {
        log.info("Delegating {} notification delivery to Kernel → destination={}", channel, destination);
        SendNotificationRequestDto request = new SendNotificationRequestDto(
                null,
                destination,
                KernelChannelMapper.toKernel(channel),
                null,
                null,
                content,
                null,
                null);
        return client.send(tenantId, organizationId, request)
                .doOnSuccess(delivery -> log.debug("Kernel accepted delivery {} (status={})",
                        delivery.id(), delivery.status()))
                .then()
                .onErrorResume(e -> {
                    log.error("Kernel notification delivery failed for {}: {}", destination, e.getMessage());
                    return Mono.error(e);
                });
    }
}
