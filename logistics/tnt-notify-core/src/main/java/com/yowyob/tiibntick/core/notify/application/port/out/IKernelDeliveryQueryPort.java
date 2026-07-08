package com.yowyob.tiibntick.core.notify.application.port.out;

import com.yowyob.tiibntick.core.notify.domain.model.NotificationDeliveryRecord;
import reactor.core.publisher.Flux;

/**
 * Secondary port for read-only visibility into deliveries tracked by the
 * Kernel notification engine — complements {@link ISearchNotificationsPort},
 * which only sees TiiBnTick's own local send pipeline.
 *
 * @author MANFOUO Braun
 */
public interface IKernelDeliveryQueryPort {

    Flux<NotificationDeliveryRecord> listDeliveries(String tenantId, String organizationId);
}
