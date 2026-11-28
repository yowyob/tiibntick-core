package com.yowyob.tiibntick.core.billing.wallet.application.port.in;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowMirror;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowShares;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent;
import com.yowyob.tiibntick.core.billing.wallet.domain.policy.EscrowOutcomePolicy;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Escrow of a delivery price in the Kernel (spec §4.1, §4.2, lot L5). The hold is confirmed by the payer with the code
 * the Kernel sends him; every later movement is a platform operation driven by the delivery and dispute lifecycles.
 */
public interface IEscrowUseCase {

    /**
     * @param deliveryId    id of the delivery to create (chosen before it exists: the escrow subject)
     * @param platformRate  platform levy rate (e.g. 0.05)
     * @param merchants     merchant parts (QO-19), empty when none
     * @param ttl           validity of the intent (bounded by the Kernel challenge expiry for ESCROW)
     */
    record IntentRequest(UUID tenantId, UUID deliveryId, UUID announcementId, UUID responseId, PaymentIntent.Mode mode,
                         BigDecimal price, String currency, BigDecimal platformRate, List<EscrowShares.Part> merchants,
                         UUID payerActorId, UUID courierActorId, Duration ttl) {
    }

    /** The intent and, for ESCROW, the Kernel challenge to show the payer. */
    record IntentChallenge(PaymentIntent intent, String challengeToken, Long expiresInSeconds, String codePreview) {
    }

    /**
     * ESCROW: the Kernel challenge is requested with the payer's bearer; CASH: no challenge. A delivery has one
     * intent: an unused one is replaced (new challenge after expiry), a used one is refused (409).
     */
    Mono<IntentChallenge> createIntent(IntentRequest request);

    /**
     * Holds the escrow with the payer's code; idempotent per delivery (an escrow already held is returned, after a
     * lookup in the Kernel when the mirror is missing). The intent is consumed.
     *
     * @throws com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException 404 unknown intent,
     *         403 not the payer, 409 expired/consumed/not ESCROW, 409/503 Kernel refusal/unavailability
     */
    Mono<EscrowMirror> hold(UUID intentId, UUID callerActorId, String challengeToken, String code);

    /**
     * Applies a lifecycle action to the escrow of a delivery (platform identity). No escrow (cash, agency delivery
     * without announcement QO-28): empty. Already in the target state, or already settled: returned unchanged.
     */
    Mono<EscrowMirror> apply(UUID deliveryId, EscrowOutcomePolicy.Action action, String reason);

    /** A payment intent, empty when unknown. */
    Mono<PaymentIntent> intent(UUID intentId);

    /** The payment intent of a delivery (one per delivery), empty when none. */
    Mono<PaymentIntent> intentOfDelivery(UUID deliveryId);

    /** The escrow mirror of a delivery price, empty when the delivery has none. */
    Mono<EscrowMirror> find(UUID deliveryId);

    /** Reason prefix of a freeze ordered by a dispute: an incident closing does not lift it. */
    String DISPUTE_FREEZE = "DISPUTE:";

    // ── return guarantee of a delivery to a relay hub (D-147, D-151, D-152) ──

    /**
     * Second escrow of an ESCROW intent whose delivery goes to a relay hub: the same amount as the delivery, one
     * PLATFORM share to the compensation fund wallet, subject {@code RETURN_GUARANTEE}. The Kernel sends the payer a
     * second code.
     */
    Mono<com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Challenge>
    guaranteeChallenge(java.util.UUID intentId, java.util.UUID callerActorId);

    /** Holds the guarantee with the payer's second code; idempotent per delivery. */
    Mono<EscrowMirror> holdGuarantee(java.util.UUID intentId, java.util.UUID callerActorId, String challengeToken,
                                     String code);

    /** Platform operation on the guarantee: REFUND (parcel collected, delivery ended), RELEASE (paid to the fund). */
    Mono<EscrowMirror> applyGuarantee(java.util.UUID deliveryId, EscrowOutcomePolicy.Action action, String reason);

    Mono<EscrowMirror> findGuarantee(java.util.UUID deliveryId);

    /**
     * Confirms a FUND intent (a return paid by the compensation fund, QO-22): checks the caller is its payer (the
     * sender electing the return) and consumes it. Nothing is taken from anybody's wallet.
     */
    Mono<com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent> confirmFunded(java.util.UUID intentId,
                                                                                          java.util.UUID callerActorId);

    /** The escrow of a delivery, refunded after a failed selection (the local transaction did not commit). */
    default Mono<EscrowMirror> refundAbortedSelection(UUID deliveryId) {
        return apply(deliveryId, EscrowOutcomePolicy.Action.REFUND, "SELECTION_ABORTED");
    }
}
