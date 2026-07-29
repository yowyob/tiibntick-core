package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.ManualRerouteRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerReroutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;

import java.util.UUID;

/**
 * REST controller for rerouting a freelancer during an active delivery.
 *
 * <p>The reroute is scoped to an existing delivery that must be in {@code IN_TRANSIT} status.</p>
 */
@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class FreelancerReroutingController {

    private final FreelancerReroutingService reroutingService;

    /**
     * Triggers a manual reroute for a delivery currently in transit.
     *
     * @param deliveryId the delivery UUID to reroute
     * @param request    the reroute details (tenantId, newRouteId, reason)
     * @return 202 Accepted when the reroute alert has been dispatched
     */
    @PostMapping("/{deliveryId}/reroute")
    public Mono<ResponseEntity<Void>> rerouteDelivery(
            @PathVariable UUID deliveryId,
            @RequestBody ManualRerouteRequestDTO request,
            @AuthenticationPrincipal TntSecurityContext securityContext) {

        return reroutingService.rerouteDelivery(
                        deliveryId,
                        securityContext.tenantId().toString(),
                        request.getNewRouteId(),
                        request.getReason()
                )
                .then(Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED).build()));
    }
}
