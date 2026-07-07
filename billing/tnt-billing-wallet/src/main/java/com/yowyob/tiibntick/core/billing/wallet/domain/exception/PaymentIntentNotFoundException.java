package com.yowyob.tiibntick.core.billing.wallet.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when no PaymentIntent is found for a given reference.
 * @author MANFOUO Braun
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PaymentIntentNotFoundException extends RuntimeException {
    public PaymentIntentNotFoundException(String ref) {
        super("PaymentIntent not found for reference: " + ref);
    }
}
