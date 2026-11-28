package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.RelayPointRevocation;
import com.yowyob.tiibntick.core.gofreelancer.application.service.AdminRelayPointApplicationService.RelayPointStatusException;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/tnt-go-freelancer/relay-points")
@RequiredArgsConstructor
@RequirePermission(resource = "gofp-admin", action = "manage")
public class AdminRelayPointController {

    private final AdminRelayPointUseCase adminRelayPointUseCase;

    /**
     * Suspends an APPROVED relay point (service: local status, relay operator profile in actor-core, notification).
     * 200 (also when already suspended), 404 unknown relay point, 409 any other status (revoked included).
     */
    @PutMapping("/{id}/suspend")
    public Mono<ResponseEntity<Void>> suspendRelayPoint(@PathVariable UUID id, @RequestParam(required = false) String loginUrl) {
        log.info("Admin suspends relay point {}", id);
        return statusChange(adminRelayPointUseCase.suspendRelayPoint(id, loginUrl));
    }

    /**
     * Definitive revocation of a relay point (decision D-129): permission {@code relay:revoke} (platform administrator
     * only), body {@code {"reason": "..."}} mandatory. Delegates to the core relay hub revocation. 200 with the
     * revocation (parcels still in custody included), 400 reason missing, 404 unknown relay point,
     * 409 relay point not APPROVED/SUSPENDED (already revoked included).
     */
    @PutMapping("/{id}/revoke")
    @RequirePermission(resource = "relay", action = "revoke")
    public Mono<ResponseEntity<RelayPointRevocation>> revokeRelayPoint(@PathVariable UUID id,
                                                                       @Valid @RequestBody RevokeRelayPointRequest request,
                                                                       @RequestParam(required = false) String loginUrl,
                                                                       @CurrentUser(required = false) TntSecurityContext ctx) {
        if (ctx == null || ctx.userId() == null) {
            return Mono.just(ResponseEntity.status(401).<RelayPointRevocation>build());
        }
        log.info("Admin {} revokes relay point {}", ctx.userId(), id);
        return adminRelayPointUseCase.revokeRelayPoint(id, request.reason(), ctx.userId(), loginUrl)
                .map(ResponseEntity::ok)
                .onErrorResume(RelayPointStatusException.class,
                        e -> Mono.just(ResponseEntity.status(409).<RelayPointRevocation>build()))
                .onErrorResume(com.yowyob.tiibntick.core.geo.domain.exception.RelayHubStateException.class,
                        e -> Mono.just(ResponseEntity.status(409).<RelayPointRevocation>build()))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(e.getMessage() != null && e.getMessage().contains("not found")
                                ? ResponseEntity.notFound().<RelayPointRevocation>build()
                                : ResponseEntity.badRequest().<RelayPointRevocation>build()));
    }

    /** Body of the revocation: the reason is mandatory (D-129). */
    public record RevokeRelayPointRequest(@NotBlank @Size(max = 1000) String reason) { }

    /**
     * Reactivates a SUSPENDED relay point (service: local status, relay operator profile in actor-core,
     * notification). 200 (also when already approved), 404 unknown relay point, 409 any other status (a PENDING relay
     * point is validated, not activated; a revoked one stays revoked).
     */
    @PutMapping("/{id}/activate")
    public Mono<ResponseEntity<Void>> activateRelayPoint(@PathVariable UUID id, @RequestParam(required = false) String loginUrl) {
        log.info("Admin reactivates relay point {}", id);
        return statusChange(adminRelayPointUseCase.activateRelayPoint(id, loginUrl));
    }

    private static Mono<ResponseEntity<Void>> statusChange(Mono<Void> change) {
        return change
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .onErrorResume(RelayPointStatusException.class,
                        e -> Mono.just(ResponseEntity.status(409).<Void>build()))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(e.getMessage() != null && e.getMessage().contains("not found")
                                ? ResponseEntity.notFound().<Void>build()
                                : ResponseEntity.badRequest().<Void>build()));
    }
}
