package com.yowyob.tiibntick.core.roles.application.port.in;

import com.yowyob.tiibntick.core.roles.domain.model.PlatformRoleGrant;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Inbound port — the TiiBnTick platform administrators (TNT_ADMIN), held by the Core registry whatever the tenant
 * of the login (decisions D-159 to D-175, {@code docs/architecture/chantiers/registre-administrateurs-plateforme.md}).
 *
 * @author MANFOUO Braun
 */
public interface ManagePlatformAdminsUseCase {

    /**
     * Who acts: the token subject, the token's tenant ({@code tid}) and whether the Core's owner rule recognised
     * the token as the Kernel owner of the system tenant.
     */
    record Caller(UUID userId, UUID tenantId, boolean systemTenantOwner) {}

    record ForeignTenantPreview(List<PlatformRoleGrant> grants, String confirmationToken, Instant expiresAt) {}

    record ForeignTenantRevocation(UUID batchId, List<PlatformRoleGrant> revoked) {}

    /** Whether the account holds an active TNT_ADMIN grant (cached, D-168). */
    Mono<Boolean> isPlatformAdmin(UUID userId);

    /** D-161: records (once) the protected SYSTEM_OWNER grant of the Kernel owner of the system tenant. */
    Mono<PlatformRoleGrant> ensureSystemOwner(UUID ownerUserId);

    /** D-161: the explicit claim; only for a token recognised as the system-tenant owner. */
    Mono<PlatformRoleGrant> claimOwnership(Caller caller);

    /** D-166: any active TNT_ADMIN; idempotent when the account already holds the role. */
    Mono<PlatformRoleGrant> grant(Caller caller, UUID targetUserId);

    /** D-163, D-166: revokes an ASSIGNED grant; the protected owner grant answers 409. */
    Mono<PlatformRoleGrant> revoke(Caller caller, UUID grantId, String reason);

    Mono<PlatformRoleGrant> find(UUID grantId);

    Flux<PlatformRoleGrant> list(boolean includeRevoked);

    /** D-165: SYSTEM_OWNER only — the exact list and a short-lived confirmation token. */
    Mono<ForeignTenantPreview> previewForeignTenantRevocation(Caller caller);

    /** D-165: revokes exactly the previewed list; 409 when it changed since the preview. */
    Mono<ForeignTenantRevocation> revokeForeignTenant(Caller caller, String confirmationToken, String reason);
}
