package com.yowyob.tiibntick.core.billing.wallet.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.UUID;

/**
 * Thrown when no Wallet is found for the requested userId or walletId.
 * @author MANFOUO Braun
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(UUID userId) {
        super("Wallet not found for userId: " + userId);
    }
    public WalletNotFoundException(String message) {
        super(message);
    }
}
