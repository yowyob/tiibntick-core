package com.yowyob.tiibntick.core.gofp.adapter.in.web;

import com.yowyob.tiibntick.core.gofp.application.port.in.IMatchingUseCase;
import com.yowyob.tiibntick.core.gofp.domain.model.MatchingRequest;
import com.yowyob.tiibntick.core.gofp.domain.model.MatchingResult;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.VehicleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Tag(name = "GOFP — Matching", description = "API métier générique — Matching TOPSIS/AHP")
@RestController
@RequestMapping("/api/v1/gofp/matching")
@RequiredArgsConstructor
public class MatchingCoreController {

    private final IMatchingUseCase matchingUseCase;

    @Operation(summary = "Lancer le matching pour une annonce publiée")
    @PostMapping("/announce/{announcementId}")
    public Flux<MatchingResult> matchAnnouncement(
            @PathVariable UUID announcementId,
            @RequestParam double pickupLat,
            @RequestParam double pickupLon,
            @RequestParam(required = false) String requiredVehicleType,
            @RequestParam(required = false) Double packetWeightKg,
            @RequestParam UUID tenantId) {

        MatchingRequest request = MatchingRequest.builder()
            .announcementId(announcementId)
            .pickupLat(pickupLat)
            .pickupLon(pickupLon)
            .initialRadiusKm(1.5)
            .requiredVehicleType(requiredVehicleType != null ? VehicleType.fromValue(requiredVehicleType) : null)
            .packetWeightKg(packetWeightKg)
            .tenantId(tenantId)
            .build();

        return matchingUseCase.matchAnnouncement(request);
    }

    @Operation(summary = "Classer des candidats sans notification (prévisualisation)")
    @PostMapping("/rank")
    public Flux<MatchingResult> rank(
            @RequestParam UUID announcementId,
            @RequestParam double pickupLat,
            @RequestParam double pickupLon,
            @RequestParam(required = false) String requiredVehicleType,
            @RequestParam UUID tenantId) {

        MatchingRequest request = MatchingRequest.builder()
            .announcementId(announcementId)
            .pickupLat(pickupLat).pickupLon(pickupLon)
            .initialRadiusKm(1.5)
            .requiredVehicleType(requiredVehicleType != null ? VehicleType.fromValue(requiredVehicleType) : null)
            .tenantId(tenantId)
            .build();

        return matchingUseCase.rankCandidates(request);
    }
}
