package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.GofpFreelancerUseCase;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for GofpFreelancer profile management.
 *
 * Base path: /api/v1/gofp/freelancer-profiles
 *
 * @author François-Charles ATANGA
 */
@RestController
@RequestMapping("/api/v1/gofp/freelancer-profiles")
@RequiredArgsConstructor
public class GofpFreelancerProfileController {

    private final GofpFreelancerUseCase freelancerUseCase;
    private final com.yowyob.tiibntick.core.gofreelancer.application.service.CourierPresenceService presence;

    /** Body of {@code PUT /me/location}. */
    public record LocationBody(Double latitude, Double longitude) {}

    /** Body and answer of {@code /me/availability}: {@code AVAILABLE} or {@code PAUSED}. */
    public record AvailabilityBody(String status) {}

    /** Lot C-52 — the caller's own position (profile resolved from the token). */
    @PutMapping("/me/location")
    public Mono<ResponseEntity<Void>> updateMyLocation(
            @RequestBody LocationBody body,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return presence.updateMyLocation(ctx, body == null ? null : body.latitude(),
                        body == null ? null : body.longitude())
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @GetMapping("/me/availability")
    public Mono<AvailabilityBody> myAvailability(@CurrentUser(required = false) TntSecurityContext ctx) {
        return presence.myAvailability(ctx).map(AvailabilityBody::new);
    }

    @PutMapping("/me/availability")
    public Mono<AvailabilityBody> setMyAvailability(
            @RequestBody AvailabilityBody body,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return presence.setMyAvailability(ctx, body == null ? null : body.status()).map(AvailabilityBody::new);
    }

    @PostMapping
    public Mono<ResponseEntity<GofpFreelancer>> createOrUpdate(
            @RequestBody GofpFreelancer freelancer,
            @CurrentUser TntSecurityContext securityContext) {
        if (securityContext == null || securityContext.userId() == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        freelancer.setCoreUserId(securityContext.userId());
        return freelancerUseCase.createOrUpdate(freelancer)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<GofpFreelancer>> getById(@PathVariable UUID id) {
        return freelancerUseCase.findById(id)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/by-core-freelancer/{coreFreelancerId}")
    public Mono<ResponseEntity<GofpFreelancer>> getByCoreFreelancerId(@PathVariable UUID coreFreelancerId) {
        return freelancerUseCase.findByCoreFreelancerId(coreFreelancerId)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/by-core-user/{coreUserId}")
    public Mono<ResponseEntity<GofpFreelancer>> getByCoreUserId(@PathVariable UUID coreUserId) {
        return freelancerUseCase.findByCoreUserId(coreUserId)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping
    public Flux<GofpFreelancer> getAll() {
        return freelancerUseCase.findAll();
    }

    @GetMapping("/active-approved")
    public Flux<GofpFreelancer> getActiveApproved() {
        return freelancerUseCase.findActiveApproved();
    }

    @GetMapping("/by-status/{status}")
    public Flux<GofpFreelancer> getByStatus(@PathVariable FreelancerStatus status) {
        return freelancerUseCase.findByStatus(status);
    }

    @PatchMapping("/{id}/status")
    @RequirePermission(resource = "gofp-admin", action = "manage")
    public Mono<ResponseEntity<GofpFreelancer>> updateStatus(
            @PathVariable UUID id,
            @RequestParam FreelancerStatus status) {
        return freelancerUseCase.updateStatus(id, status)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @PatchMapping("/{id}/active")
    @RequirePermission(resource = "gofp-admin", action = "manage")
    public Mono<ResponseEntity<GofpFreelancer>> setActive(
            @PathVariable UUID id,
            @RequestParam Boolean active) {
        return freelancerUseCase.setActive(id, active)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    /** Lot C-52 — that courier himself or staff only (403 otherwise); bounds checked (400). */
    @PatchMapping("/by-core-freelancer/{coreFreelancerId}/location")
    public Mono<ResponseEntity<Void>> updateLocation(
            @PathVariable UUID coreFreelancerId,
            @RequestParam Double lat,
            @RequestParam Double lng,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return presence.updateLocationOf(coreFreelancerId, ctx, lat, lng)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @PostMapping("/by-core-freelancer/{coreFreelancerId}/record-delivery-success")
    public Mono<ResponseEntity<Void>> recordSuccess(@PathVariable UUID coreFreelancerId) {
        return freelancerUseCase.recordSuccessfulDelivery(coreFreelancerId)
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @PostMapping("/by-core-freelancer/{coreFreelancerId}/record-delivery-failure")
    public Mono<ResponseEntity<Void>> recordFailure(@PathVariable UUID coreFreelancerId) {
        return freelancerUseCase.recordFailedDelivery(coreFreelancerId)
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable UUID id) {
        return freelancerUseCase.delete(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
