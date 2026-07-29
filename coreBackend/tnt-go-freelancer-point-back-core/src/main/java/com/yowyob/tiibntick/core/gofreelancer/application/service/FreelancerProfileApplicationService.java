package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.actor.application.port.in.IFindDelivererUseCase;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerUpdateRequest;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerDetailsResponse;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.FreelancerProfileUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service implementing FreelancerProfileUseCase.
 * Reads from tnt-actor-core; write/update via FreelancerUpdateValidator.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreelancerProfileApplicationService implements FreelancerProfileUseCase {

    private final IFindDelivererUseCase findDelivererUseCase;
    private final FreelancerUpdateValidator updateValidator;

    private static final UUID DEFAULT_TENANT = TenantContextHolder.SYSTEM_TENANT;

    @Override
    public Mono<FreelancerDetailsResponse> getProfile(UUID id) {
        return findDelivererUseCase.findByActorId(DEFAULT_TENANT, id)
                .map(this::toResponse)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Freelancer not found: " + id)));
    }

    @Override
    public Mono<Void> updateProfile(UUID id, FreelancerUpdateRequest request) {
        return updateValidator.validate(request).then();
    }

    @Override
    public Mono<Void> deleteProfile(UUID id) {
        return Mono.error(new UnsupportedOperationException(
                "Profile deletion must go through admin validation flow"));
    }

    private FreelancerDetailsResponse toResponse(DelivererProfile p) {
        FreelancerDetailsResponse resp = new FreelancerDetailsResponse();
        resp.setId(p.actorId());
        resp.setStatus(p.actorStatus() != null ? p.actorStatus().name() : null);
        resp.setCreatedAt(p.createdAt() != null ? p.createdAt().toString() : null);
        resp.setUpdatedAt(p.updatedAt() != null ? p.updatedAt().toString() : null);
        return resp;
    }
}
