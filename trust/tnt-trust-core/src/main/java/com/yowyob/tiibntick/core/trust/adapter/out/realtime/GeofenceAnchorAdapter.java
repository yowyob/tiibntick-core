package com.yowyob.tiibntick.core.trust.adapter.out.realtime;

import com.yowyob.tiibntick.core.realtime.application.port.out.GeofenceAnchorPayload;
import com.yowyob.tiibntick.core.realtime.application.port.out.IGeofenceAnchorPort;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordGeofenceCrossingUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * tnt-trust-core implementation of {@link IGeofenceAnchorPort} (outbound port
 * owned by tnt-realtime-core).
 *
 * <p>tnt-trust-core depends on tnt-realtime-core (one-directional, no Maven cycle —
 * realtime never depends back on trust) purely to see this port and its payload type;
 * it maps the realtime-owned {@link GeofenceAnchorPayload} into a call to
 * {@link RecordGeofenceCrossingUseCase}.
 *
 * @author MANFOUO Braun
 * @see IGeofenceAnchorPort
 */
@Component
@RequiredArgsConstructor
public class GeofenceAnchorAdapter implements IGeofenceAnchorPort {

    private final RecordGeofenceCrossingUseCase recordGeofenceCrossing;

    @Override
    public Mono<Void> anchor(GeofenceAnchorPayload payload) {
        return recordGeofenceCrossing.record(
                        payload.actorId(), payload.tenantId(), payload.zoneId(), payload.zoneName(),
                        payload.zoneType(), payload.direction(), payload.gpsLat(), payload.gpsLng(),
                        payload.missionId())
                .then();
    }
}
