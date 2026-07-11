package com.yowyob.tiibntick.bootstrap.config.trustnoop;

import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgDidAnchorPayload;
import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgDidAnchorPort;
import reactor.core.publisher.Mono;

/**
 * No-op fallback for {@link FreelancerOrgDidAnchorPort}, wired only when
 * {@code tnt.trust.enabled=false} — see {@code TrustNoOpFallbackConfig}.
 *
 * @author MANFOUO Braun
 */
public class NoOpFreelancerOrgDidAnchorPort implements FreelancerOrgDidAnchorPort {

    @Override
    public Mono<String> issueDid(final FreelancerOrgDidAnchorPayload payload) {
        return Mono.empty();
    }
}
