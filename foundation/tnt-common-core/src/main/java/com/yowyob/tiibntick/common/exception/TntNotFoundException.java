package com.yowyob.tiibntick.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested resource does not exist in TiiBnTick Core.
 * Maps to HTTP 404 at the REST adapter layer.
 *
 * Author: MANFOUO Braun
 * Created: 2025-10-01
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TntNotFoundException extends TntException {

    public TntNotFoundException(String message) {
        super("NOT_FOUND", message);
    }

    public TntNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }

    public TntNotFoundException(String errorCode, String resourceType, Object resourceId) {
        super(errorCode, resourceType + " not found with id: " + resourceId);
    }
}
