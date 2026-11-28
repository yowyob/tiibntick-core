package com.yowyob.tiibntick.core.roles.application.port.out;

import com.yowyob.tiibntick.core.roles.domain.model.PlatformRoleGrant;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound port — the registry of platform-role grants {@code tnt_platform_role_grants} (decision D-159).
 * A revocation only stamps the row: nothing is ever deleted.
 *
 * @author MANFOUO Braun
 */
public interface PlatformRoleGrantRepository {

    Mono<PlatformRoleGrant> findActive(String roleCode, UUID userId);

    Mono<PlatformRoleGrant> findById(UUID id);

    /** Newest first; revoked rows only when {@code includeRevoked}. */
    Flux<PlatformRoleGrant> findAll(String roleCode, boolean includeRevoked);

    /** Active grants whose source tenant differs from {@code systemTenantId}, ordered by id. */
    Flux<PlatformRoleGrant> findActiveFromOtherTenants(String roleCode, UUID systemTenantId);

    /** Inserts the grant; empty when an active grant of the same role already exists for the account. */
    Mono<PlatformRoleGrant> insertIfNoActive(PlatformRoleGrant grant);

    /** Stamps the revocation of an active grant; emits whether a row changed. */
    Mono<Boolean> revoke(UUID id, UUID revokedBy, String reason, UUID batchId, Instant at);
}
