package com.yowyob.tiibntick.core.organization.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Outbound port for issuing a FreelancerOrganization's Decentralized Identifier (DID)
 * on the blockchain, once the organization has been verified.
 *
 * <p>Implemented by an adapter living in {@code tnt-trust-core} (which depends on
 * {@code tnt-organization-core} to see this port — never the other way round, to keep
 * the module graph acyclic). Best-effort: a failure here must never fail the
 * organization-verification flow, so callers should contain errors before/around this call.
 *
 * @author MANFOUO Braun
 */
public interface FreelancerOrgDidAnchorPort {

    /**
     * Issues and anchors a DID for a verified FreelancerOrganization.
     *
     * @param payload the organization's identity data to anchor
     * @return a {@link Mono} emitting the issued DID string (format:
     *         {@code did:tiibntick:{tenantId}:org:{orgId}})
     */
    Mono<String> issueDid(FreelancerOrgDidAnchorPayload payload);
}
