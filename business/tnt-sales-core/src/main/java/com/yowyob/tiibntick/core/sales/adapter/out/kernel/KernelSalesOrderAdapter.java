package com.yowyob.tiibntick.core.sales.adapter.out.kernel;

import com.yowyob.tiibntick.core.sales.application.port.out.KernelSalesOrderPort;
import com.yowyob.tiibntick.core.sales.domain.model.KernelSalesOrderDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

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
     * <p>GET /sales/orders/{kernelSalesOrderId}</p>
     */
    @Override
    public Mono<KernelSalesOrderDto> findByKernelSalesOrderId(UUID kernelSalesOrderId) {
        return kernelWebClient.get()
                .uri("/sales/orders/{id}", kernelSalesOrderId)
                .retrieve()
                .bodyToMono(KernelSalesOrderDto.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    log.debug("Kernel sales order not found: {}", kernelSalesOrderId);
                    return Mono.empty();
                })
                .onErrorResume(Exception.class, ex -> {
                    log.warn("Kernel sales bridge error for orderId={}: {}",
                            kernelSalesOrderId, ex.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>GET /sales/orders?tenantId=...&clientThirdPartyId=...&organizationId=...&latest=true</p>
     */
    @Override
    public Mono<KernelSalesOrderDto> findByClientAndOrganization(UUID tenantId,
                                                                   UUID clientThirdPartyId,
                                                                   UUID organizationId) {
        return kernelWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/sales/orders")
                        .queryParam("tenantId", tenantId)
                        .queryParam("clientThirdPartyId", clientThirdPartyId)
                        .queryParam("organizationId", organizationId)
                        .queryParam("latest", true)
                        .build())
                .retrieve()
                .bodyToMono(KernelSalesOrderDto.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    log.debug("No Kernel sales order for client={} org={}", clientThirdPartyId, organizationId);
                    return Mono.empty();
                })
                .onErrorResume(Exception.class, ex -> {
                    log.warn("Kernel sales bridge error for client={} org={}: {}",
                            clientThirdPartyId, organizationId, ex.getMessage());
                    return Mono.empty();
                });
    }
}
