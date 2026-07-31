package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.actor.application.port.in.IFindDelivererUseCase;
import com.yowyob.tiibntick.core.actor.domain.exception.DelivererNotFoundException;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerUpdateRequest;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerDetailsResponse;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerProfileUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service implementing FreelancerProfileUseCase.
 * Reads from tnt-actor-core; falls back to local GofpFreelancer enrichment.
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreelancerProfileApplicationService implements FreelancerProfileUseCase {

    private final IFindDelivererUseCase findDelivererUseCase;
    private final FreelancerUpdateValidator updateValidator;
    private final GofpFreelancerRepository gofpFreelancerRepository;
    private final GofpUserRepository gofpUserRepository;

    private static final UUID DEFAULT_TENANT = TenantContextHolder.SYSTEM_TENANT;

    @Override
    public Mono<FreelancerDetailsResponse> getProfile(UUID id) {
        return findDelivererUseCase.findByActorId(DEFAULT_TENANT, id)
                .map(this::toResponse)
                .onErrorResume(DelivererNotFoundException.class, e -> loadFromGofp(id))
                .switchIfEmpty(loadFromGofp(id));
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

    private Mono<FreelancerDetailsResponse> loadFromGofp(UUID coreFreelancerId) {
        return gofpFreelancerRepository.findByCoreFreelancerId(coreFreelancerId)
                .flatMap(fl -> gofpUserRepository.findByCoreUserId(fl.getCoreUserId())
                        .map(user -> toResponse(fl, user))
                        .defaultIfEmpty(toResponse(fl, null)))
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Freelancer not found: " + coreFreelancerId)));
    }

    private FreelancerDetailsResponse toResponse(DelivererProfile p) {
        FreelancerDetailsResponse resp = new FreelancerDetailsResponse();
        resp.setId(p.actorId());
        resp.setStatus(p.actorStatus() != null ? p.actorStatus().name() : null);
        resp.setCreatedAt(p.createdAt() != null ? p.createdAt().toString() : null);
        resp.setUpdatedAt(p.updatedAt() != null ? p.updatedAt().toString() : null);
        return resp;
    }

    private FreelancerDetailsResponse toResponse(GofpFreelancer fl, GofpUser user) {
        FreelancerDetailsResponse resp = new FreelancerDetailsResponse();
        resp.setId(fl.getCoreFreelancerId());
        resp.setCommercialName(fl.getCommercialName());
        resp.setNuiNumber(fl.getTaxpayerNumber());
        resp.setStatus(fl.getStatus() != null ? fl.getStatus().name() : null);
        resp.setCreatedAt(fl.getCreatedAt() != null ? fl.getCreatedAt().toString() : null);
        resp.setUpdatedAt(fl.getUpdatedAt() != null ? fl.getUpdatedAt().toString() : null);
        if (user != null) {
            resp.setFirstName(user.getFirstName());
            resp.setLastName(user.getLastName());
            resp.setEmail(user.getEmail());
            resp.setPhone(user.getPhone());
            resp.setNationalId(user.getCniNumber());
        }
        return resp;
    }
}
