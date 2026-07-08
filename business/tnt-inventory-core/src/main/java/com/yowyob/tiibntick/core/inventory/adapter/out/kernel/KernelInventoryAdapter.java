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
     * <p><b>Known gap</b> (see {@code docs/architecture/decisions.md} ADR-011 and
     * {@code docs/knowledge/known-issues.md} #11): the Kernel's inventory API has no
     * "stock entry by id" resource at all — stock is a live balance computed from a
     * movement ledger (see {@code inventory-movement-controller}), not a persisted
     * record with its own stable id. The closest Kernel equivalent is
     * {@code GET /api/inventory/movements/balance?organizationId&agencyId&productId},
     * which needs a Kernel {@code organizationId}/{@code agencyId} that this module's
     * {@code warehouseId}/{@code tenantId} don't currently resolve to (no {@code Warehouse}
     * domain concept exists locally to bridge them). Deliberately left unfixed rather than
     * wired to the wrong identifiers — always resolves empty (fail-open), same net behavior
     * as before this review.</p>
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
