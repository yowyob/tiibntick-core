package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.GofpFreelancerUseCase;
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

    @PostMapping
    public Mono<ResponseEntity<GofpFreelancer>> createOrUpdate(@RequestBody GofpFreelancer freelancer) {
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
    public Mono<ResponseEntity<GofpFreelancer>> updateStatus(
            @PathVariable UUID id,
            @RequestParam FreelancerStatus status) {
        return freelancerUseCase.updateStatus(id, status)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @PatchMapping("/{id}/active")
    public Mono<ResponseEntity<GofpFreelancer>> setActive(
            @PathVariable UUID id,
            @RequestParam Boolean active) {
        return freelancerUseCase.setActive(id, active)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @PatchMapping("/by-core-freelancer/{coreFreelancerId}/location")
    public Mono<ResponseEntity<Void>> updateLocation(
            @PathVariable UUID coreFreelancerId,
            @RequestParam Float lat,
            @RequestParam Float lng) {
        return freelancerUseCase.updateLocation(coreFreelancerId, lat, lng)
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
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
