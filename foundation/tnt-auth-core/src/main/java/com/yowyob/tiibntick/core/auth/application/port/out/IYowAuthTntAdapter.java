package com.yowyob.tiibntick.core.auth.application.port.out;

import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Secondary (outbound) port — defined in tnt-auth-core, implemented by tnt-actor-core.
 *
 * <p>Resolves the TiiBnTick actor identity linked to a Kernel user account.
 * Follows the "Extension without repetition" principle: tnt-auth-core defines
 * the contract; tnt-actor-core provides the implementation via
 * {@code @ConditionalOnMissingBean}, wiring the Kernel's userId to the
 * TiiBnTick actor domain without creating a circular dependency.
 *
 * <p>If no implementation is registered (e.g. in tests), the
 * {@link com.yowyob.tiibntick.core.auth.adapter.out.kernel.NoOpYowAuthTntAdapter}
 * fallback is auto-configured and returns empty for all queries.
 *
 * @author MANFOUO Braun
 */
public interface IYowAuthTntAdapter {

    /**
     * Returns the TiiBnTick actorId associated with the given Kernel userId within a tenant.
     * Returns {@code Optional.empty()} when no actor profile is linked yet.
     *
     * @param userId   Kernel user identifier (from JWT subject / YowAuth0)
     * @param tenantId tenant scope
     */
    Mono<Optional<UUID>> resolveActorId(UUID userId, UUID tenantId);

    /**
     * Returns true if the actor identified by {@code actorId} is a freelancer
     * (not permanently assigned to an agency).
     *
     * @param actorId  TiiBnTick actor identifier
     * @param tenantId tenant scope
     */
    Mono<Boolean> isFreelancer(UUID actorId, UUID tenantId);

    /**
     * Returns the agencyId the actor belongs to, or empty if unassigned.
     *
     * @param actorId  TiiBnTick actor identifier
     * @param tenantId tenant scope
     */
    Mono<Optional<UUID>> resolveAgencyId(UUID actorId, UUID tenantId);
}
