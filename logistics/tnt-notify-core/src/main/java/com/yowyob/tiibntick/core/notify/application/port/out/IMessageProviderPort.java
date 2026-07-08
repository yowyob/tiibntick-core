package com.yowyob.tiibntick.core.notify.application.port.out;

import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import reactor.core.publisher.Mono;

/**
 * Secondary port for dispatching messages to a specific delivery channel.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public interface IMessageProviderPort {

    /**
     * Returns true if this provider handles the given channel.
     */
    boolean supports(NotificationChannel channel);

    /**
     * Sends the message content to the physical destination.
     *
     * @param channel        the delivery channel this message is being sent on —
     *                       relevant to providers (like the Kernel bridge) that
     *                       {@link #supports(NotificationChannel)} more than one channel
     * @param tenantId       TiiBnTick tenant the notification belongs to — required by
     *                       providers that call out to multi-tenant infrastructure (Kernel)
     * @param organizationId organization scope within the tenant, may be {@code null}
     * @param destination    the target address (phone, FCM token, email)
     * @param content        the ready-to-send message string
     */
    Mono<Void> sendMessage(NotificationChannel channel, String tenantId, String organizationId,
            String destination, String content);
}
