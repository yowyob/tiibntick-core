package com.yowyob.tiibntick.core.sync.application.port.out;

import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import reactor.core.publisher.Mono;

/**
 * Outbound SPI — lets a business module (e.g. {@code tnt-market-back-core})
 * plug real use-case execution into the generic offline-sync push path.
 *
 * <p>{@code tnt-sync-core} is deliberately domain-agnostic: by default,
 * {@link com.yowyob.tiibntick.core.sync.domain.service.OfflineQueueDomainService}
 * only journals a pushed {@link OfflineOperation} into {@code entity_version}
 * (the source of truth for delta-pull) without performing any real
 * side-effect. Modules that need the push path to actually mutate their own
 * aggregates implement this SPI and expose it as a Spring bean; Spring
 * autowires every implementation into
 * {@code OfflineQueueDomainService}'s {@code List<IOfflineOperationApplier>}
 * constructor parameter. If no implementation claims a given
 * {@link OfflineOperation#getAggregateType()}, the journal-only fallback
 * behavior is preserved unchanged (this is what keeps delivery aggregates —
 * MISSION, PACKAGE, ACTOR_PROFILE, RELAY_HUB, GEO_ALERT, GEOFENCE_TRIGGER —
 * working exactly as before).
 *
 * @author MANFOUO Braun
 */
public interface IOfflineOperationApplier {

    /**
     * @param aggregateType the {@link OfflineOperation#getAggregateType()} of an incoming
     *                      offline operation, e.g. {@code "MARKET_ORDER"}
     * @return {@code true} if this applier is responsible for executing the real
     * business mutation for operations against that aggregate type
     */
    boolean supports(String aggregateType);

    /**
     * Executes the real use-case mutation for the given offline operation.
     *
     * @param op the offline operation to apply — {@link OfflineOperation#getPayload()} carries
     *           the module-specific command envelope (JSON)
     * @return the resulting response DTO serialized as JSON; this becomes the
     * {@code payloadJson} persisted into {@code entity_version} for delta-pull, so
     * downstream clients replaying pulls see the authoritative server-side result,
     * not just the client's echoed input
     */
    Mono<String> apply(OfflineOperation op);
}
