package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.CashCollection;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.CashCollectionRequest;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Challenge;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Compensation;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.CompensationRequest;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Escrow;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.EscrowHoldRequest;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Fund;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletDto;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The Kernel holds the money (decision [H], spec §4): escrow of a delivery price, cash collected by a courier, the
 * platform compensation fund. The Core keeps mirrors only.
 *
 * <p>Two identities: the operations the payer or the courier confirms with their code ({@code escrowChallenge},
 * {@code holdEscrow}, {@code remitChallenge}, {@code remit}) run with the caller's bearer, inside their request; every
 * other operation is a platform operation (Kafka consumer, scheduler, support) and runs with the Core's service
 * account. Errors propagate ({@code WebClientResponseException}); an empty envelope is an empty {@link Mono}.
 */
public interface IKernelSettlementPort {

    // ── escrow (payer's code) ───────────────────────────────────────────────
    Mono<Challenge> escrowChallenge(EscrowHoldRequest request);

    Mono<Escrow> holdEscrow(EscrowHoldRequest request);

    // ── escrow (platform) ───────────────────────────────────────────────────
    /** The escrow of a subject, empty when there is none (idempotent resumption of a hold). */
    Mono<Escrow> findEscrow(String subjectType, UUID subjectId);

    Mono<Escrow> getEscrow(UUID escrowId);

    Mono<Escrow> releaseEscrow(UUID escrowId);

    Mono<Escrow> refundEscrow(UUID escrowId, String reason);

    Mono<Escrow> freezeEscrow(UUID escrowId, String reason);

    Mono<Escrow> unfreezeEscrow(UUID escrowId, String reason);

    // ── cash collection ─────────────────────────────────────────────────────
    Mono<CashCollection> openCashCollection(CashCollectionRequest request);

    Mono<CashCollection> getCashCollection(UUID collectionId);

    Mono<CashCollection> markCollected(UUID collectionId, BigDecimal amount);

    Mono<CashCollection> cancelCashCollection(UUID collectionId);

    /** Courier's bearer. */
    Mono<Challenge> remitChallenge(UUID collectionId);

    /** Courier's bearer. */
    Mono<CashCollection> remit(UUID collectionId, String challengeToken, String code);

    // ── compensation fund (platform) ────────────────────────────────────────
    Mono<Fund> compensationFund();

    Mono<Compensation> compensate(CompensationRequest request);

    // ── wallets (platform) ──────────────────────────────────────────────────
    /** The Kernel wallet of an owner (a user: the Core actor id is the Kernel user id), empty when none. */
    Mono<KernelWalletDto> walletOfOwner(UUID ownerId);
}
