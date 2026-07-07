package com.yowyob.tiibntick.core.sales.application.port.in;

import com.yowyob.tiibntick.core.sales.domain.model.ReturnReason;
import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrder;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Use case — Return a sales order (DISPATCHED | IN_DELIVERY → RETURNED).
 *
 * <p>Records the return reason (e.g. RECIPIENT_ABSENT, DAMAGED_GOODS) and a note.
 * Typically triggered by the Kafka event {@code tnt.delivery.mission.failed}.</p>
 *
 * @author MANFOUO Braun
 */
public interface ReturnSalesOrderUseCase {
    Mono<TntSalesOrder> returnOrder(UUID tenantId, UUID orderId, ReturnReason reason, String note);
}
