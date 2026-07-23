package com.yowyob.tiibntick.core.agency.sync.adapter.in.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.sync.application.offline.AgencyOfflineOpContracts;
import com.yowyob.tiibntick.core.realtime.application.port.in.IProcessGpsPingUseCase;
import com.yowyob.tiibntick.core.realtime.domain.model.GPSStreamEntry;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.sync.application.port.out.IOfflineOperationApplier;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Agency {@link IOfflineOperationApplier} for aggregate {@code GPS}.
 * Forwards offline {@code GPS_UPDATE} ops to realtime GPS ingestion.
 */
@Component
public class AgencyGpsOfflineOperationApplier implements IOfflineOperationApplier {

    private static final Logger log = LoggerFactory.getLogger(AgencyGpsOfflineOperationApplier.class);

    private final IProcessGpsPingUseCase processGpsPingUseCase;
    private final ObjectMapper objectMapper;

    public AgencyGpsOfflineOperationApplier(
            IProcessGpsPingUseCase processGpsPingUseCase,
            ObjectMapper objectMapper) {
        this.processGpsPingUseCase = processGpsPingUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String aggregateType) {
        return AgencyOfflineOpContracts.AGGREGATE_GPS.equals(aggregateType);
    }

    @Override
    public Mono<String> apply(OfflineOperation op) {
        return Mono.defer(() -> {
            if (op.getType() != OfflineOpType.GPS_UPDATE) {
                return Mono.error(new TntValidationException(
                        "Agency GPS applier does not support type: " + op.getType()));
            }
            AgencyOfflineOpContracts.ParsedGpsPayload payload =
                    AgencyOfflineOpContracts.parseAndValidateGpsPayload(op.getPayload(), objectMapper);
            UUID aggregateDelivererId = AgencyOfflineOpContracts.parseUuid(op.getAggregateId(), "aggregateId");
            if (!aggregateDelivererId.equals(payload.delivererId())) {
                return Mono.error(new TntValidationException(
                        "GPS aggregateId must equal payload.delivererId"));
            }

            GeoCoordinates coordinates = GeoCoordinates.of(
                    payload.latitude(),
                    payload.longitude(),
                    null,
                    payload.accuracyMeters());
            GPSStreamEntry entry = GPSStreamEntry.of(
                    payload.delivererId().toString(),
                    payload.missionId() != null ? payload.missionId().toString() : null,
                    op.getTenantId(),
                    coordinates,
                    payload.speedKmh(),
                    normalizeBearing(payload.bearing()),
                    Math.max(0, payload.accuracyMeters()),
                    null,
                    payload.timestamp());

            log.debug("Applying Agency offline GPS_UPDATE for deliverer {} (opId={})",
                    payload.delivererId(), op.getId());

            return processGpsPingUseCase.processGpsPing(entry)
                    .thenReturn("{\"status\":\"GPS_ACCEPTED\",\"delivererId\":\""
                            + payload.delivererId() + "\"}");
        });
    }

    private static double normalizeBearing(double bearing) {
        if (bearing < 0 || bearing > 360) {
            return 0;
        }
        return bearing;
    }
}
