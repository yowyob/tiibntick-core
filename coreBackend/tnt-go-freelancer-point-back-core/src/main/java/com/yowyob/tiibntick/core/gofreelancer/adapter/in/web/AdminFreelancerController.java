package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SubscriptionStatusResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminFreelancerUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.SubscriptionUseCase;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Admin REST adapter for freelancer lifecycle management (F-R2).
 *
 * <p>All routes are guarded by {@code @RequirePermission(resource = "gofp-admin", action = "manage")}.
 * Each mutation delegates to {@link AdminFreelancerUseCase}, which:
 * <ol>
 *   <li>Enforces the domain state machine via {@code GofpFreelancerUseCase.updateStatus}.</li>
 *   <li>Notifies {@code tnt-actor-core} KYC (best-effort).</li>
 *   <li>Sends the account notification email (fire-and-forget).</li>
 * </ol>
 * Invalid transitions → 409. Unknown freelancer → 404.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/tnt-go-freelancer/freelancers")
@RequiredArgsConstructor
@RequirePermission(resource = "gofp-admin", action = "manage")
public class AdminFreelancerController {

    private final AdminFreelancerUseCase adminFreelancerUseCase;
    private final SubscriptionUseCase subscriptionUseCase;

    @PutMapping("/{id}/validate")
    public Mono<ResponseEntity<Void>> validateRegistration(
            @PathVariable UUID id,
            @RequestParam boolean approved,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String loginUrl) {
        return adminFreelancerUseCase.validateFreelancer(id, approved, reason, loginUrl)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @PutMapping("/{id}/suspend")
    public Mono<ResponseEntity<Void>> suspendFreelancer(
            @PathVariable UUID id,
            @RequestParam(required = false) String loginUrl) {
        return adminFreelancerUseCase.suspendFreelancer(id, loginUrl)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @PutMapping("/{id}/revoke")
    public Mono<ResponseEntity<Void>> revokeFreelancer(
            @PathVariable UUID id,
            @RequestParam(required = false) String loginUrl) {
        return adminFreelancerUseCase.revokeFreelancer(id, loginUrl)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @PutMapping("/{id}/price")
    public Mono<ResponseEntity<SubscriptionStatusResponseDTO>> updateSubscriptionPrice(
            @PathVariable UUID id,
            @RequestParam Float price) {
        return subscriptionUseCase.updateSubscriptionPrice(id, price)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }
}
