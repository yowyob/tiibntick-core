package com.yowyob.tiibntick.core.product.application.port.out;

import com.yowyob.tiibntick.core.product.domain.model.KernelProductDto;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for communicating with the Yowyob Kernel product catalog
 * (RT-comops-product-core, yow_kernel_db) via the KernelBridge HTTP client.
 *
 * <p>Used in two contexts:
 * <ul>
 *   <li>Product creation: if a {@code catalogProductId} is provided, verify the Kernel product
 *       exists before creating the TNT product.</li>
 *   <li>ServiceOffer publication: if the offer has a {@code catalogProductId}, validate that
 *       the Kernel product is still active before publishing to market.</li>
 * </ul>
 *
 * <p>This port must not be called from the domain layer. Only application services may use it.
 *
 * @author MANFOUO Braun
 */
public interface KernelProductPort {

    /**
     * Finds a Kernel catalog product by its UUID.
     *
     * @param catalogProductId the UUID of the product in yow_kernel_db
     * @return the Kernel product data, or empty if not found
     */
    Mono<KernelProductDto> findByCatalogProductId(UUID catalogProductId);

    /**
     * Verifies that a Kernel catalog product exists and is active.
     * Returns true if the product exists and is active, false otherwise
     * (including on network errors — fail-open to avoid blocking TNT operations).
     *
     * @param catalogProductId the UUID of the product in yow_kernel_db
     * @return true if the Kernel product exists and is active
     */
    Mono<Boolean> existsAndActive(UUID catalogProductId);
}
