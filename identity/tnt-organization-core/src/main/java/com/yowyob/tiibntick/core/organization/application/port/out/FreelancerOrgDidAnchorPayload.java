package com.yowyob.tiibntick.core.organization.application.port.out;

import java.util.UUID;

/**
 * Organization-owned payload for {@link FreelancerOrgDidAnchorPort#issueDid}.
 *
 * <p>Deliberately independent from any {@code tnt-trust-core} domain type — the
 * implementing adapter (in {@code tnt-trust-core}) maps this into its own
 * {@code DIDDocument}, keeping the hexagonal boundary between the two modules.
 *
 * @author MANFOUO Braun
 */
public record FreelancerOrgDidAnchorPayload(
        UUID orgId,
        String tenantId,
        String tradeName) {
}
