package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IEscrowUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ISettlementUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.RecordSettlementCommand;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementMethod;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IKernelSettlementPort;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IPaymentMirrorRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Challenge;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Escrow;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.EscrowHoldRequest;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Share;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletDto;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowMirror;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowShares;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent;
import com.yowyob.tiibntick.core.billing.wallet.domain.policy.EscrowOutcomePolicy;
import com.yowyob.tiibntick.core.billing.wallet.domain.policy.EscrowSharesPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Escrow of a delivery price (spec §4.1, §4.2, lot L5). Shares from {@link EscrowSharesPolicy}; the platform wallet is
 * {@code tnt.payments.platform-wallet-id}. The Kernel holds the money; {@code billing.wallet_escrows} mirrors it.
 */
@Slf4j
@Service
public class EscrowService implements IEscrowUseCase {

    static final String SUBJECT_DELIVERY = "DELIVERY";
    static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final IKernelSettlementPort kernel;
    private final IPaymentMirrorRepository mirrors;
    private final ISettlementUseCase settlements;
    private final UUID platformWalletId;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public EscrowService(IKernelSettlementPort kernel, IPaymentMirrorRepository mirrors,
                         ISettlementUseCase settlements,
                         @Value("${tnt.payments.platform-wallet-id:}") String platformWalletId) {
        this(kernel, mirrors, settlements, platformWalletId == null || platformWalletId.isBlank() ? null
                : UUID.fromString(platformWalletId.trim()), Clock.systemUTC());
    }

    public EscrowService(IKernelSettlementPort kernel, IPaymentMirrorRepository mirrors,
                         ISettlementUseCase settlements, UUID platformWalletId, Clock clock) {
        this.kernel = kernel;
        this.mirrors = mirrors;
        this.settlements = settlements;
        this.platformWalletId = platformWalletId;
        this.clock = clock;
    }

    @Override
    public Mono<IntentChallenge> createIntent(IntentRequest r) {
        if (platformWalletId == null) {
            return Mono.error(new WalletSettlementException(WalletSettlementException.PLATFORM_WALLET_MISSING, 503,
                    "tnt.payments.platform-wallet-id is not set: no escrow nor cash collection can be split"));
        }
        EscrowShares shares;
        try {
            shares = EscrowSharesPolicy.split(r.price(), r.platformRate(), r.merchants());
        } catch (IllegalArgumentException e) {
            return Mono.error(new WalletSettlementException(WalletSettlementException.INVALID_SPLIT, 409,
                    e.getMessage(), e));
        }
        return mirrors.findIntentByDelivery(r.deliveryId())
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMap(existing -> existing.isPresent() && existing.get().consumedAt() != null
                        ? Mono.<IntentChallenge>error(conflict(WalletSettlementException.PAYMENT_INTENT_INVALID,
                                "The payment of delivery " + r.deliveryId() + " was already made"))
                        : newIntent(r, shares, existing.map(PaymentIntent::id).orElse(null)));
    }

    private Mono<IntentChallenge> newIntent(IntentRequest r, EscrowShares shares, UUID reusedId) {
        boolean isNew = reusedId == null;
        // FUND: nothing is taken from the sender — only the courier's wallet matters (the fund pays it)
        Mono<java.util.Optional<UUID>> payerWallet = r.mode() == PaymentIntent.Mode.FUND
                ? Mono.just(java.util.Optional.empty())
                : wallet(r.payerActorId(), "payer").map(w -> java.util.Optional.of(w.id()));
        return Mono.zip(payerWallet, wallet(r.courierActorId(), "courier"))
                .flatMap(w -> {
                    Instant now = clock.instant();
                    Duration ttl = r.ttl() != null ? r.ttl() : DEFAULT_TTL;
                    PaymentIntent intent = new PaymentIntent(isNew ? UUID.randomUUID() : reusedId, r.tenantId(),
                            r.deliveryId(),
                            r.announcementId(), r.responseId(), r.mode(), shares.price(),
                            r.currency() == null ? "XAF" : r.currency(), shares, r.payerActorId(), w.getT1().orElse(null),
                            r.courierActorId(), w.getT2().id(), platformWalletId, now.plus(ttl), null, now);
                    if (r.mode() != PaymentIntent.Mode.ESCROW) {
                        return mirrors.saveIntent(intent, isNew).map(i -> new IntentChallenge(i, null, null, null));
                    }
                    return kernel.escrowChallenge(holdRequest(intent))
                            .onErrorMap(EscrowService::kernelError)
                            .switchIfEmpty(Mono.error(() -> unavailable("The Kernel returned no escrow challenge")))
                            .flatMap(ch -> {
                                PaymentIntent bounded = ch.expiresInSeconds() == null ? intent
                                        : boundedBy(intent, now.plusSeconds(ch.expiresInSeconds()));
                                return mirrors.saveIntent(bounded, isNew).map(i -> new IntentChallenge(i,
                                        ch.challengeToken(), ch.expiresInSeconds(), ch.codePreview()));
                            });
                });
    }

    @Override
    public Mono<PaymentIntent> confirmFunded(UUID intentId, UUID callerActorId) {
        return mirrors.findIntent(intentId)
                .switchIfEmpty(Mono.error(() -> new WalletSettlementException(
                        WalletSettlementException.PAYMENT_INTENT_NOT_FOUND, 404, "Unknown payment intent " + intentId)))
                .flatMap(intent -> {
                    if (callerActorId == null || !callerActorId.equals(intent.payerActorId())) {
                        return Mono.error(new WalletSettlementException(
                                WalletSettlementException.PAYMENT_INTENT_FORBIDDEN, 403,
                                "Only the payer confirms this payment"));
                    }
                    if (intent.mode() != PaymentIntent.Mode.FUND) {
                        return Mono.error(conflict(WalletSettlementException.PAYMENT_INTENT_INVALID,
                                "The intent " + intentId + " is not paid by the fund"));
                    }
                    if (intent.consumedAt() != null) {
                        return Mono.just(intent);
                    }
                    if (intent.expired(clock.instant())) {
                        return Mono.error(conflict(WalletSettlementException.PAYMENT_INTENT_EXPIRED,
                                "The payment intent " + intentId + " expired"));
                    }
                    return mirrors.saveIntent(intent.consume(clock.instant()), false);
                });
    }

    @Override
    public Mono<EscrowMirror> hold(UUID intentId, UUID callerActorId, String challengeToken, String code) {
        return mirrors.findIntent(intentId)
                .switchIfEmpty(Mono.error(() -> new WalletSettlementException(
                        WalletSettlementException.PAYMENT_INTENT_NOT_FOUND, 404, "Unknown payment intent " + intentId)))
                .flatMap(intent -> {
                    if (callerActorId == null || !callerActorId.equals(intent.payerActorId())) {
                        return Mono.error(new WalletSettlementException(
                                WalletSettlementException.PAYMENT_INTENT_FORBIDDEN, 403,
                                "Only the payer confirms this payment"));
                    }
                    if (intent.mode() != PaymentIntent.Mode.ESCROW) {
                        return Mono.error(conflict(WalletSettlementException.PAYMENT_INTENT_INVALID,
                                "The intent " + intentId + " is a cash payment"));
                    }
                    return mirrors.findEscrow(intent.deliveryId(), EscrowMirror.DELIVERY)
                            .switchIfEmpty(Mono.defer(() -> holdNow(intent, challengeToken, code)));
                });
    }

    private Mono<EscrowMirror> holdNow(PaymentIntent intent, String token, String code) {
        if (intent.consumedAt() != null) {
            return Mono.error(conflict(WalletSettlementException.PAYMENT_INTENT_INVALID,
                    "The payment intent " + intent.id() + " was already used"));
        }
        if (intent.expired(clock.instant())) {
            return Mono.error(conflict(WalletSettlementException.PAYMENT_INTENT_EXPIRED,
                    "The payment intent " + intent.id() + " expired"));
        }
        // idempotent resumption: an escrow the Kernel already holds for this delivery (a confirmation retried after
        // a timeout) is recorded instead of holding twice
        return kernel.findEscrow(SUBJECT_DELIVERY, intent.deliveryId())
                .onErrorMap(EscrowService::kernelError)
                .switchIfEmpty(Mono.defer(() -> kernel.holdEscrow(holdRequest(intent).withCode(token, code))
                        .onErrorMap(EscrowService::kernelError)
                        .switchIfEmpty(Mono.error(() -> unavailable("The Kernel returned no escrow")))))
                .flatMap(escrow -> record(intent, escrow, EscrowMirror.DELIVERY))
                .flatMap(mirror -> mirrors.saveIntent(intent.consume(clock.instant()), false).thenReturn(mirror));
    }

    private Mono<EscrowMirror> record(PaymentIntent intent, Escrow escrow, String purpose) {
        Instant now = clock.instant();
        EscrowMirror.Status status = EscrowMirror.Status.of(escrow.status());
        EscrowMirror mirror = new EscrowMirror(UUID.randomUUID(), intent.tenantId(), intent.deliveryId(),
                purpose, escrow.id(), status != null ? status : EscrowMirror.Status.HELD,
                escrow.amount() != null ? escrow.amount() : intent.amount(), intent.currency(),
                intent.payerWalletId(), null, now, now);
        return mirrors.saveEscrow(mirror, true);
    }

    @Override
    public Mono<EscrowMirror> apply(UUID deliveryId, EscrowOutcomePolicy.Action action, String reason) {
        return applyTo(deliveryId, EscrowMirror.DELIVERY, action, reason);
    }

    // ── return guarantee (D-147, D-151, D-152) ──────────────────────────────

    public static final String SUBJECT_RETURN_GUARANTEE = "RETURN_GUARANTEE";

    @Override
    public Mono<Challenge> guaranteeChallenge(UUID intentId, UUID callerActorId) {
        return payersEscrowIntent(intentId, callerActorId)
                .flatMap(intent -> guaranteeRequest(intent)
                        .flatMap(r -> kernel.escrowChallenge(r).onErrorMap(EscrowService::kernelError))
                        .switchIfEmpty(Mono.error(() -> unavailable("The Kernel returned no guarantee challenge"))));
    }

    @Override
    public Mono<EscrowMirror> holdGuarantee(UUID intentId, UUID callerActorId, String token, String code) {
        return payersEscrowIntent(intentId, callerActorId).flatMap(intent -> mirrors
                .findEscrow(intent.deliveryId(), EscrowMirror.RETURN_GUARANTEE)
                .switchIfEmpty(Mono.defer(() -> {
                    if (intent.consumedAt() == null && intent.expired(clock.instant())) {
                        return Mono.error(conflict(WalletSettlementException.PAYMENT_INTENT_EXPIRED,
                                "The payment intent " + intent.id() + " expired"));
                    }
                    return kernel.findEscrow(SUBJECT_RETURN_GUARANTEE, intent.deliveryId())
                            .onErrorMap(EscrowService::kernelError)
                            .switchIfEmpty(Mono.defer(() -> guaranteeRequest(intent)
                                    .flatMap(r -> kernel.holdEscrow(r.withCode(token, code)))
                                    .onErrorMap(EscrowService::kernelError)
                                    .switchIfEmpty(Mono.error(() -> unavailable("The Kernel returned no guarantee")))))
                            .flatMap(escrow -> record(intent, escrow, EscrowMirror.RETURN_GUARANTEE));
                })));
    }

    @Override
    public Mono<EscrowMirror> applyGuarantee(UUID deliveryId, EscrowOutcomePolicy.Action action, String reason) {
        return applyTo(deliveryId, EscrowMirror.RETURN_GUARANTEE, action, reason);
    }

    @Override
    public Mono<EscrowMirror> findGuarantee(UUID deliveryId) {
        return mirrors.findEscrow(deliveryId, EscrowMirror.RETURN_GUARANTEE);
    }

    private Mono<PaymentIntent> payersEscrowIntent(UUID intentId, UUID callerActorId) {
        return mirrors.findIntent(intentId)
                .switchIfEmpty(Mono.error(() -> new WalletSettlementException(
                        WalletSettlementException.PAYMENT_INTENT_NOT_FOUND, 404, "Unknown payment intent " + intentId)))
                .flatMap(intent -> {
                    if (callerActorId == null || !callerActorId.equals(intent.payerActorId())) {
                        return Mono.error(new WalletSettlementException(
                                WalletSettlementException.PAYMENT_INTENT_FORBIDDEN, 403,
                                "Only the payer confirms this payment"));
                    }
                    if (intent.mode() != PaymentIntent.Mode.ESCROW) {
                        return Mono.error(conflict(WalletSettlementException.PAYMENT_INTENT_INVALID,
                                "A return guarantee goes with an escrow payment only"));
                    }
                    return Mono.just(intent);
                });
    }

    /** Same amount as the delivery (D-151), one PLATFORM share: the compensation fund wallet. */
    private Mono<EscrowHoldRequest> guaranteeRequest(PaymentIntent intent) {
        return kernel.compensationFund()
                .onErrorMap(EscrowService::kernelError)
                .switchIfEmpty(Mono.error(() -> unavailable("The Kernel compensation fund did not answer")))
                .map(fund -> new EscrowHoldRequest(SUBJECT_RETURN_GUARANTEE, intent.deliveryId(),
                        intent.payerWalletId(), intent.currency(),
                        List.of(new Share(Share.PLATFORM, fund.walletId(), intent.amount())), null, null,
                        intent.id()));
    }

    private Mono<EscrowMirror> applyTo(UUID deliveryId, String purpose, EscrowOutcomePolicy.Action action,
                                       String reason) {
        if (action == EscrowOutcomePolicy.Action.NONE) {
            return mirrors.findEscrow(deliveryId, purpose);
        }
        EscrowMirror.Status target = switch (action) {
            case RELEASE -> EscrowMirror.Status.RELEASED;
            case REFUND -> EscrowMirror.Status.REFUNDED;
            case FREEZE -> EscrowMirror.Status.FROZEN;
            case UNFREEZE, NONE -> EscrowMirror.Status.HELD;
        };
        return mirrors.findEscrow(deliveryId, purpose).flatMap(mirror -> {
            if (mirror.status() == target) {
                // a dispute taking over an incident freeze records itself as the holder (no Kernel call)
                return action == EscrowOutcomePolicy.Action.FREEZE && reason != null
                        && reason.startsWith(DISPUTE_FREEZE) && !reason.equals(mirror.reason())
                        ? mirrors.saveEscrow(mirror.with(target, reason, clock.instant()), false)
                        : Mono.just(mirror);
            }
            if (mirror.status().isFinal()) {
                log.warn("ESCROW_ALREADY_SETTLED delivery={} escrow={} status={} requested={} reason={} — left as is",
                        deliveryId, mirror.kernelEscrowId(), mirror.status(), action, reason);
                return Mono.just(mirror);
            }
            if (action == EscrowOutcomePolicy.Action.UNFREEZE && mirror.status() != EscrowMirror.Status.FROZEN) {
                return Mono.just(mirror);
            }
            UUID id = mirror.kernelEscrowId();
            Mono<Escrow> call = switch (action) {
                case RELEASE -> kernel.releaseEscrow(id);
                case REFUND -> kernel.refundEscrow(id, reason);
                case FREEZE -> kernel.freezeEscrow(id, reason);
                default -> kernel.unfreezeEscrow(id, reason);
            };
            return call.onErrorResume(WebClientResponseException.class, e -> e.getStatusCode().is4xxClientError()
                            // refused: maybe already done (a retried operation) — read the Kernel state
                            ? kernel.getEscrow(id).filter(k -> EscrowMirror.Status.of(k.status()) == target)
                                    .switchIfEmpty(Mono.error(e))
                            : Mono.error(e))
                    .onErrorMap(EscrowService::kernelError)
                    .map(k -> EscrowMirror.Status.of(k.status()))
                    .defaultIfEmpty(target)
                    .flatMap(s -> mirrors.saveEscrow(mirror.with(s != null ? s : target, reason, clock.instant()),
                            false))
                    .flatMap(saved -> saved.status() == EscrowMirror.Status.RELEASED
                            && EscrowMirror.DELIVERY.equals(purpose)
                            ? recordReleased(saved).thenReturn(saved) : Mono.just(saved));
        });
    }

    /**
     * E4: the delivery is settled (spec §4.5: {@code wallet_settlements} is fed by the release) — PAID by ESCROW, the
     * platform levy as commission SETTLED at source; this publishes {@code payment.confirmed} and
     * {@code commission.calculated}. The Kernel already moved the money: a failure is logged, never propagated.
     */
    private Mono<Void> recordReleased(EscrowMirror escrow) {
        return mirrors.findIntentByDelivery(escrow.deliveryId())
                .flatMap(intent -> settlements.record(new RecordSettlementCommand(intent.tenantId(),
                        SUBJECT_DELIVERY, intent.deliveryId(), intent.payerActorId(), intent.courierActorId(),
                        escrow.amount(), escrow.currency(), SettlementMethod.ESCROW, SettlementStatus.PAID,
                        escrow.kernelEscrowId().toString(), rate(intent))))
                .onErrorResume(e -> {
                    log.error("PAYMENT_UNRECORDED delivery={} escrow={} — released by the Kernel but the settlement "
                            + "could not be saved: {}", escrow.deliveryId(), escrow.kernelEscrowId(), e.toString());
                    return Mono.empty();
                })
                .then();
    }

    static java.math.BigDecimal rate(PaymentIntent intent) {
        return intent.amount() == null || intent.amount().signum() == 0 ? java.math.BigDecimal.ZERO
                : intent.shares().platform().divide(intent.amount(), 4, java.math.RoundingMode.HALF_UP);
    }

    @Override
    public Mono<PaymentIntent> intent(UUID intentId) {
        return mirrors.findIntent(intentId);
    }

    @Override
    public Mono<PaymentIntent> intentOfDelivery(UUID deliveryId) {
        return mirrors.findIntentByDelivery(deliveryId);
    }

    @Override
    public Mono<EscrowMirror> find(UUID deliveryId) {
        return mirrors.findEscrow(deliveryId, EscrowMirror.DELIVERY);
    }

    private Mono<KernelWalletDto> wallet(UUID actorId, String who) {
        if (actorId == null) {
            return Mono.error(conflict(WalletSettlementException.WALLET_MISSING, "No " + who + " for this payment"));
        }
        return kernel.walletOfOwner(actorId)
                .onErrorMap(EscrowService::kernelError)
                .switchIfEmpty(Mono.error(() -> conflict(WalletSettlementException.WALLET_MISSING,
                        "The " + who + " " + actorId + " has no Kernel wallet")));
    }

    private static EscrowHoldRequest holdRequest(PaymentIntent i) {
        List<Share> shares = new ArrayList<>();
        if (i.shares().platform().signum() > 0) {
            shares.add(new Share(Share.PLATFORM, i.platformWalletId(), i.shares().platform()));
        }
        i.shares().merchants().forEach(m -> shares.add(new Share(Share.MERCHANT, m.walletId(), m.amount())));
        shares.add(new Share(Share.COURIER, i.courierWalletId(), i.shares().courier()));
        return new EscrowHoldRequest(SUBJECT_DELIVERY, i.deliveryId(), i.payerWalletId(), i.currency(), shares, null,
                null, i.id());
    }

    private static PaymentIntent boundedBy(PaymentIntent i, Instant kernelExpiry) {
        Instant expires = i.expiresAt() == null || kernelExpiry.isBefore(i.expiresAt()) ? kernelExpiry : i.expiresAt();
        return new PaymentIntent(i.id(), i.tenantId(), i.deliveryId(), i.announcementId(), i.responseId(), i.mode(),
                i.amount(), i.currency(), i.shares(), i.payerActorId(), i.payerWalletId(), i.courierActorId(),
                i.courierWalletId(), i.platformWalletId(), expires, i.consumedAt(), i.createdAt());
    }

    private static WalletSettlementException conflict(String code, String message) {
        return new WalletSettlementException(code, 409, message);
    }

    private static WalletSettlementException unavailable(String message) {
        return new WalletSettlementException(WalletSettlementException.KERNEL_UNAVAILABLE, 503, message);
    }

    static Throwable kernelError(Throwable e) {
        if (e instanceof WalletSettlementException) {
            return e;
        }
        if (e instanceof WebClientResponseException w && w.getStatusCode().is4xxClientError()) {
            return new WalletSettlementException(WalletSettlementException.KERNEL_REFUSED, 409,
                    "The Kernel refused the payment operation: " + w.getStatusCode().value() + " "
                            + w.getResponseBodyAsString(), e);
        }
        return new WalletSettlementException(WalletSettlementException.KERNEL_UNAVAILABLE, 503,
                "The Kernel payment service is unavailable: " + e.getMessage(), e);
    }
}
