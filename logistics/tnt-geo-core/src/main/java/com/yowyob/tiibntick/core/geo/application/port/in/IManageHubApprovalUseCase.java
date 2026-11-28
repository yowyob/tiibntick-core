package com.yowyob.tiibntick.core.geo.application.port.in;

import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port: an administrator changes the approval of a relay hub (decision D-138, couriers' machine, D-139).
 * Asking for the current status again changes nothing and tells nobody; a transition the machine refuses raises
 * {@code RelayHubStateException} (409). Revocation: {@link IRevokeRelayHubUseCase}.
 *
 * <p>{@code tenantId} may be {@code null} (any tenant). Errors: {@code GeoNotFoundException} for an unknown hub.
 */
public interface IManageHubApprovalUseCase {

    /** PENDING → APPROVED. */
    Mono<RelayHub> approve(UUID tenantId, UUID hubId, UUID actorId);

    /** PENDING → REJECTED (final); the reason is optional. */
    Mono<RelayHub> reject(UUID tenantId, UUID hubId, String reason, UUID actorId);

    /** APPROVED → SUSPENDED; the reason is optional. */
    Mono<RelayHub> suspend(UUID tenantId, UUID hubId, String reason, UUID actorId);

    /** SUSPENDED → APPROVED. */
    Mono<RelayHub> reactivate(UUID tenantId, UUID hubId, UUID actorId);
}
