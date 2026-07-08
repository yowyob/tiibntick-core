package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.yowyob.tiibntick.core.notify.application.port.out.IKernelDeliveryQueryPort;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationDeliveryRecord;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.NotificationDeliveryDto;
import reactor.core.publisher.Flux;

/**
 * Implements {@link IKernelDeliveryQueryPort} via
 * {@code GET /api/notifications/deliveries} on the Kernel notification
 * engine.
 *
 * @author MANFOUO Braun
 */
public class KernelDeliveryQueryAdapter implements IKernelDeliveryQueryPort {

    private final KernelNotificationClient client;

    public KernelDeliveryQueryAdapter(KernelNotificationClient client) {
        this.client = client;
    }

    @Override
    public Flux<NotificationDeliveryRecord> listDeliveries(String tenantId, String organizationId) {
        return client.listDeliveries(tenantId, organizationId).map(this::toDomain);
    }

    private NotificationDeliveryRecord toDomain(NotificationDeliveryDto dto) {
        return new NotificationDeliveryRecord(
                dto.id() != null ? dto.id().toString() : null,
                dto.recipientUserId() != null ? dto.recipientUserId().toString() : null,
                dto.recipientAddress(),
                KernelChannelMapper.fromKernel(dto.channel()),
                dto.templateCode(),
                dto.subject(),
                dto.body(),
                dto.status(),
                dto.providerType(),
                dto.providerMessageId(),
                dto.errorMessage(),
                dto.requestedAt(),
                dto.sentAt());
    }
}
