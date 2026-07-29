package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.actor.application.port.in.IValidateKycUseCase;
import com.yowyob.tiibntick.core.actor.application.command.ValidateKycCommand;
import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.actor.domain.model.KycStatus;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.RelayPointDetailsResponse;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.EmailPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service implementing AdminRelayPointUseCase.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRelayPointApplicationService implements AdminRelayPointUseCase {

    private final IValidateKycUseCase validateKycUseCase;
    private final EmailPort emailPort;

    private static final UUID DEFAULT_TENANT = TenantContextHolder.SYSTEM_TENANT;

    @Override
    public Flux<RelayPointDetailsResponse> getPendingRelayPoints() {
        return Flux.empty(); // Requires relay-point actor repository — stub for now
    }

    @Override
    public Flux<RelayPointDetailsResponse> getAllRelayPoints(RelayPointStatus status) {
        return Flux.empty();
    }

    @Override
    public Mono<RelayPointDetailsResponse> getRelayPointDetails(UUID id) {
        return Mono.just(RelayPointDetailsResponse.builder().id(id).build());
    }

    @Override
    public Mono<Void> validateRelayPoint(UUID id, boolean approved, String reason, String loginUrl) {
        KycStatus newStatus = approved ? KycStatus.VERIFIED : KycStatus.REJECTED;
        ValidateKycCommand cmd = new ValidateKycCommand(
                DEFAULT_TENANT, id, ActorType.RELAY_OPERATOR, newStatus, "admin", reason);
        return validateKycUseCase.validateKyc(cmd)
                .then(Mono.fromRunnable(() -> {
                    if (approved) emailPort.sendAccountApproved(id.toString(), loginUrl);
                    else emailPort.sendAccountRejected(id.toString(), reason, loginUrl);
                }));
    }

    @Override
    public Mono<Void> suspendRelayPoint(UUID id, String loginUrl) {
        log.info("Suspending RelayPoint {}", id);
        return Mono.fromRunnable(() -> emailPort.sendAccountSuspended(id.toString(), loginUrl));
    }

    @Override
    public Mono<Void> revokeRelayPoint(UUID id, String loginUrl) {
        log.info("Revoking RelayPoint {}", id);
        return Mono.fromRunnable(() -> emailPort.sendAccountRevoked(id.toString(), loginUrl));
    }

    @Override
    public Mono<Void> activateRelayPoint(UUID id, String loginUrl) {
        log.info("Activating RelayPoint {}", id);
        return Mono.fromRunnable(() -> emailPort.sendAccountApproved(id.toString(), loginUrl));
    }
}
