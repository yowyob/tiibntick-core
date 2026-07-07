package com.yowyob.tiibntick.core.media.domain.exception;

/**
 * Thrown when a scanned QR code's HMAC signature does not match the expected value.
 * This indicates tampering or use of an invalid QR code.
 *
 * @author MANFOUO Braun
 */
public class QrSignatureInvalidException extends RuntimeException {
    public QrSignatureInvalidException(String message) {
        super(message);
    }
}
