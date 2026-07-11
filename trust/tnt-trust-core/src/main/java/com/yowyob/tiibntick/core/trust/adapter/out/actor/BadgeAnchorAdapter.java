package com.yowyob.tiibntick.core.trust.adapter.out.actor;

import com.yowyob.tiibntick.core.actor.application.port.out.BadgeAnchorPayload;
import com.yowyob.tiibntick.core.actor.application.port.out.IBadgeAnchorPort;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordBadgeUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * tnt-trust-core implementation of {@link IBadgeAnchorPort} (outbound port
 * owned by tnt-actor-core).
 *
 * <p>tnt-trust-core depends on tnt-actor-core (one-directional, no Maven cycle —
 * actor-core never depends back on trust) purely to see this port and its payload
 * type; it delegates to the pre-existing {@link RecordBadgeUseCase}. Compliant
 * layering: tnt-actor-core is L2 (identity), tnt-trust-core is L3 (logistics) —
 * trust depending on a lower layer is the normal direction.
 *
 * @author MANFOUO Braun
 * @see IBadgeAnchorPort
 */
@Component
@RequiredArgsConstructor
public class BadgeAnchorAdapter implements IBadgeAnchorPort {

    /** tnt-actor-core's Badge domain model doesn't track reputation points yet. */
    private static final int NO_POINTS_TRACKED = 0;

    private final RecordBadgeUseCase recordBadge;

    @Override
    public Mono<String> anchor(BadgeAnchorPayload payload) {
        return recordBadge.record(
                payload.actorId().toString(),
                payload.badgeCode(),
                NO_POINTS_TRACKED,
                payload.tenantId().toString());
    }
}
