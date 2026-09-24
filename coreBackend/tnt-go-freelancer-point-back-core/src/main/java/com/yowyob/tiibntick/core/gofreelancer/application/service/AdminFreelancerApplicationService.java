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
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.GofpFreelancerUseCase;
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
    private final GofpFreelancerUseCase freelancerUseCase;
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
        FreelancerStatus target = approved ? FreelancerStatus.APPROVED : FreelancerStatus.REJECTED;
        KycStatus newKycStatus = approved ? KycStatus.VERIFIED : KycStatus.REJECTED;
        // 1. State machine enforces transition; throws FreelancerNotFoundException or
        //    InvalidFreelancerStatusTransitionException on failure — both propagate upstream.
        return freelancerUseCase.updateStatus(id, target)
                // 2. KYC update to tnt-actor-core (best-effort: secondary bounded context must
                //    not block a valid GOFP state transition).
                .flatMap(f -> {
                    if (f.getCoreFreelancerId() == null) return Mono.<Void>empty();
                    ValidateKycCommand cmd = new ValidateKycCommand(
                            DEFAULT_TENANT, f.getCoreFreelancerId(),
                            ActorType.PERMANENT_DELIVERER, newKycStatus, "admin", reason);
                    return validateKycUseCase.validateKyc(cmd)
                            .onErrorResume(e -> {
                                log.warn("KYC update skipped for freelancer {} — actor-core unreachable: {}",
                                        id, e.getMessage());
                                return Mono.empty();
                            });
                })
                // 3. Notification fire-and-forget — mail failure must never fail the transition.
                .doOnSuccess(ignored -> {
                    log.info("[GOFP-NOTIFY] {} notification triggered for freelancer {}",
                            approved ? "ACCOUNT_APPROVED" : "ACCOUNT_REJECTED", id);
                    try {
                        if (approved) emailPort.sendAccountApproved(id.toString(), loginUrl);
                        else emailPort.sendAccountRejected(id.toString(), reason, loginUrl);
                    } catch (Exception e) {
                        log.warn("Email notification failed for freelancer {}: {}", id, e.getMessage());
                    }
                });
    }

    @Override
    public Mono<Void> suspendFreelancer(UUID id, String loginUrl) {
        return freelancerUseCase.updateStatus(id, FreelancerStatus.SUSPENDED)
                .doOnSuccess(f -> {
                    log.info("[GOFP-NOTIFY] ACCOUNT_SUSPENDED notification triggered for freelancer {}", id);
                    try {
                        emailPort.sendAccountSuspended(id.toString(), loginUrl);
                    } catch (Exception e) {
                        log.warn("Email notification failed for freelancer {}: {}", id, e.getMessage());
                    }
                })
                .then();
    }

    @Override
    public Mono<Void> revokeFreelancer(UUID id, String loginUrl) {
        return freelancerUseCase.updateStatus(id, FreelancerStatus.REVOKED)
                .doOnSuccess(f -> {
                    log.info("[GOFP-NOTIFY] ACCOUNT_REVOKED notification triggered for freelancer {}", id);
                    try {
                        emailPort.sendAccountRevoked(id.toString(), loginUrl);
                    } catch (Exception e) {
                        log.warn("Email notification failed for freelancer {}: {}", id, e.getMessage());
                    }
                })
                .then();
    }

    @Override
    public Mono<Void> activateFreelancer(UUID id, String loginUrl) {
        return freelancerUseCase.updateStatus(id, FreelancerStatus.APPROVED)
                .doOnSuccess(f -> {
                    log.info("[GOFP-NOTIFY] ACCOUNT_APPROVED notification triggered for freelancer {}", id);
                    try {
                        emailPort.sendAccountApproved(id.toString(), loginUrl);
                    } catch (Exception e) {
                        log.warn("Email notification failed for freelancer {}: {}", id, e.getMessage());
                    }
                })
                .then();
    }
}
