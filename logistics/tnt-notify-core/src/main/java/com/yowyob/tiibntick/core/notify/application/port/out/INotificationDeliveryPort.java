package com.yowyob.tiibntick.core.notify.application.port.out;

import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Outbound port to the platform's notification engine (the Kernel). TiiBnTick does not deliver
 * anything itself: every email, SMS, WhatsApp, push and in-app message is handed to the Kernel,
 * which owns providers, templates, user preferences and the delivery history.
 *
 * @author MANFOUO Braun
 */
public interface INotificationDeliveryPort {

    /**
     * @param channel        TiiBnTick channel
     * @param tenantId       tenant the message belongs to (falls back to the configured default)
     * @param organizationId organization scope (falls back to the configured default)
     * @param recipientId    business id of the recipient; a UUID is sent as {@code recipientUserId} so the
     *                       Kernel can apply that user's preferences and resolve devices
     * @param destination    explicit address (email, phone, ...) — optional when {@code recipientId} is a user
     * @param templateCode   code of the Kernel template to render
     * @param locale         locale tag of the template (e.g. {@code fr_CM})
     * @param variables      template variables
     * @param body           ready-to-send text, required by the Kernel and used when the template cannot be applied
     * @param priority       business priority, forwarded as metadata
     */
    record OutboundMessage(NotificationChannel channel,
            String tenantId,
            String organizationId,
            String recipientId,
            String destination,
            String templateCode,
            String locale,
            Map<String, Object> variables,
            String body,
            NotificationPriority priority) {
    }

    /** Result of a hand-over to the Kernel: its delivery id and its own status string. */
    record DeliveryReceipt(String deliveryId, String status) {
    }

    Mono<DeliveryReceipt> deliver(OutboundMessage message);
}
