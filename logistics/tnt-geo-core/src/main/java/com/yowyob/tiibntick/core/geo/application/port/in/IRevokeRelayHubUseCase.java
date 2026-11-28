package com.yowyob.tiibntick.core.geo.application.port.in;

import com.yowyob.tiibntick.core.geo.domain.model.RelayHubRevocation;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port: an administrator revokes a relay hub for good (maintainer decision D-129, 2026-11-26).
 *
 * <p>Only an APPROVED or SUSPENDED hub can be revoked (D-135, D-138). Effects: approval status {@code REVOKED} with
 * reason, date and administrator (no transition leaves it); the parcels
 * still in custody are listed and traced; every {@link com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubRevocationListener}
 * is told (product backends notify the operator in the language of their account and align their own records).
 */
public interface IRevokeRelayHubUseCase {

    /**
     * @param tenantId          tenant of the hub ({@code null}: any tenant)
     * @param hubId             the hub
     * @param reason            mandatory reason (not blank)
     * @param revokedByActorId  the administrator
     * @return the revocation; errors: {@code GeoNotFoundException} (unknown hub),
     *         {@code IllegalArgumentException} (reason missing), {@code RelayHubStateException} (not APPROVED nor SUSPENDED, already revoked included)
     */
    Mono<RelayHubRevocation> revoke(UUID tenantId, UUID hubId, String reason, UUID revokedByActorId);
}
