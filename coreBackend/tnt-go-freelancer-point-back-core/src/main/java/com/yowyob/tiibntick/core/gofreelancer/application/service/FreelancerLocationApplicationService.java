package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.realtime.application.port.in.IProcessGpsPingUseCase;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.GPSStreamEntry;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.FreelancerLocationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Application service implementing the FreelancerLocationUseCase port.
 *
 * <p>Bridges the Go-Freelancer-Point domain with the centralized
 * {@code tnt-realtime-core} GPS pipeline, which handles:
 * <ul>
 *   <li>Presence update in Redis</li>
 *   <li>Kalman filter ETA recomputation</li>
 *   <li>Geofence monitoring and trigger events</li>
 *   <li>WebSocket/SSE broadcast to connected clients</li>
 *   <li>FreelancerOrg fleet topic broadcast (when freelancerOrgId is present)</li>
 * </ul>
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreelancerLocationApplicationService implements FreelancerLocationUseCase {

    private final IProcessGpsPingUseCase processGpsPingUseCase;

    @Override
    public Mono<Void> updateLocation(UUID freelancerId,
                                     String tenantId,
                                     double latitude,
                                     double longitude,
                                     double speedKmh,
                                     double bearing,
                                     double accuracy,
                                     String missionId,
                                     String freelancerOrgId) {

        log.debug("Forwarding GPS ping to realtime-core for freelancer {} (tenant={})", freelancerId, tenantId);

        GeoCoordinates coordinates = GeoCoordinates.of(latitude, longitude, null, accuracy > 0 ? accuracy : null);

        GPSStreamEntry entry = new GPSStreamEntry(
                freelancerId.toString(),
                missionId,
                tenantId,
                coordinates,
                speedKmh,
                bearing,
                accuracy,
                null,           // batteryLevel — non disponible via REST, géré par WS
                LocalDateTime.now(),
                freelancerOrgId
        );

        return processGpsPingUseCase.processGpsPing(entry);
    }
}
