package com.yowyob.tiibntick.core.tp.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

/**
 * Thrown when a TntClientProfile is not found.
 *
 * @author MANFOUO Braun
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TntThirdPartyNotFoundException extends RuntimeException {

    private final UUID identifier;

    public TntThirdPartyNotFoundException(UUID id) {
        super("TntClientProfile not found for id: " + id);
        this.identifier = id;
    }

    public TntThirdPartyNotFoundException(String ref) {
        super("TntClientProfile not found for ref: " + ref);
        this.identifier = null;
    }

    public UUID getIdentifier() {
        return identifier;
    }
}
