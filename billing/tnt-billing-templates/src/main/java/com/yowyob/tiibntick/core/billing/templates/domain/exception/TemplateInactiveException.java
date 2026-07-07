package com.yowyob.tiibntick.core.billing.templates.domain.exception;

/**
 * Thrown when an actor attempts to apply a {@code PolicyTemplate} that has been
 * deactivated by the TiiBnTick administration.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public class TemplateInactiveException extends RuntimeException {

    public TemplateInactiveException(String templateCode) {
        super("Billing policy template '" + templateCode
                + "' is currently inactive and cannot be applied. "
                + "Please contact TiiBnTick support or select another template.");
    }
}
