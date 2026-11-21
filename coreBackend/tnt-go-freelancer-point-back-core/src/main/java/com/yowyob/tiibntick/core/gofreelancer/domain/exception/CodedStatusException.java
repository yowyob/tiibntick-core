package com.yowyob.tiibntick.core.gofreelancer.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A refusal that carries a stable machine-readable {@code code} in the error body, so that clients
 * can tell it apart from other errors with the same HTTP status.
 */
public class CodedStatusException extends ResponseStatusException {

    private final String code;

    public CodedStatusException(HttpStatus status, String code, String reason) {
        super(status, reason);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
