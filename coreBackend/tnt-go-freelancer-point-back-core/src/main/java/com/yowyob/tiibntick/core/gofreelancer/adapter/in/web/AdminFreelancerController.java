package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.SubscriptionUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SubscriptionStatusResponseDTO;

import java.util.UUID;

/**
 * Inbound REST adapter for admin freelancer management in Core (Layer 6).
 * Follows the route: /api/v1/admin/tnt-go-freelancer/freelancers
 * Enforces RBAC directly
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/tnt-go-freelancer/freelancers")
@RequiredArgsConstructor
public class AdminFreelancerController {

    private final SubscriptionUseCase subscriptionUseCase;

    // Note: Depends on AdminFreelancerUseCase from application layer.
    // For now we mock the signature until UseCase is fully defined.

    @PutMapping("/{id}/validate")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> validateRegistration(@PathVariable UUID id, @RequestParam boolean approved,
            @RequestParam(required = false) String reason, @RequestParam(required = false) String loginUrl) {
        log.info("Direct Admin validate freelancer {} to {}", id, approved);
        // return adminUseCase.validateFreelancer(id, approved, reason,
        // loginUrl).then(Mono.just(ResponseEntity.ok().<Void>build()));
        return Mono.just(ResponseEntity.ok().build());
    }

    @PutMapping("/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> suspendFreelancer(@PathVariable UUID id,
            @RequestParam(required = false) String loginUrl) {
        log.info("Direct Admin suspend freelancer {}", id);
        return Mono.just(ResponseEntity.ok().build());
    }

    @PutMapping("/{id}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> revokeFreelancer(@PathVariable UUID id,
            @RequestParam(required = false) String loginUrl) {
        log.info("Direct Admin revoke freelancer {}", id);
        return Mono.just(ResponseEntity.ok().build());
    }

    @PutMapping("/{id}/price")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<SubscriptionStatusResponseDTO>> updateSubscriptionPrice(@PathVariable UUID id,
            @RequestParam Float price) {
        return subscriptionUseCase.updateSubscriptionPrice(id, price)
                .map(dto -> ResponseEntity.ok(dto))
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }
}
