package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.FreelancerProfileUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.SubscriptionUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerUpdateRequest;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerDetailsResponse;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SubscriptionStatusResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound REST adapter for delivery person profile management.
 * Delegates to the FreelancerProfileUseCase inbound port.
 *
 * @author Kengfack Lagrange
 * @date 25/01/2026
 */
@RestController
@RequestMapping("/api/freelancers")
@RequiredArgsConstructor
public class GofpFreelancerController {

    private final FreelancerProfileUseCase profileUseCase;
    private final SubscriptionUseCase subscriptionUseCase;

    @GetMapping("/{id}")
    public Mono<ResponseEntity<FreelancerDetailsResponse>> getProfile(@PathVariable UUID id) {
        return profileUseCase.getProfile(id)
                .map(ResponseEntity::ok)
                .onErrorResume(ResponseStatusException.class,
                        e -> Mono.just(ResponseEntity.status(e.getStatusCode()).build()));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Void>> updateProfile(
            @PathVariable UUID id,
            @Valid @RequestBody FreelancerUpdateRequest request) {
        return profileUseCase.updateProfile(id, request)
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .onErrorResume(ResponseStatusException.class,
                        e -> Mono.just(ResponseEntity.status(e.getStatusCode()).<Void>build()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteProfile(@PathVariable UUID id) {
        return profileUseCase.deleteProfile(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(ResponseStatusException.class,
                        e -> Mono.just(ResponseEntity.status(e.getStatusCode()).<Void>build()));
    }

    /**
     * Returns the full subscription state of a delivery person.
     *
     * Response includes:
     *   - plan type (FREE / STANDARD / ADVANCE)
     *   - status (ACTIVE / SUSPENDED / EXPIRED / ...)
     *   - quota: deliveriesUsed, deliveriesRemaining, maxDeliveries, resetDate
     *   - commission: commissionPercent (TiiBnTick share), netPercent (livreur share)
     *   - validity: startDate, endDate
     *
     * Returns 404 if the delivery person has no subscription.
     */
    @GetMapping("/{id}/subscription")
    public Mono<ResponseEntity<SubscriptionStatusResponseDTO>> getSubscriptionStatus(
            @PathVariable UUID id) {
        return subscriptionUseCase.getSubscriptionStatus(id)
                .map(ResponseEntity::ok)
                .onErrorResume(ResponseStatusException.class,
                        e -> Mono.just(ResponseEntity.status(e.getStatusCode()).build()));
    }
}
