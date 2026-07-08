package com.yowyob.tiibntick.core.sales.adapter.out.kernel;

import com.yowyob.tiibntick.common.kernel.KernelResponses;
import com.yowyob.tiibntick.core.sales.application.port.out.KernelSalesOrderPort;
import com.yowyob.tiibntick.core.sales.domain.model.KernelSalesOrderDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/**
 * Outbound adapter — Kernel Sales Bridge via reactive HTTP (WebClient).
 *
 * <p>Implements {@link KernelSalesOrderPort} by calling the Yowyob Kernel REST API
 * (RT-comops-sales-core). All calls are non-blocking (Reactor Mono).</p>
 *
 * <p>Design contract:
 * <ul>
 *   <li>HTTP 404 from Kernel → {@code Mono.empty()} (Kernel link is optional in TNT).</li>
 *   <li>Network or timeout errors → {@code Mono.empty()} with WARN log, allowing TNT
 *       to continue without the optional Kernel link (resilient by design).</li>
 * </ul>
 * </p>
 *
 * <p>WebClient bean provided by
 * {@link com.yowyob.tiibntick.core.common.config.KernelWebClientConfig}.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class KernelSalesOrderAdapter implements KernelSalesOrderPort {

    private static final Logger log = LoggerFactory.getLogger(KernelSalesOrderAdapter.class);

    private final WebClient kernelWebClient;

    /**
     * @param kernelWebClient reactive WebClient pre-configured with the Kernel base URL.
     *                        Qualified as "kernelWebClient" to avoid bean conflicts.
     */
    public KernelSalesOrderAdapter(@Qualifier("kernelWebClient") WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    /**
     * {@inheritDoc}
     *
     * <p>GET /api/sales/orders/{kernelSalesOrderId}. Unused today (no caller) but kept —
     * unlike {@code tnt-inventory-core}'s equivalent (see ADR-011), this one has a real
     * backing Kernel resource.</p>
     */
    @Override
    public Mono<KernelSalesOrderDto> findByKernelSalesOrderId(UUID kernelSalesOrderId) {
        var responseSpec = kernelWebClient.get()
                .uri("/api/sales/orders/{id}", kernelSalesOrderId)
                .retrieve();
        return KernelResponses.unwrapObject(responseSpec, KernelSalesOrderDto.class, log,
                "findByKernelSalesOrderId " + kernelSalesOrderId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>GET /api/sales/orders?organizationId=... — the only filter the Kernel actually
     * supports (see {@code sales-order-controller} in docs/kernel-api/endpoints.md; there is
     * no {@code clientThirdPartyId}/{@code tenantId}/{@code latest} query support). Filters
     * client-side for the requested client + tenant. "Latest" can't be honored precisely —
     * the Kernel's {@code SalesOrderResponse} carries no timestamp — so this takes the last
     * matching entry in Kernel's returned order as a best-effort approximation.</p>
     */
    @Override
    public Mono<KernelSalesOrderDto> findByClientAndOrganization(UUID tenantId,
                                                                   UUID clientThirdPartyId,
                                                                   UUID organizationId) {
        var responseSpec = kernelWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/sales/orders")
                        .queryParam("organizationId", organizationId)
                        .build())
                .retrieve();
        return KernelResponses.unwrapList(responseSpec, KernelSalesOrderDto.class, log,
                        "findByClientAndOrganization client=" + clientThirdPartyId + " org=" + organizationId)
                .filter(dto -> Objects.equals(dto.tenantId(), tenantId)
                        && Objects.equals(dto.clientThirdPartyId(), clientThirdPartyId))
                .takeLast(1)
                .next();
    }
}
