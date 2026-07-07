package com.yowyob.tiibntick.core.realtime.application.service;

import com.yowyob.tiibntick.core.realtime.application.port.in.IRegisterGeofenceZoneUseCase;
import com.yowyob.tiibntick.core.realtime.domain.model.GeofenceZone;
import com.yowyob.tiibntick.core.realtime.domain.service.GeofenceMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Application service managing geofence zone registration.
 *
 * @author MANFOUO Braun
 */
@Service
public class GeofenceApplicationService implements IRegisterGeofenceZoneUseCase {

    private static final Logger log = LoggerFactory.getLogger(GeofenceApplicationService.class);

    private final GeofenceMonitorService geofenceMonitorService;

    public GeofenceApplicationService(GeofenceMonitorService geofenceMonitorService) {
        this.geofenceMonitorService = geofenceMonitorService;
    }

    @Override
    public Mono<Void> registerZone(GeofenceZone zone) {
        log.info("Registering geofence zone '{}' for tenant {} (radius={}m)",
                zone.getName(), zone.getTenantId(), zone.getRadiusMeters());
        return geofenceMonitorService.registerZone(zone);
    }

    @Override
    public Mono<Void> removeZone(String zoneId, String tenantId) {
        log.info("Removing geofence zone {} for tenant {}", zoneId, tenantId);
        return geofenceMonitorService.removeZone(zoneId, tenantId);
    }
}
