package com.yowyob.tiibntick.core.realtime.adapter.in.rest;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.realtime.application.port.in.IProcessGpsPingUseCase;
import com.yowyob.tiibntick.core.realtime.domain.model.GPSStreamEntry;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    private final IProcessGpsPingUseCase processGpsPing;

    public GpsPingRestController(IProcessGpsPingUseCase processGpsPing) {
        this.processGpsPing = processGpsPing;
    }

    @PostMapping("/ping")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> ping(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @Valid @RequestBody GpsPingRestRequest body) {
        GPSStreamEntry entry = new GPSStreamEntry(
                body.delivererId(),
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
        return processGpsPing.processGpsPing(entry);
    }

    public record GpsPingRestRequest(
            @NotBlank String delivererId,
            String missionId,
            @NotNull Double latitude,
            @NotNull Double longitude,
            Double accuracyMeters,
            Double speedKmh,
            Double bearing
    ) {}
}
