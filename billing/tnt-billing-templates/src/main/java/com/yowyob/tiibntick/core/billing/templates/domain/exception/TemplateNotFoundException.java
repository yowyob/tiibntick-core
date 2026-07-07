package com.yowyob.tiibntick.core.billing.templates.domain.exception;

/**
 * Thrown when a requested {@code PolicyTemplate} is not found in the catalog.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public class TemplateNotFoundException extends RuntimeException {

    public TemplateNotFoundException(String templateCode) {
        super("Billing policy template not found: '" + templateCode + "'");
    }

    public TemplateNotFoundException(java.util.UUID id) {
        super("Billing policy template not found with id: " + id);
    }
}
