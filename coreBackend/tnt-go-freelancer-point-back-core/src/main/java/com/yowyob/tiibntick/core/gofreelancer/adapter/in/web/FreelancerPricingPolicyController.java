package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerPricingDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerPricingPolicyUseCase;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
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
 * Freelancer pricing policy REST endpoints.
 *
 * @author MANFOUO BRAUN
 */
@RestController
@RequestMapping("/api/freelancers")
@RequiredArgsConstructor
public class FreelancerPricingPolicyController {

    private final FreelancerPricingPolicyUseCase freelancerPricingPolicyUseCase;

    @GetMapping("/{id}/policies")
    public Mono<ResponseEntity<FreelancerPricingDTO>> getPolicy(@PathVariable("id") UUID freelancerId) {
        return freelancerPricingPolicyUseCase.getPolicy(freelancerId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/policies")
    public Mono<FreelancerPricingDTO> upsertPolicy(
            @PathVariable("id") UUID freelancerId,
            @Valid @RequestBody FreelancerPricingDTO request,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        UUID callerId = ctx != null ? ctx.userId() : null;
        UUID tenantId = ctx != null ? ctx.tenantId() : null;
        boolean admin = ctx != null && ctx.hasPermission("gofp-admin", "manage");
        return freelancerPricingPolicyUseCase.upsertPolicyAsCaller(freelancerId, request, tenantId, callerId, admin);
    }
}
