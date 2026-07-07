package com.yowyob.tiibntick.core.media.domain.exception;

/**
 * Thrown when an object storage operation fails (MinIO error, network issue, etc.).
 *
 * @author MANFOUO Braun
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
