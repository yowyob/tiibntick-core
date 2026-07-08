package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.yowyob.tiibntick.core.notify.application.port.out.INotificationProviderAdminPort;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationProviderConfig;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.NotificationProviderDto;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.SaveProviderRequestDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implements {@link INotificationProviderAdminPort} via the Kernel
 * notification engine's {@code /api/notifications/providers} endpoints.
 *
 * @author MANFOUO Braun
 */
public class KernelNotificationProviderConfigAdapter implements INotificationProviderAdminPort {

    private final KernelNotificationClient client;

    public KernelNotificationProviderConfigAdapter(KernelNotificationClient client) {
        this.client = client;
    }

    @Override
    public Flux<NotificationProviderConfig> list(String tenantId, String organizationId) {
        return client.listProviders(tenantId, organizationId).map(this::toDomain);
    }

    @Override
    public Mono<NotificationProviderConfig> save(String tenantId, String organizationId,
            NotificationProviderConfig config) {
        SaveProviderRequestDto request = new SaveProviderRequestDto(
                KernelChannelMapper.toKernel(config.channel()),
                config.type(),
                config.name(),
                config.defaultProvider(),
                config.active(),
                config.configurationJson());
        return client.saveProvider(tenantId, organizationId, request).map(this::toDomain);
    }

    private NotificationProviderConfig toDomain(NotificationProviderDto dto) {
        return new NotificationProviderConfig(
                dto.id(),
                KernelChannelMapper.fromKernel(dto.channel()),
                dto.type(),
                dto.name(),
                Boolean.TRUE.equals(dto.defaultProvider()),
                Boolean.TRUE.equals(dto.active()),
                dto.configurationJson(),
                dto.createdAt(),
                dto.updatedAt());
    }
}
