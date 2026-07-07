package com.yowyob.tiibntick.core.product.application.port.in;

import com.yowyob.tiibntick.common.vo.Money;
import com.yowyob.tiibntick.core.product.domain.model.Dimensions;
import com.yowyob.tiibntick.core.product.domain.model.LogisticsProfile;
import com.yowyob.tiibntick.core.product.domain.model.ProductType;
import com.yowyob.tiibntick.core.product.domain.model.UnitOfMeasure;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command to create a new TNT product in the catalog.
 *
 * <p>{@code catalogProductId} is optional: when provided, the application service will
 * verify the Kernel product (RT-comops-product-core) exists before creating the TNT product.
 * When null, the product is created as a TNT-exclusive catalog entry.
 *
 * <p>Uses {@link Money} from {@code tnt-common-core} (ISO 4217 compliant, XAF support).
 *
 * @author MANFOUO Braun
 */
public record CreateProductCommand(
        UUID tenantId,
        /**
         * Optional UUID of the Kernel catalog product this TNT product references.
         * Null for TNT-exclusive products with no Kernel catalog counterpart.
         */
        UUID catalogProductId,
        String sku,
        String name,
        String description,
        UUID categoryId,
        ProductType type,
        Money basePrice,
        UnitOfMeasure unit,
        Double weightKg,
        Dimensions dimensions,
        LogisticsProfile logisticsProfile,
        List<String> tags,
        Map<String, String> attributes
) {
    /** Backward-compatible constructor without catalogProductId. */
    public CreateProductCommand(UUID tenantId, String sku, String name, String description,
                                 UUID categoryId, ProductType type, Money basePrice,
                                 UnitOfMeasure unit, Double weightKg, Dimensions dimensions,
                                 LogisticsProfile logisticsProfile, List<String> tags,
                                 Map<String, String> attributes) {
        this(tenantId, null, sku, name, description, categoryId, type,
             basePrice, unit, weightKg, dimensions, logisticsProfile, tags, attributes);
    }
}
