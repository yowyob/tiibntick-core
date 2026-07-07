package com.yowyob.tiibntick.core.sales.application.port.out;

import com.yowyob.tiibntick.core.sales.domain.model.KernelSalesOrderDto;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — Kernel Sales Order Bridge.
 *
 * <p>Defines the contract for querying the Yowyob Kernel (RT-comops-sales-core)
 * about sales orders. TiiBnTick does NOT inherit or extend Kernel SalesOrder classes.
 * Instead, it references Kernel orders by their UUID ({@code kernelSalesOrderId})
 * and only queries the Kernel for optional validation or enrichment.</p>
 *
 * <p>The coupling is intentionally <em>optional</em>: a TNT SalesOrder can exist with
 * a {@code null} {@code kernelSalesOrderId} for informal or cash-on-delivery orders
 * not registered in the Kernel ERP.</p>
 *
 * <p>Implementation: {@link com.yowyob.tiibntick.core.sales.adapter.out.kernel.KernelSalesOrderAdapter}
 * (reactive WebClient over the Kernel REST API).</p>
 *
 * @author MANFOUO Braun
 */
public interface KernelSalesOrderPort {

    /**
     * Fetches a sales order from the Kernel by its UUID.
     *
     * <p>Returns {@code Mono.empty()} if the order does not exist in the Kernel,
     * allowing callers to treat the reference as optional.</p>
     *
     * @param kernelSalesOrderId the Kernel sales order UUID
     * @return the kernel sales order data, or {@code Mono.empty()} if not found
     */
    Mono<KernelSalesOrderDto> findByKernelSalesOrderId(UUID kernelSalesOrderId);

    /**
     * Resolves a Kernel sales order by client third-party, organization, and tenant.
     *
     * <p>Used by the application service to optionally resolve a {@code kernelSalesOrderId}
     * before persisting a new TNT sales order. Returns {@code Mono.empty()} when no
     * Kernel counterpart exists (informal transaction).</p>
     *
     * @param tenantId           tenant context
     * @param clientThirdPartyId Kernel third-party UUID of the client
     * @param organizationId     Kernel organization UUID
     * @return the matching Kernel sales order, or {@code Mono.empty()} if none found
     */
    Mono<KernelSalesOrderDto> findByClientAndOrganization(UUID tenantId,
                                                           UUID clientThirdPartyId,
                                                           UUID organizationId);
}
