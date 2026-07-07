package com.yowyob.tiibntick.core.billing.wallet.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a write operation is attempted on a FROZEN or CLOSED wallet.
 * @author MANFOUO Braun
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class WalletFrozenException extends RuntimeException {
    public WalletFrozenException(String message) {
        super(message);
    }
}
