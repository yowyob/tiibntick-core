package com.yowyob.tiibntick.core.billing.templates.domain.exception;

/**
 * The template exists in the catalog but cannot yet be turned into rules the pricing engine
 * evaluates (modifier-only templates such as loyalty or time slots, and the hub, network and
 * commission templates which target another engine). Refused explicitly rather than creating a
 * policy that silently prices at zero.
 */
public class TemplateNotMaterializableException extends RuntimeException {

    public TemplateNotMaterializableException(String templateCode, String reason) {
        super("Template '" + templateCode + "' cannot be applied yet: " + reason);
    }
}
