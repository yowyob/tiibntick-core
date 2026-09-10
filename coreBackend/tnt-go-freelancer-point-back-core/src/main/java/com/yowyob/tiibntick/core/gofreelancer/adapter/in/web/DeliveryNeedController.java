package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryNeedUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryNeedRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.DeliveryNeedResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerCandidateDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AssignFreelancerRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/delivery-needs")
@RequiredArgsConstructor
public class DeliveryNeedController {

    private final DeliveryNeedUseCase deliveryNeedUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DeliveryNeedResponseDTO> createDeliveryNeed(
            @RequestBody DeliveryNeedRequestDTO request,
            @CurrentUser TntSecurityContext securityContext) {
        if (securityContext == null || securityContext.userId() == null) {
            return Mono.error(new IllegalArgumentException(
                    "Authenticated user context is required to create a delivery need"));
        }
        request.setUserId(securityContext.userId());
        return deliveryNeedUseCase.createDeliveryNeed(request);
    }

    @GetMapping
    public Flux<DeliveryNeedResponseDTO> getAllDeliveryNeeds() {
        return deliveryNeedUseCase.getAllDeliveryNeeds();
    }

    @GetMapping("/{id}")
    public Mono<DeliveryNeedResponseDTO> getDeliveryNeed(@PathVariable UUID id) {
        return deliveryNeedUseCase.getDeliveryNeed(id);
    }

    @GetMapping("/user/{userId}")
    public Flux<DeliveryNeedResponseDTO> getDeliveryNeedsByUser(@PathVariable UUID userId) {
        return deliveryNeedUseCase.getDeliveryNeedsByUserId(userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteDeliveryNeed(@PathVariable UUID id) {
        return deliveryNeedUseCase.deleteDeliveryNeed(id);
    }

    @GetMapping("/{id}/candidates")
    public Flux<FreelancerCandidateDTO> getCandidatesWithPricing(@PathVariable UUID id) {
        return deliveryNeedUseCase.getCandidatesWithPricing(id);
    }

    @PostMapping("/{id}/assign")
    public Mono<ResponseEntity<DeliveryNeedResponseDTO>> assignFreelancer(
            @PathVariable UUID id,
            @RequestBody AssignFreelancerRequestDTO request) {
        return deliveryNeedUseCase.assignFreelancer(id, request.getFreelancerId())
                .map(ResponseEntity::ok);
    }
}
