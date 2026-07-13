package com.yowyob.tiibntick.core.marketback.domain.exception;

/**
 * Thrown when a MarketListing references an {@code organizationId} that does not
 * exist, or is not active, in the Kernel (see {@code tnt-organization-core}'s
 * {@code KernelOrganizationPort}).
 *
 * @author MANFOUO Braun
 */
public class InvalidOrganizationException extends MarketDomainException {
    public InvalidOrganizationException(String organizationId) {
        super("Organization not found or inactive in Kernel: " + organizationId);
    }
}
