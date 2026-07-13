package com.yowyob.tiibntick.core.gofp.domain.exception;

import com.yowyob.tiibntick.core.gofp.domain.model.enums.DeliveryStatus;

public class InvalidDeliveryStateException extends GofpException {
    public InvalidDeliveryStateException(DeliveryStatus current, DeliveryStatus target) {
        super("GOFP_INVALID_STATE",
              "Transition invalide : " + current + " → " + target);
    }
}
