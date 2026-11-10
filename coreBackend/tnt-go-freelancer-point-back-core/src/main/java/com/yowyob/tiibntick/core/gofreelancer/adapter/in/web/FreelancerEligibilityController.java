package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerEligibilityDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerEligibilityUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * What a delivery person accepts to carry and when ({@code /api/freelancers/{id}/eligibility}).
 * {@code {id}} is the delivery-person id, like {@code /policies}. GET answers 404 until configured:
 * no stored preference means no constraint.
 */
@RestController
@RequestMapping("/api/freelancers")
@RequiredArgsConstructor
public class FreelancerEligibilityController {

    private final FreelancerEligibilityUseCase eligibilityUseCase;

    @GetMapping("/{id}/eligibility")
    public Mono<ResponseEntity<FreelancerEligibilityDTO>> get(@PathVariable("id") UUID deliveryPersonId) {
        return eligibilityUseCase.get(deliveryPersonId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/eligibility")
    public Mono<FreelancerEligibilityDTO> replace(
            @PathVariable("id") UUID deliveryPersonId,
            @Valid @RequestBody FreelancerEligibilityDTO request,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        UUID callerId = ctx != null ? ctx.userId() : null;
        boolean admin = ctx != null && ctx.hasPermission("gofp-admin", "manage");
        return eligibilityUseCase.replaceAsCaller(deliveryPersonId, request, callerId, admin);
    }
}
