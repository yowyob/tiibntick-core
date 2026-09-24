package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerLocationUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerLocationUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;

import java.util.UUID;

/**
 * Inbound REST adapter for delivery person location updates.
 * Delegates to the FreelancerLocationUseCase inbound port, which forwards
 * the GPS ping to the centralized tnt-realtime-core pipeline.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@RestController
@RequestMapping("/api/freelancers")
@RequiredArgsConstructor
public class FreelancerLocationController {

    private final FreelancerLocationUseCase locationUseCase;

    @PatchMapping("/{id}/location")
    public Mono<ResponseEntity<Void>> updateLocation(
            @PathVariable UUID id,
            @Valid @RequestBody FreelancerLocationUpdateRequest request,
            @CurrentUser TntSecurityContext securityContext) {
        if (securityContext == null || securityContext.tenantId() == null) {
            return Mono.error(new IllegalArgumentException(
                    "Authenticated tenant context is required to update freelancer location"));
        }
        if (!id.equals(securityContext.userId())) {
            log.warn("Position spoofing attempt: caller {} tried to write position for {} (tenant {})",
                    securityContext.userId(), id, securityContext.tenantId());
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).<Void>build());
        }
        return locationUseCase.updateLocation(
                        id,
                        securityContext.tenantId().toString(),
                        request.getLatitude(),
                        request.getLongitude(),
                        request.getSpeedKmh(),
                        request.getBearing(),
                        request.getAccuracy(),
                        request.getMissionId(),
                        request.getFreelancerOrgId())
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }
}
