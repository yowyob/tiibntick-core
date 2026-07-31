package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindDelivererUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IValidateKycUseCase;
import com.yowyob.tiibntick.core.actor.application.command.ValidateKycCommand;
import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.actor.domain.model.KycStatus;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerDetailsResponse;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminFreelancerUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.EmailPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service implementing AdminFreelancerUseCase.
 * Delegates KYC validation to tnt-actor-core.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFreelancerApplicationService implements AdminFreelancerUseCase {

    private final IFindDelivererUseCase findDelivererUseCase;
    private final IValidateKycUseCase validateKycUseCase;
    private final EmailPort emailPort;

    private static final UUID DEFAULT_TENANT = TenantContextHolder.SYSTEM_TENANT;

    @Override
    public Flux<FreelancerDetailsResponse> getPendingFreelancers() {
        return findDelivererUseCase.findByAgency(DEFAULT_TENANT, DEFAULT_TENANT)
                .filter(p -> p.kycStatus() == KycStatus.PENDING)
                .map(p -> {
                    FreelancerDetailsResponse r = new FreelancerDetailsResponse();
                    r.setId(p.actorId());
                    r.setStatus(p.actorStatus().name());
                    return r;
                });
    }

    @Override
    public Flux<FreelancerDetailsResponse> getAllFreelancers(FreelancerStatus status) {
        return findDelivererUseCase.findByAgency(DEFAULT_TENANT, DEFAULT_TENANT)
                .map(p -> {
                    FreelancerDetailsResponse r = new FreelancerDetailsResponse();
                    r.setId(p.actorId());
                    r.setStatus(p.actorStatus().name());
                    return r;
                });
    }

    @Override
    public Mono<FreelancerDetailsResponse> getFreelancerDetails(UUID id) {
        return findDelivererUseCase.findByActorId(DEFAULT_TENANT, id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Freelancer not found: " + id)))
                .map(p -> {
                    FreelancerDetailsResponse r = new FreelancerDetailsResponse();
                    r.setId(p.actorId());
                    r.setStatus(p.actorStatus().name());
                    r.setCreatedAt(p.createdAt().toString());
                    return r;
                });
    }

    @Override
    public Mono<Void> validateFreelancer(UUID id, boolean approved, String reason, String loginUrl) {
        KycStatus newStatus = approved ? KycStatus.VERIFIED : KycStatus.REJECTED;
        ValidateKycCommand cmd = new ValidateKycCommand(
                DEFAULT_TENANT, id, ActorType.PERMANENT_DELIVERER, newStatus, "admin", reason);
        return validateKycUseCase.validateKyc(cmd)
                .then(Mono.fromRunnable(() -> {
                    // Send notification email (fire-and-forget)
                    if (approved) emailPort.sendAccountApproved(id.toString(), loginUrl);
                    else emailPort.sendAccountRejected(id.toString(), reason, loginUrl);
                }));
    }

    @Override
    public Mono<Void> suspendFreelancer(UUID id, String loginUrl) {
        log.info("Suspending freelancer {}", id);
        return Mono.fromRunnable(() -> emailPort.sendAccountSuspended(id.toString(), loginUrl));
    }

    @Override
    public Mono<Void> revokeFreelancer(UUID id, String loginUrl) {
        log.info("Revoking freelancer {}", id);
        return Mono.fromRunnable(() -> emailPort.sendAccountRevoked(id.toString(), loginUrl));
    }

    @Override
    public Mono<Void> activateFreelancer(UUID id, String loginUrl) {
        log.info("Activating freelancer {}", id);
        return Mono.fromRunnable(() -> emailPort.sendAccountApproved(id.toString(), loginUrl));
    }
}
