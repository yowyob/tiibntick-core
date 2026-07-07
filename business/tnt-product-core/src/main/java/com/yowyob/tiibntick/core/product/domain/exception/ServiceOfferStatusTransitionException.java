package com.yowyob.tiibntick.core.product.domain.exception;

import com.yowyob.tiibntick.common.exception.TntConflictException;
import com.yowyob.tiibntick.core.product.domain.model.ProductStatus;

import java.util.UUID;

/**
 * Thrown when an illegal ServiceOffer status transition is attempted.
 * Maps to HTTP 409 at the REST adapter layer.
 *
 * @author MANFOUO Braun
 */
public class ServiceOfferStatusTransitionException extends TntConflictException {

    public ServiceOfferStatusTransitionException(UUID offerId, ProductStatus from, ProductStatus to) {
        super("OFFER_INVALID_TRANSITION",
                "Cannot transition ServiceOffer " + offerId + " from " + from + " to " + to);
    }

    public ServiceOfferStatusTransitionException(UUID offerId, ProductStatus from, ProductStatus to, String reason) {
        super("OFFER_INVALID_TRANSITION",
                "Cannot transition ServiceOffer " + offerId + " from " + from + " to " + to + ": " + reason);
    }
}
