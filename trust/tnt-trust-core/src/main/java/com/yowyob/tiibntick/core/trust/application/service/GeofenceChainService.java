package com.yowyob.tiibntick.core.trust.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordGeofenceCrossingUseCase;

/**
 * Application Service — {@code GeofenceChainService}.
 *
 * <p>Anchors geofence zone crossings (deliverer entering/exiting a delivery zone,
 * relay hub, danger zone, etc.) on Hyperledger Fabric.
 *
 * <p>Implements {@link RecordGeofenceCrossingUseCase}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Service
public class GeofenceChainService implements RecordGeofenceCrossingUseCase {

    private static final Logger log = LoggerFactory.getLogger(GeofenceChainService.class);

    private final LogisticEventPublisherService publisherService;
    private final MeterRegistry meterRegistry;

    public GeofenceChainService(
            final LogisticEventPublisherService publisherService,
            final MeterRegistry meterRegistry) {
        this.publisherService = publisherService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Builds a {@code GEOFENCE_CROSSING_RECORDED} logistic event and
     * publishes it to Kafka for Fabric anchoring.
     */
    @Override
    public Mono<String> record(
            final String actorId,
            final String tenantId,
            final String zoneId,
            final String zoneName,
            final String zoneType,
            final String direction,
            final double gpsLat,
            final double gpsLng,
            final String missionId) {

        log.info("Recording geofence crossing — actorId={}, zoneId={}, direction={}",
                actorId, zoneId, direction);

        final LogisticTrustEvent event = LogisticTrustEvent.forGeofenceCrossing(
                actorId, tenantId, zoneId, zoneName, zoneType, direction, gpsLat, gpsLng, missionId);

        return publisherService.publish(event)
                .doOnSuccess(v -> {
                    meterRegistry.counter("tnt.trust.geofence.crossing.recorded",
                            "tenant", tenantId, "direction", direction).increment();
                    log.info("Geofence crossing published — correlationId={}", event.getCorrelationId());
                })
                .thenReturn(event.getCorrelationId());
    }
}
