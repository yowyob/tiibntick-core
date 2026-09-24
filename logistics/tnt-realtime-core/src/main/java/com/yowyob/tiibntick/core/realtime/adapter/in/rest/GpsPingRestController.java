package com.yowyob.tiibntick.core.realtime.adapter.in.rest;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.realtime.application.port.in.IProcessGpsPingUseCase;
import com.yowyob.tiibntick.core.realtime.domain.model.GPSStreamEntry;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * HTTP ingress for GPS pings — consumed by tnt-agency {@code RealtimeCoreClient}.
 */
@RestController
@RequestMapping("/api/v1/realtime/gps")
public class GpsPingRestController {

    private static final Logger log = LoggerFactory.getLogger(GpsPingRestController.class);

    private final IProcessGpsPingUseCase processGpsPing;

    public GpsPingRestController(IProcessGpsPingUseCase processGpsPing) {
        this.processGpsPing = processGpsPing;
    }

    @PostMapping("/ping")
    public Mono<ResponseEntity<Void>> ping(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @Valid @RequestBody GpsPingRestRequest body) {

        String subjectId = currentUser.userId().toString();

        if (body.delivererId() != null && !body.delivererId().equals(subjectId)) {
            log.warn("GPS ping spoofing attempt: caller {} tried to write position for {} (tenant {})",
                    subjectId, body.delivererId(), currentUser.tenantId());
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).<Void>build());
        }

        GPSStreamEntry entry = new GPSStreamEntry(
                subjectId,
                body.missionId(),
                currentUser.tenantId().toString(),
                GeoCoordinates.of(body.latitude(), body.longitude(), null, null),
                body.speedKmh() != null ? body.speedKmh() : 0.0,
                body.bearing() != null ? body.bearing() : 0.0,
                body.accuracyMeters() != null ? body.accuracyMeters() : 0.0,
                null,
                LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()),
                null
        );
        return processGpsPing.processGpsPing(entry)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    public record GpsPingRestRequest(
            String delivererId,      // optional; if present must equal JWT sub, else 403
            String missionId,
            @NotNull Double latitude,
            @NotNull Double longitude,
            Double accuracyMeters,
            Double speedKmh,
            Double bearing
    ) {}
}
