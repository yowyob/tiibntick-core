package com.yowyob.tiibntick.core.billing.wallet.adapter.out.incident;

import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IPaymentIntentRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.WalletStatus;
import com.yowyob.tiibntick.core.incident.port.outbound.IPaymentFreezePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adapter implementing {@link IPaymentFreezePort} (port defined in tnt-incident-core).
 *
 * <p>Allows the incident engine to freeze and unfreeze payments linked to affected missions.
 * Freezing prevents the deliverer from receiving their commission and the client's wallet
 * from being further debited while an incident is being resolved.</p>
 *
 * <p>Implementation strategy:</p>
 * <ul>
 *   <li><b>freeze</b>: locates the PaymentIntent associated with the mission's invoice,
 *       then freezes the payer's wallet (FROZEN status). An audit log reason is persisted
 *       alongside the status change.</li>
 *   <li><b>unfreeze</b>: reverts the wallet to ACTIVE status when the incident is resolved,
 *       allowing normal payment flow to resume.</li>
 * </ul>
 *
 * <p>Hexagonal position: secondary (out) adapter in tnt-billing-wallet implementing
 * a port from tnt-incident-core. Assembled in tnt-bootstrap.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class IncidentPaymentFreezeAdapter implements IPaymentFreezePort {

    private static final Logger log = LoggerFactory.getLogger(IncidentPaymentFreezeAdapter.class);

    private final IPaymentIntentRepository paymentIntentRepository;
    private final IWalletRepository walletRepository;

    public IncidentPaymentFreezeAdapter(IPaymentIntentRepository paymentIntentRepository,
                                         IWalletRepository walletRepository) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.walletRepository = walletRepository;
    }

    /**
     * Freezes the payment associated with a mission.
     *
     * <p>Lookup chain: {@code missionId} → {@code invoiceId} (via PaymentIntent) →
     * {@code walletId} → freeze wallet (status = FROZEN).</p>
     *
     * <p>If no PaymentIntent is found for the mission (e.g. payment not yet initiated),
     * the operation completes silently — there is nothing to freeze.</p>
     *
     * @param missionId the UUID of the mission whose payment must be frozen
     * @param reason    human-readable reason for the freeze (stored in audit log)
     * @return Mono completing when the freeze is applied
     */
    @Override
    public Mono<Void> freezePayment(UUID missionId, String reason) {
        log.info("Freezing payment for mission={} reason={}", missionId, reason);

        // Lookup PaymentIntent by the mission's invoice reference
        return paymentIntentRepository.findByInvoiceId(missionId.toString())
                .flatMap(intent -> walletRepository.findById(intent.getWalletId())
                        .flatMap(wallet -> {
                            if (wallet.getStatus() == WalletStatus.FROZEN) {
                                log.debug("Wallet {} is already frozen — skipping", wallet.getId());
                                return Mono.empty();
                            }
                            wallet.freeze(reason);
                            return walletRepository.save(wallet)
                                    .doOnSuccess(w -> log.info(
                                            "Wallet {} frozen for mission={} reason={}",
                                            w.getId(), missionId, reason));
                        }))
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("No PaymentIntent found for missionId={} — nothing to freeze", missionId)))
                .then();
    }

    /**
     * Unfreezes the payment associated with a mission, allowing normal operations to resume.
     *
     * <p>Called when an incident is resolved or closed. The wallet transitions from
     * FROZEN back to ACTIVE, and any pending payment processing can proceed.</p>
     *
     * <p>If the wallet is already ACTIVE (e.g. unfrozen by another process),
     * the operation completes silently.</p>
     *
     * @param missionId the UUID of the mission whose payment must be unfrozen
     * @param reason    human-readable reason for the unfreeze (stored in audit log)
     * @return Mono completing when the unfreeze is applied
     */
    @Override
    public Mono<Void> unfreezePayment(UUID missionId, String reason) {
        log.info("Unfreezing payment for mission={} reason={}", missionId, reason);

        return paymentIntentRepository.findByInvoiceId(missionId.toString())
                .flatMap(intent -> walletRepository.findById(intent.getWalletId())
                        .flatMap(wallet -> {
                            if (wallet.getStatus() != WalletStatus.FROZEN) {
                                log.debug("Wallet {} is not frozen — skipping unfreeze", wallet.getId());
                                return Mono.empty();
                            }
                            wallet.unfreeze(reason);
                            return walletRepository.save(wallet)
                                    .doOnSuccess(w -> log.info(
                                            "Wallet {} unfrozen for mission={} reason={}",
                                            w.getId(), missionId, reason));
                        }))
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("No PaymentIntent found for missionId={} — nothing to unfreeze", missionId)))
                .then();
    }
}
