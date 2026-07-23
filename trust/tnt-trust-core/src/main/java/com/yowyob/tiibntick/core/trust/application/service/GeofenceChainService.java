package com.yowyob.tiibntick.core.trust.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.GeofenceCrossingRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordGeofenceCrossingUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.GetGeofenceCrossingsUseCase;
import com.yowyob.tiibntick.core.trust.application.port.out.GeofenceCrossingRepository;

/**
 * Application Service — {@code GeofenceChainService}.
 *
 * <p>Anchors geofence zone crossings (deliverer entering/exiting a delivery zone,
 * relay hub, danger zone, etc.) on Hyperledger Fabric.
 *
 * <p>Implements {@link RecordGeofenceCrossingUseCase} and {@link GetGeofenceCrossingsUseCase}.
 *
 * @author MANFOUO Braun
 * @version 1.1
 */
@Service
public class GeofenceChainService implements RecordGeofenceCrossingUseCase, GetGeofenceCrossingsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GeofenceChainService.class);

    private final LogisticEventPublisherService publisherService;
    private final GeofenceCrossingRepository geofenceCrossingRepository;
    private final MeterRegistry meterRegistry;

    public GeofenceChainService(
            final LogisticEventPublisherService publisherService,
            final GeofenceCrossingRepository geofenceCrossingRepository,
            final MeterRegistry meterRegistry) {
        this.publisherService = publisherService;
        this.geofenceCrossingRepository = geofenceCrossingRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Persists a {@link GeofenceCrossingRecord} locally first — so it is
     * immediately visible to reads — then builds a
     * {@code GEOFENCE_CROSSING_RECORDED} logistic event and publishes it to
     * Kafka for Fabric anchoring.
     */
    @Override
    @Transactional
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

        final GeofenceCrossingRecord crossing = GeofenceCrossingRecord.record(
                actorId, tenantId, zoneId, zoneName, zoneType, direction, gpsLat, gpsLng, missionId);
        final LogisticTrustEvent event = LogisticTrustEvent.forGeofenceCrossing(crossing);

        return geofenceCrossingRepository.save(crossing)
                .then(publisherService.publish(event))
                .doOnSuccess(v -> {
                    meterRegistry.counter("tnt.trust.geofence.crossing.recorded",
                            "tenant", tenantId, "direction", direction).increment();
                    log.info("Geofence crossing published — correlationId={}", event.getCorrelationId());
                })
                .thenReturn(event.getCorrelationId());
    }

    /** {@inheritDoc} */
    @Override
    public Flux<GeofenceCrossingRecord> getByActorId(final String actorId, final String tenantId) {
        return geofenceCrossingRepository.findByActorId(actorId, tenantId);
    }
}
