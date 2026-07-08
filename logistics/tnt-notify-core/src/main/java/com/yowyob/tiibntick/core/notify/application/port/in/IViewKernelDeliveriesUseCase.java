package com.yowyob.tiibntick.core.notify.application.port.in;

import com.yowyob.tiibntick.core.notify.domain.model.NotificationDeliveryRecord;
import reactor.core.publisher.Flux;

/**
 * Primary port for admin/troubleshooting visibility into deliveries tracked
 * directly by the Kernel notification engine.
 *
 * @author MANFOUO Braun
 */
public interface IViewKernelDeliveriesUseCase {

    Flux<NotificationDeliveryRecord> listKernelDeliveries(String tenantId, String organizationId);
}
