package com.yowyob.tiibntick.core.product.domain.exception;

import com.yowyob.tiibntick.common.exception.TntConflictException;
import com.yowyob.tiibntick.core.product.domain.model.ProductStatus;

import java.util.UUID;

/**
 * Thrown when an illegal product status transition is attempted.
 * Maps to HTTP 409 at the REST adapter layer.
 *
 * @author MANFOUO Braun
 */
public class ProductStatusTransitionException extends TntConflictException {

    public ProductStatusTransitionException(UUID productId, ProductStatus from, ProductStatus to) {
        super("PRODUCT_INVALID_TRANSITION",
                "Cannot transition product " + productId + " from " + from + " to " + to);
    }
}
