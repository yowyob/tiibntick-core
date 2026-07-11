package com.yowyob.tiibntick.bootstrap.config.trustnoop;

import com.yowyob.tiibntick.core.realtime.application.port.out.GeofenceAnchorPayload;
import com.yowyob.tiibntick.core.realtime.application.port.out.IGeofenceAnchorPort;
import reactor.core.publisher.Mono;

/**
 * No-op fallback for {@link IGeofenceAnchorPort}, wired only when
 * {@code tnt.trust.enabled=false} — see {@code TrustNoOpFallbackConfig}.
 *
 * @author MANFOUO Braun
 */
public class NoOpGeofenceAnchorPort implements IGeofenceAnchorPort {

    @Override
    public Mono<Void> anchor(final GeofenceAnchorPayload payload) {
        return Mono.empty();
    }
}
