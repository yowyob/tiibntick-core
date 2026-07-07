package com.yowyob.tiibntick.core.product.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-only DTO representing product catalog data fetched from the Yowyob Kernel
 * (RT-comops-product-core, yow_kernel_db) via the KernelBridge HTTP client.
 *
 * <p>This record is <strong>never persisted</strong> in tnt_core_db. It is used only for:
 * <ul>
 *   <li>Validation: confirm a Kernel catalog product exists before linking {@code catalogProductId}
 *       in a TNT {@link Product} or {@link ServiceOffer}.</li>
 *   <li>Enrichment: surface Kernel catalog metadata in product/offer API responses.</li>
 * </ul>
 *
 * <p>The {@code catalogProductId} in the TNT domain is the logical foreign key to this DTO's
 * {@code productId}. No physical FK constraint exists across databases.
 *
 * @author MANFOUO Braun
 */
public record KernelProductDto(
        /** UUID of the product in the Kernel catalog (yow_kernel_db). Primary integration key. */
        UUID productId,

        /** Human-readable name from the Kernel catalog. */
        String name,

        /** SKU as defined in the Kernel catalog. */
        String sku,

        /** Product type from the Kernel (e.g., PHYSICAL_GOOD, SERVICE). */
        String productType,

        /** ISO 4217 currency code for the base price in the Kernel catalog. */
        String basePriceCurrency,

        /** Base price amount in the Kernel catalog. */
        BigDecimal basePriceAmount,

        /** Tenant this product belongs to in the Kernel. */
        UUID tenantId,

        /** Whether this product is currently active in the Kernel catalog. */
        boolean active
) {}
