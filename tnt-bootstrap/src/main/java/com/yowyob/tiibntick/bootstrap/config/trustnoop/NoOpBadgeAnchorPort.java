package com.yowyob.tiibntick.bootstrap.config.trustnoop;

import com.yowyob.tiibntick.core.actor.application.port.out.BadgeAnchorPayload;
import com.yowyob.tiibntick.core.actor.application.port.out.IBadgeAnchorPort;
import reactor.core.publisher.Mono;

/**
 * No-op fallback for {@link IBadgeAnchorPort}, wired only when
 * {@code tnt.trust.enabled=false} — see {@code TrustNoOpFallbackConfig}.
 *
 * <p>Unlike the other no-op ports, {@link IBadgeAnchorPort#anchor} is documented
 * as erroring on failure (callers are expected to contain it) rather than
 * returning an empty/default value — an empty {@link Mono} here would violate
 * that contract by silently completing without emitting the tx hash callers
 * may {@code .map()} over. Erroring keeps callers' existing error-containment
 * paths exercised instead of introducing a new "successful but empty" case.
 *
 * @author MANFOUO Braun
 */
public class NoOpBadgeAnchorPort implements IBadgeAnchorPort {

    @Override
    public Mono<String> anchor(final BadgeAnchorPayload payload) {
        return Mono.error(new IllegalStateException("tnt-trust-core is disabled (tnt.trust.enabled=false)"));
    }
}
