package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerUpdateRequest;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SubscriptionStatusResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerDetailsResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerProfileUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.SubscriptionUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound REST adapter for delivery person profile management.
 * Delegates to the FreelancerProfileUseCase inbound port.
 *
 * @author MANFOUO BRAUN
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
                .onErrorResume(this::mapNotFound);
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Void>> updateProfile(
            @PathVariable UUID id,
            @Valid @RequestBody FreelancerUpdateRequest request) {
        return profileUseCase.updateProfile(id, request)
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .onErrorResume(e -> mapNotFound(e).map(r -> ResponseEntity.status(r.getStatusCode()).build()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteProfile(@PathVariable UUID id) {
        return profileUseCase.deleteProfile(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(ResponseStatusException.class,
                        e -> Mono.just(ResponseEntity.status(e.getStatusCode()).<Void>build()))
                .onErrorResume(UnsupportedOperationException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).<Void>build()));
    }

    /**
     * Returns the full subscription state of a delivery person.
     *
     * Returns 404 if the delivery person has no subscription.
     */
    @GetMapping("/{id}/subscription")
    public Mono<ResponseEntity<SubscriptionStatusResponseDTO>> getSubscriptionStatus(
            @PathVariable UUID id) {
        return subscriptionUseCase.getSubscriptionStatus(id)
                .map(ResponseEntity::ok)
                .onErrorResume(this::mapNotFound);
    }

    private <T> Mono<ResponseEntity<T>> mapNotFound(Throwable e) {
        if (e instanceof ResponseStatusException rse) {
            return Mono.just(ResponseEntity.status(rse.getStatusCode()).build());
        }
        if (e instanceof IllegalArgumentException iae) {
            String msg = iae.getMessage() != null ? iae.getMessage().toLowerCase() : "";
            if (msg.contains("not found")) {
                return Mono.just(ResponseEntity.notFound().build());
            }
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return Mono.error(e);
    }
}
