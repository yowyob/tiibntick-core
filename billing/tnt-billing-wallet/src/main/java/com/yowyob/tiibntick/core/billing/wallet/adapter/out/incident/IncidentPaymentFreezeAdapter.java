package com.yowyob.tiibntick.core.billing.wallet.adapter.out.incident;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletHoldUseCase;
import com.yowyob.tiibntick.core.incident.port.outbound.IPaymentFreezePort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adapter implementing {@link IPaymentFreezePort} (port defined in tnt-incident-core).
 *
 * <p>The Kernel wallet has no freeze or reservation, so a "freeze" is a <em>local hold</em>: the
 * amount the beneficiary received for the mission is recorded in {@code billing.wallet_holds}
 * and {@code KernelWalletService} refuses any outgoing transfer/withdrawal that would dip into
 * it. Unfreezing releases the holds. Missions that were never paid through the Kernel wallet
 * (cash, unpaid) have nothing to hold and are skipped with a warning.
 *
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class IncidentPaymentFreezeAdapter implements IPaymentFreezePort {

    /** A mission is a delivery; settlements of deliveries are keyed on the delivery id. */
    static final String REFERENCE_TYPE = "DELIVERY";

    private static final Logger log = LoggerFactory.getLogger(IncidentPaymentFreezeAdapter.class);

    private final IWalletHoldUseCase holds;

    @Override
    public Mono<Void> freezePayment(UUID missionId, String reason) {
        log.info("Holding payment for mission={} reason={}", missionId, reason);
        return holds.holdSettlement(REFERENCE_TYPE, missionId, reason).then();
    }

    @Override
    public Mono<Void> unfreezePayment(UUID missionId, String reason) {
        log.info("Releasing payment hold for mission={} reason={}", missionId, reason);
        return holds.releaseHolds(REFERENCE_TYPE, missionId).then();
    }
}
