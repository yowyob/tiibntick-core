package com.yowyob.tiibntick.core.auth.adapter.out.kernel;

import com.yowyob.tiibntick.core.auth.application.port.out.IYowAuthTntAdapter;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * No-op fallback implementation of {@link IYowAuthTntAdapter}.
 *
 * <p>Auto-configured by {@link com.yowyob.tiibntick.core.auth.config.TntAuthAutoConfiguration}
 * via {@code @ConditionalOnMissingBean(IYowAuthTntAdapter.class)}.
 * When tnt-actor-core is on the classpath and provides a real implementation,
 * this bean is bypassed automatically.
 *
 * <p>Returns empty / false defaults for all queries — the security context
 * will still be populated from the JWT claims alone (actorId, agencyId from the token).
 *
 * @author MANFOUO Braun
 */
public class NoOpYowAuthTntAdapter implements IYowAuthTntAdapter {

    @Override
    public Mono<Optional<UUID>> resolveActorId(UUID userId, UUID tenantId) {
        return Mono.just(Optional.empty());
    }

    @Override
    public Mono<Boolean> isFreelancer(UUID actorId, UUID tenantId) {
        return Mono.just(false);
    }

    @Override
    public Mono<Optional<UUID>> resolveAgencyId(UUID actorId, UUID tenantId) {
        return Mono.just(Optional.empty());
    }
}
