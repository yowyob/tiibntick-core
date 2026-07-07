package com.yowyob.tiibntick.core.product.domain.exception;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;

import java.util.UUID;

/**
 * Thrown when a requested {@link com.yowyob.tiibntick.core.product.domain.model.Product}
 * does not exist in the TNT product catalog.
 * Maps to HTTP 404 at the REST adapter layer.
 *
 * @author MANFOUO Braun
 */
public class ProductNotFoundException extends TntNotFoundException {

    public ProductNotFoundException(UUID productId) {
        super("PRODUCT_NOT_FOUND", "Product", productId);
    }

    public ProductNotFoundException(String sku, UUID tenantId) {
        super("PRODUCT_NOT_FOUND", "Product with SKU '" + sku + "' not found for tenant " + tenantId);
    }
}
