package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.SendNotificationRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Hands every notification to the Kernel notification engine ({@code POST /api/notifications/deliveries}).
 * The Kernel owns the providers (SMTP, Twilio, Meta WhatsApp, Firebase, WebSocket), the user preferences
 * and the delivery history; TiiBnTick sends nothing itself.
 *
 * <p>A UUID recipient is sent as {@code recipientUserId} — for push and in-app channels the Kernel resolves
 * the user's devices and sessions from it, and it can apply that user's channel preferences. The explicit
 * address (email, phone) is added when the caller has one.
 *
 * @author MANFOUO Braun
 */
public class KernelDeliveryAdapter implements INotificationDeliveryPort {

    private static final Logger log = LoggerFactory.getLogger(KernelDeliveryAdapter.class);

    private final KernelNotificationClient client;
    private final KernelTemplateRegistrar templates;
    private final boolean templatesEnabled;

    public KernelDeliveryAdapter(KernelNotificationClient client, KernelTemplateRegistrar templates,
            boolean templatesEnabled) {
        this.client = client;
        this.templates = templates;
        this.templatesEnabled = templatesEnabled;
    }

    @Override
    public Mono<DeliveryReceipt> deliver(OutboundMessage m) {
        String kernelChannel = KernelChannelMapper.toKernel(m.channel());
        Mono<Void> registration = templatesEnabled && m.templateCode() != null
                ? templates.ensureRegistered(m.tenantId(), m.organizationId(), m.templateCode(), kernelChannel,
                        m.locale())
                : Mono.empty();
        return registration.then(Mono.defer(() -> client.send(m.tenantId(), m.organizationId(), toRequest(m, kernelChannel))))
                .doOnNext(d -> log.debug("Kernel accepted notification {} on {} (status={})", d.id(), kernelChannel,
                        d.status()))
                .map(d -> new DeliveryReceipt(d.id() != null ? d.id().toString() : null, d.status()));
    }

    private SendNotificationRequestDto toRequest(OutboundMessage m, String kernelChannel) {
        UUID recipientUserId = asUuid(m.recipientId());
        // For channels addressed to a person (push, in-app) the user id is enough; mail/SMS/WhatsApp need an address.
        String address = userAddressedChannel(m.channel()) && recipientUserId != null ? null : m.destination();
        boolean useTemplate = templatesEnabled && m.templateCode() != null;
        return new SendNotificationRequestDto(
                recipientUserId,
                address,
                kernelChannel,
                useTemplate ? m.templateCode() : null,
                m.subject(),
                m.body(),
                useTemplate ? m.variables() : null,
                m.priority() != null ? Map.of("priority", m.priority().name()) : null);
    }

    private static boolean userAddressedChannel(NotificationChannel channel) {
        return channel == NotificationChannel.PUSH_FCM || channel == NotificationChannel.IN_APP_WEBSOCKET;
    }

    private static UUID asUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
