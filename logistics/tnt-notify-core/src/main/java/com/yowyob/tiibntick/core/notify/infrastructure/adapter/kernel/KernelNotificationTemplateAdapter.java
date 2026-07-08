package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.yowyob.tiibntick.core.notify.application.port.out.INotificationTemplateAdminPort;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationTemplateConfig;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.NotificationTemplateDto;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.SaveTemplateRequestDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implements {@link INotificationTemplateAdminPort} via the Kernel
 * notification engine's {@code /api/notifications/templates} endpoints.
 *
 * @author MANFOUO Braun
 */
public class KernelNotificationTemplateAdapter implements INotificationTemplateAdminPort {

    private final KernelNotificationClient client;

    public KernelNotificationTemplateAdapter(KernelNotificationClient client) {
        this.client = client;
    }

    @Override
    public Flux<NotificationTemplateConfig> list(String tenantId, String organizationId) {
        return client.listTemplates(tenantId, organizationId).map(this::toDomain);
    }

    @Override
    public Mono<NotificationTemplateConfig> save(String tenantId, String organizationId,
            NotificationTemplateConfig config) {
        SaveTemplateRequestDto request = new SaveTemplateRequestDto(
                config.code(),
                KernelChannelMapper.toKernel(config.channel()),
                config.locale(),
                config.subjectTemplate(),
                config.bodyTemplate(),
                config.active());
        return client.saveTemplate(tenantId, organizationId, request).map(this::toDomain);
    }

    private NotificationTemplateConfig toDomain(NotificationTemplateDto dto) {
        return new NotificationTemplateConfig(
                dto.id(),
                dto.code(),
                KernelChannelMapper.fromKernel(dto.channel()),
                dto.locale(),
                dto.subjectTemplate(),
                dto.bodyTemplate(),
                Boolean.TRUE.equals(dto.active()),
                dto.createdAt(),
                dto.updatedAt());
    }
}
