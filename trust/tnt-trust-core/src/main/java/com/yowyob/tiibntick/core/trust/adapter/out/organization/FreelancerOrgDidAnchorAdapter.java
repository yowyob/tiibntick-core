package com.yowyob.tiibntick.core.trust.adapter.out.organization;

import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgDidAnchorPayload;
import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgDidAnchorPort;
import com.yowyob.tiibntick.core.trust.application.port.in.IssueDIDUseCase;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;
import com.yowyob.tiibntick.core.trust.domain.service.DidKeyPairGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * tnt-trust-core implementation of {@link FreelancerOrgDidAnchorPort} (outbound port
 * owned by tnt-organization-core).
 *
 * <p>tnt-trust-core depends on tnt-organization-core (one-directional, no Maven cycle —
 * organization never depends back on trust) purely to see this port and its payload type;
 * it maps the organization-owned {@link FreelancerOrgDidAnchorPayload} into a call to
 * {@link IssueDIDUseCase#issueForFreelancerOrg}.
 *
 * <p>The org itself owns no PKI material, so this adapter generates the ECDSA P-256
 * keypair the DID is anchored with via {@link DidKeyPairGenerator} — DID key custody
 * is a trust-domain concern, not an organization one.
 *
 * @author MANFOUO Braun
 * @see FreelancerOrgDidAnchorPort
 */
@Component
@RequiredArgsConstructor
public class FreelancerOrgDidAnchorAdapter implements FreelancerOrgDidAnchorPort {

    private final IssueDIDUseCase issueDIDUseCase;

    @Override
    public Mono<String> issueDid(FreelancerOrgDidAnchorPayload payload) {
        return issueDIDUseCase.issueForFreelancerOrg(
                        payload.orgId().toString(), payload.tenantId(),
                        payload.tradeName(), DidKeyPairGenerator.generatePublicKeyPem())
                .map(DIDDocument::getDid);
    }
}
