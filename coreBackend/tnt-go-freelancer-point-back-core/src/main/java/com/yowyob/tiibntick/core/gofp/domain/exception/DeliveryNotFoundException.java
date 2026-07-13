package com.yowyob.tiibntick.core.gofp.domain.exception;

import java.util.UUID;

public class DeliveryNotFoundException extends GofpException {
    public DeliveryNotFoundException(UUID id) {
        super("GOFP_DEL_NOT_FOUND", "Livraison introuvable : " + id);
    }
}
