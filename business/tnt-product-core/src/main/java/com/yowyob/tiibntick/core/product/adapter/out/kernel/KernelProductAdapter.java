package com.yowyob.tiibntick.core.product.adapter.out.kernel;

import com.yowyob.tiibntick.core.product.application.port.out.KernelProductPort;
import com.yowyob.tiibntick.core.product.domain.model.KernelProductDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * WebClient-based adapter implementing {@link KernelProductPort}.
 *
 * <p>Communicates with the Yowyob Kernel product catalog (RT-comops-product-core) via HTTP REST
 * through the KernelBridge {@link WebClient} configured in {@code ProductCoreAutoConfiguration}.
 *
 * <p>All calls are fail-open: network errors and 404s return empty/false rather than propagating
 * exceptions, ensuring TNT product operations are not blocked by transient Kernel unavailability.
 *
 * @author MANFOUO Braun
 */
@Component
public class KernelProductAdapter implements KernelProductPort {

    private static final Logger log = LoggerFactory.getLogger(KernelProductAdapter.class);

    /** Base path for the Kernel product catalog endpoint. */
    private static final String PRODUCTS_BASE_PATH = "/products";

    private final WebClient kernelWebClient;

    public KernelProductAdapter(WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    @Override
    public Mono<KernelProductDto> findByCatalogProductId(UUID catalogProductId) {
        return kernelWebClient.get()
                .uri(PRODUCTS_BASE_PATH + "/{productId}", catalogProductId)
                .retrieve()
                .bodyToMono(KernelProductDto.class)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> {
                    log.debug("Kernel product not found: {}", catalogProductId);
                    return Mono.empty();
                })
                .onErrorResume(Exception.class, e -> {
                    log.warn("Failed to fetch Kernel product {}: {}", catalogProductId, e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Boolean> existsAndActive(UUID catalogProductId) {
        return findByCatalogProductId(catalogProductId)
                .map(KernelProductDto::active)
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }
}
