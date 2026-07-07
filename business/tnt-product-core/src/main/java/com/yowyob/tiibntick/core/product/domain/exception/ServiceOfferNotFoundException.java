package com.yowyob.tiibntick.core.product.domain.exception;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;

import java.util.UUID;

/**
 * Thrown when a requested {@link com.yowyob.tiibntick.core.product.domain.model.ServiceOffer}
 * does not exist.
 * Maps to HTTP 404 at the REST adapter layer.
 *
 * @author MANFOUO Braun
 */
public class ServiceOfferNotFoundException extends TntNotFoundException {

    public ServiceOfferNotFoundException(UUID offerId) {
        super("SERVICE_OFFER_NOT_FOUND", "ServiceOffer", offerId);
    }
}
