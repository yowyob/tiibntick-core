package com.yowyob.tiibntick.core.sales.application.port.in;

import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrder;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Use case — Link an invoice to a sales order.
 *
 * <p>Called by tnt-accounting-core (via Kafka or REST) after generating the invoice.
 * The invoiceId stored on the order can be a TNT invoice ID or a Kernel invoice ID
 * depending on the integration level.</p>
 *
 * @author MANFOUO Braun
 */
public interface LinkInvoiceToOrderUseCase {
    Mono<TntSalesOrder> linkInvoice(UUID tenantId, UUID orderId, UUID invoiceId);
}
