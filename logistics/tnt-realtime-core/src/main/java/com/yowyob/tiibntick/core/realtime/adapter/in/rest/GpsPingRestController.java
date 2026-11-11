package com.yowyob.tiibntick.core.realtime.adapter.in.rest;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.realtime.application.port.in.IProcessGpsPingUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.out.IMissionAssignmentPort;
import org.springframework.web.server.ResponseStatusException;
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
import java.util.UUID;

/**
 * HTTP ingress for GPS pings — consumed by tnt-agency {@code RealtimeCoreClient}.
 */
@RestController
@RequestMapping("/api/v1/realtime/gps")
public class GpsPingRestController {

    private static final Logger log = LoggerFactory.getLogger(GpsPingRestController.class);

    private final IProcessGpsPingUseCase processGpsPing;
    private final java.util.function.Supplier<IMissionAssignmentPort> missionAssignment;

    @org.springframework.beans.factory.annotation.Autowired
    public GpsPingRestController(IProcessGpsPingUseCase processGpsPing,
                                 org.springframework.beans.factory.ObjectProvider<IMissionAssignmentPort> missionAssignment) {
        this.processGpsPing = processGpsPing;
        this.missionAssignment = missionAssignment::getIfAvailable;
    }

    public GpsPingRestController(IProcessGpsPingUseCase processGpsPing, IMissionAssignmentPort missionAssignment) {
        this.processGpsPing = processGpsPing;
        this.missionAssignment = () -> missionAssignment;
    }

    /** Without a mission-assignment check (tests, deployments without a delivery owner). */
    public GpsPingRestController(IProcessGpsPingUseCase processGpsPing) {
        this.processGpsPing = processGpsPing;
        this.missionAssignment = () -> null;
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

        return assertMissionBelongsToCaller(currentUser, body.missionId())
                .then(Mono.defer(() -> ingest(currentUser, subjectId, body)));
    }

    /** A deliverer may only feed the mission they carry. */
    private Mono<Void> assertMissionBelongsToCaller(TntUserIdentity caller, String missionId) {
        IMissionAssignmentPort port = missionAssignment.get();
        if (port == null || missionId == null || missionId.isBlank()) {
            return Mono.empty();
        }
        UUID mission;
        try {
            mission = UUID.fromString(missionId);
        } catch (IllegalArgumentException notAUuid) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "missionId must be a UUID"));
        }
        return port.isMissionAssignedTo(caller.tenantId(), mission, caller.userId())
                .flatMap(ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        return Mono.<Void>empty();
                    }
                    log.warn("GPS ping rejected: user {} is not assigned to mission {} (tenant {})",
                            caller.userId(), mission, caller.tenantId());
                    return Mono.<Void>error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "You are not the deliverer of this mission"));
                });
    }

    private Mono<ResponseEntity<Void>> ingest(TntUserIdentity currentUser, String subjectId, GpsPingRestRequest body) {
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
