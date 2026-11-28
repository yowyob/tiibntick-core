package com.yowyob.tiibntick.core.billing.wallet.adapter.out.incident;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IEscrowUseCase;
import com.yowyob.tiibntick.core.billing.wallet.domain.policy.EscrowOutcomePolicy;
import com.yowyob.tiibntick.core.incident.port.outbound.IPaymentFreezePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * {@link IPaymentFreezePort} of tnt-incident-core on the Kernel escrow (lot L5, spec §4.2 I27, I29, I31): an escalated
 * incident freezes the escrow of the delivery ({@code POST /escrows/{id}/freeze}), its resolution or cancellation
 * unfreezes it — unless a dispute holds the freeze. A delivery without escrow (cash, agency delivery without
 * announcement QO-28) has nothing to freeze.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentPaymentFreezeAdapter implements IPaymentFreezePort {

    static final String INCIDENT_FREEZE = "INCIDENT:";

    private final IEscrowUseCase escrows;

    @Override
    public Mono<Void> freezePayment(UUID missionId, String reason) {
        return escrows.apply(missionId, EscrowOutcomePolicy.Action.FREEZE, INCIDENT_FREEZE + reason)
                .doOnNext(e -> log.info("Escrow of delivery {} frozen ({}): {}", missionId, e.status(), reason))
                .then();
    }

    @Override
    public Mono<Void> unfreezePayment(UUID missionId, String reason) {
        return escrows.find(missionId)
                .flatMap(e -> e.reason() != null && e.reason().startsWith(IEscrowUseCase.DISPUTE_FREEZE)
                        ? Mono.fromRunnable(() -> log.info("Escrow of delivery {} kept frozen: held by {}",
                                missionId, e.reason()))
                        : escrows.apply(missionId, EscrowOutcomePolicy.Action.UNFREEZE, INCIDENT_FREEZE + reason))
                .then();
    }
}
