package com.yowyob.tiibntick.core.inventory.adapter.out.kernel;

import com.yowyob.tiibntick.core.inventory.application.port.out.KernelInventoryPort;
import com.yowyob.tiibntick.core.inventory.domain.model.KernelStockEntryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter — Kernel Inventory Bridge via reactive HTTP (WebClient).
 *
 * <p>Implements {@link KernelInventoryPort} by calling the Yowyob Kernel REST API
 * (RT-comops-inventory-core). All calls are non-blocking (Reactor Mono).</p>
 *
 * <p>Design contract:
 * <ul>
 *   <li>HTTP 404 from Kernel → returns {@code Mono.empty()} (not an error in TNT context).</li>
 *   <li>Network errors → returns {@code Mono.empty()} with a WARN log, allowing TNT
 *       to continue without the optional Kernel link.</li>
 * </ul>
 * </p>
 *
 * <p>The WebClient bean is provided by
 * {@link com.yowyob.tiibntick.core.inventory.config.KernelWebClientConfig}.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class KernelInventoryAdapter implements KernelInventoryPort {

    private static final Logger log = LoggerFactory.getLogger(KernelInventoryAdapter.class);

    /** Path template to fetch a stock entry by its Kernel UUID. */
    private static final String STOCK_ENTRY_BY_ID_PATH = "/inventory/stock-entries/{id}";

    /** Path template to query a stock entry by product + warehouse + tenant. */
    //private static final String STOCK_ENTRY_BY_PRODUCT_PATH =
    //        "/inventory/stock-entries?productId={productId}&warehouseId={warehouseId}&tenantId={tenantId}";

    private final WebClient kernelWebClient;

    /**
     * @param kernelWebClient reactive WebClient pre-configured with the Kernel base URL.
     *                        Qualified as "kernelWebClient" to avoid conflicts with other WebClient beans.
     */
    public KernelInventoryAdapter(@Qualifier("kernelWebClient") WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    /**
     * {@inheritDoc}
     *
     * <p>GET /inventory/stock-entries/{kernelStockEntryId}</p>
     */
    @Override
    public Mono<KernelStockEntryDto> findByKernelStockEntryId(UUID kernelStockEntryId) {
        return kernelWebClient.get()
                .uri(STOCK_ENTRY_BY_ID_PATH, kernelStockEntryId)
                .retrieve()
                .bodyToMono(KernelStockEntryDto.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    log.debug("Kernel stock entry not found: {}", kernelStockEntryId);
                    return Mono.empty();
                })
                .onErrorResume(Exception.class, ex -> {
                    log.warn("Kernel inventory bridge error for stockEntryId={}: {}",
                            kernelStockEntryId, ex.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>GET /inventory/stock-entries?productId=...&warehouseId=...&tenantId=...</p>
     */
    @Override
    public Mono<KernelStockEntryDto> findByProductAndWarehouse(UUID productId,
                                                                UUID warehouseId,
                                                                UUID tenantId) {
        return kernelWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/inventory/stock-entries")
                        .queryParam("productId", productId)
                        .queryParam("warehouseId", warehouseId)
                        .queryParam("tenantId", tenantId)
                        .build())
                .retrieve()
                .bodyToMono(KernelStockEntryDto.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    log.debug("No Kernel stock entry for product={} warehouse={}", productId, warehouseId);
                    return Mono.empty();
                })
                .onErrorResume(Exception.class, ex -> {
                    log.warn("Kernel inventory bridge error for product={} warehouse={}: {}",
                            productId, warehouseId, ex.getMessage());
                    return Mono.empty();
                });
    }
}
