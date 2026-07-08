package com.yowyob.tiibntick.core.notify.application.service;

import com.yowyob.tiibntick.core.notify.application.port.in.IManageNotificationProvidersUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.IManageNotificationRemindersUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.IManageNotificationTemplatesUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.IViewKernelDeliveriesUseCase;
import com.yowyob.tiibntick.core.notify.application.port.out.IKernelDeliveryQueryPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationProviderAdminPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationReminderPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationTemplateAdminPort;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationDeliveryRecord;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationProviderConfig;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationReminder;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationTemplateConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Thin application service exposing the Kernel notification engine's
 * administrative surface (providers, templates, reminders, raw deliveries)
 * to TiiBnTick callers. Pure pass-through to the outbound ports — no local
 * business rules — mirroring the "thin bridge" style already used by
 * tnt-auth-core / tnt-roles-core for other Kernel-delegated concerns.
 *
 * @author MANFOUO Braun
 */
public class NotificationAdminService implements
        IManageNotificationProvidersUseCase,
        IManageNotificationTemplatesUseCase,
        IManageNotificationRemindersUseCase,
        IViewKernelDeliveriesUseCase {

    private final INotificationProviderAdminPort providerPort;
    private final INotificationTemplateAdminPort templatePort;
    private final INotificationReminderPort reminderPort;
    private final IKernelDeliveryQueryPort deliveryQueryPort;

    public NotificationAdminService(INotificationProviderAdminPort providerPort,
            INotificationTemplateAdminPort templatePort,
            INotificationReminderPort reminderPort,
            IKernelDeliveryQueryPort deliveryQueryPort) {
        this.providerPort = providerPort;
        this.templatePort = templatePort;
        this.reminderPort = reminderPort;
        this.deliveryQueryPort = deliveryQueryPort;
    }

    @Override
    public Flux<NotificationProviderConfig> listProviders(String tenantId, String organizationId) {
        return providerPort.list(tenantId, organizationId);
    }

    @Override
    public Mono<NotificationProviderConfig> saveProvider(String tenantId, String organizationId,
            NotificationProviderConfig config) {
        return providerPort.save(tenantId, organizationId, config);
    }

    @Override
    public Flux<NotificationTemplateConfig> listTemplates(String tenantId, String organizationId) {
        return templatePort.list(tenantId, organizationId);
    }

    @Override
    public Mono<NotificationTemplateConfig> saveTemplate(String tenantId, String organizationId,
            NotificationTemplateConfig config) {
        return templatePort.save(tenantId, organizationId, config);
    }

    @Override
    public Flux<NotificationReminder> listReminders(String tenantId, String organizationId) {
        return reminderPort.list(tenantId, organizationId);
    }

    @Override
    public Mono<NotificationReminder> scheduleReminder(String tenantId, String organizationId,
            NotificationReminder reminder) {
        return reminderPort.save(tenantId, organizationId, reminder);
    }

    @Override
    public Flux<NotificationDeliveryRecord> listKernelDeliveries(String tenantId, String organizationId) {
        return deliveryQueryPort.listDeliveries(tenantId, organizationId);
    }
}
