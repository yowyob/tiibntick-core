package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ICashCollectionUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ICompensationUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IDebtUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ICourierLegRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.CourierLeg;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent;
import com.yowyob.tiibntick.core.billing.wallet.domain.policy.ReplacementProrataPolicy;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletDebt;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IEscrowUseCase;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowMirror;
import com.yowyob.tiibntick.core.billing.wallet.domain.policy.EscrowOutcomePolicy;
import com.yowyob.tiibntick.core.billing.wallet.domain.policy.EscrowOutcomePolicy.Action;
import com.yowyob.tiibntick.core.billing.wallet.domain.policy.EscrowOutcomePolicy.DeliveryEnd;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Effects of the delivery and dispute lifecycles on the money (spec §4.2, lot L5): {@link EscrowOutcomePolicy} picks the
 * escrow action, the cash collection follows (collected at delivery, cancelled otherwise). A delivery without escrow
 * nor collection (agency delivery without announcement, QO-28) is left alone. Every step is idempotent (replays).
 *
 * <p>Debts (QO-22 / D-115, QO-26 / D-120): a return paid by the fund (FUND intent) pays the return's courier at its
 * delivery, which turns the PENDING debt of the courier at fault into an OPEN one (cancelled when the return does not
 * happen); a dispute compensation paid by the fund while a courier, relay or agency is responsible becomes that
 * party's debt.
 *
 * <p>Replacement couriers (QO-11 / D-153): the delivery publishes each change of courier with the legs' distances
 * ({@link #courierReplaced}); when the escrow is released, the Kernel pays the whole courier part to the first courier
 * (its parts cannot change), the fund pays every other courier at the prorata of the distance
 * ({@link ReplacementProrataPolicy}) and the first courier owes the fund the same amount (debt
 * {@code COURIER_REPLACEMENT}). An undecided split or a refused payment is left to the administrator; a Kernel outage
 * is retried with the record.
 */
@Slf4j
@Service
public class PaymentLifecycleService {

    /** Statuses before the parcel is collected: a cancellation there is "before pickup" (D3). */
    static final Set<String> BEFORE_PICKUP = Set.of("CREATED", "ASSIGNED");

    private final IEscrowUseCase escrows;
    private final ICashCollectionUseCase cash;
    private final ICompensationUseCase compensations;
    private final IDebtUseCase debts;
    private final ICourierLegRepository legs;

    /** Without the fund nor the debts (tests of the escrow alone). */
    public PaymentLifecycleService(IEscrowUseCase escrows, ICashCollectionUseCase cash) {
        this(escrows, cash, null, null, null);
    }

    public PaymentLifecycleService(IEscrowUseCase escrows, ICashCollectionUseCase cash,
                                   ICompensationUseCase compensations, IDebtUseCase debts) {
        this(escrows, cash, compensations, debts, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PaymentLifecycleService(IEscrowUseCase escrows, ICashCollectionUseCase cash,
                                   ICompensationUseCase compensations, IDebtUseCase debts,
                                   ICourierLegRepository legs) {
        this.escrows = escrows;
        this.cash = cash;
        this.compensations = compensations;
        this.debts = debts;
        this.legs = legs;
    }

    /** D11. */
    public Mono<Void> delivered(UUID deliveryId, BigDecimal agreedAmount) {
        return escrow(deliveryId, EscrowOutcomePolicy.onDeliveryEnd(DeliveryEnd.DELIVERED, null), "DELIVERED")
                .then(cash.collected(deliveryId, agreedAmount).then())
                .then(fundedReturnDelivered(deliveryId))
                .then(replacementCouriersPaid(deliveryId));
    }

    /** D7: released when the hub is the destination (QO-10), nothing otherwise. */
    public Mono<Void> depositedAtRelay(UUID deliveryId, boolean atDestination) {
        return atDestination
                ? escrow(deliveryId, EscrowOutcomePolicy.onDeliveryEnd(DeliveryEnd.DEPOSITED_AT_DESTINATION_RELAY,
                        null), "DEPOSITED_AT_DESTINATION_RELAY")
                .then(replacementCouriersPaid(deliveryId))
                : Mono.empty();
    }

    /** QO-11 / D-153: a change of courier published by the delivery, kept until the escrow is released. */
    public Mono<Void> courierReplaced(CourierLeg leg) {
        if (legs == null) {
            return Mono.empty();
        }
        return legs.insertIfAbsent(leg)
                .doOnSuccess(v -> log.info("Courier of delivery {} replaced: {} → {} (leg {} km, remaining {} km)",
                        leg.deliveryId(), leg.replacedCourierId(), leg.replacementCourierId(), leg.replacedLegKm(),
                        leg.remainingKm()));
    }

    /**
     * QO-11 / D-153: once the escrow is RELEASED (the first courier got the whole courier part), the fund pays every
     * replacement courier his prorata and the first courier owes it. Idempotent (fund mirror and debt per reference).
     */
    Mono<Void> replacementCouriersPaid(UUID deliveryId) {
        if (legs == null || compensations == null) {
            return Mono.empty();
        }
        return escrows.find(deliveryId)
                .filter(e -> e.status() == EscrowMirror.Status.RELEASED)
                .flatMap(e -> escrows.intentOfDelivery(deliveryId))
                .filter(i -> i.mode() == PaymentIntent.Mode.ESCROW && i.shares() != null
                        && i.shares().courier() != null)
                .flatMap(i -> legs.byDelivery(deliveryId).collectList()
                        .filter(l -> !l.isEmpty())
                        .flatMap(l -> {
                            ReplacementProrataPolicy.Outcome outcome = ReplacementProrataPolicy.split(
                                    i.shares().courier(), i.courierActorId(), l);
                            if (!outcome.decided()) {
                                log.warn("REPLACEMENT_PRORATA_UNDECIDED delivery={} reason={} courierPart={} {} — the"
                                                + " administrator splits it (admin spec §5.2)", deliveryId,
                                        outcome.undecidedReason(), i.shares().courier(), i.currency());
                                return Mono.empty();
                            }
                            return reactor.core.publisher.Flux.fromIterable(outcome.payments())
                                    .concatMap(p -> replacementPaid(i, p))
                                    .then();
                        }))
                .then();
    }

    private Mono<Void> replacementPaid(PaymentIntent i, ReplacementProrataPolicy.Payment p) {
        String reference = WalletDebt.replacementReference(i.deliveryId(), p.index());
        return compensations.payReplacementCourier(i.tenantId(), i.deliveryId(), p.index(), p.courierActorId(),
                        p.amount(), i.currency())
                .flatMap(paid -> debts == null ? Mono.empty()
                        : debts.record(new IDebtUseCase.NewDebt(i.tenantId(), WalletDebt.DebtorType.COURIER,
                                i.courierActorId(), WalletDebt.Reason.COURIER_REPLACEMENT, reference, i.deliveryId(),
                                paid.amount(), paid.currency())))
                .doOnNext(d -> log.info("Replacement courier {} of delivery {} paid {} {} by the fund; debt {} of {}",
                        p.courierActorId(), i.deliveryId(), p.amount(), i.currency(), d.id(), i.courierActorId()))
                .then()
                .onErrorResume(e -> !(e instanceof WalletSettlementException w) || w.getHttpStatus() != 503, e -> {
                    log.error("REPLACEMENT_FUND_PAYMENT_FAILED delivery={} courier={} amount={} {} reference={}: {} —"
                                    + " the administrator pays it (admin spec §5.2)", i.deliveryId(),
                            p.courierActorId(), p.amount(), i.currency(), reference, e.toString());
                    return Mono.empty();
                });
    }

    /**
     * D3. Before pickup: refunded. After: by fault (QO-24) — the sender cancelling is his fault, the courier
     * cancelling his; anybody else (support, system) is not ruled and left to support.
     */
    public Mono<Void> cancelled(UUID deliveryId, String previousStatus, String cancelledBy) {
        boolean beforePickup = previousStatus == null || BEFORE_PICKUP.contains(previousStatus);
        String party = switch (cancelledBy == null ? "" : cancelledBy) {
            case "SENDER" -> "SENDER";
            case "COURIER" -> "COURIER";
            default -> null;
        };
        Action action = EscrowOutcomePolicy.onDeliveryEnd(beforePickup ? DeliveryEnd.CANCELLED_BEFORE_PICKUP
                : DeliveryEnd.CANCELLED_AFTER_PICKUP, party);
        return escrow(deliveryId, action, "CANCELLED" + (party != null ? " by " + party : ""))
                .then(cash.cancel(deliveryId).then())
                .then(fundedReturnAbandoned(deliveryId, "RETURN_CANCELLED"));
    }

    /** D10, D12, D20, D21. */
    public Mono<Void> failed(UUID deliveryId, String reason, String responsibleParty) {
        boolean lost = "PARCEL_LOST".equals(reason) || "PARCEL_DAMAGED".equals(reason);
        Action action = EscrowOutcomePolicy.onDeliveryEnd(lost ? DeliveryEnd.PARCEL_LOST_OR_DAMAGED
                : DeliveryEnd.FAILED, responsibleParty);
        return escrow(deliveryId, action, "FAILED " + reason + (responsibleParty != null ? " (" + responsibleParty
                + ")" : ""))
                .then(cash.cancel(deliveryId).then())
                .then(fundedReturnAbandoned(deliveryId, "RETURN_FAILED"));
    }

    /** LT1: frozen while the dispute runs (nothing on a settled escrow). */
    public Mono<Void> disputeOpened(UUID deliveryId, UUID disputeId) {
        return escrow(deliveryId, Action.FREEZE, IEscrowUseCase.DISPUTE_FREEZE + disputeId);
    }

    /**
     * LT8–LT12. An escrow still held or frozen means the outbound leg did not complete (a completed leg released
     * it), so a return/redelivery order refunds it (QO-23). LT9 compensated: refunded, collection cancelled.
     */
    public Mono<Void> disputeClosed(UUID deliveryId, UUID disputeId, String resolutionType, String responsibleParty) {
        Action action = EscrowOutcomePolicy.onDisputeClosed(resolutionType, "COURIER".equals(responsibleParty),
                false);
        Mono<Void> money = escrow(deliveryId, action, "DISPUTE_CLOSED:" + disputeId
                + (resolutionType != null ? " " + resolutionType : ""));
        return action == Action.REFUND ? money.then(cash.cancel(deliveryId).then()) : money;
    }

    /**
     * LT9 with a third party at fault (QO-26): the compensation paid by the fund becomes the debt of the responsible
     * courier (the delivery's courier), relay or agency (named later by the administrator).
     */
    public Mono<Void> disputeCompensated(UUID tenantId, UUID deliveryId, UUID disputeId, String responsibleParty,
                                         BigDecimal amount, String currency) {
        WalletDebt.DebtorType type = WalletDebt.DebtorType.ofResponsibleParty(responsibleParty);
        if (debts == null || type == null || amount == null || amount.signum() <= 0 || tenantId == null) {
            return Mono.empty();
        }
        Mono<java.util.Optional<UUID>> debtor = type == WalletDebt.DebtorType.COURIER
                ? escrows.intentOfDelivery(deliveryId).map(i -> java.util.Optional.ofNullable(i.courierActorId()))
                        .defaultIfEmpty(java.util.Optional.empty())
                : Mono.just(java.util.Optional.empty());
        return debtor.flatMap(d -> debts.record(new IDebtUseCase.NewDebt(tenantId, type, d.orElse(null),
                        WalletDebt.Reason.DISPUTE_COMPENSATION, WalletDebt.disputeReference(disputeId.toString()),
                        deliveryId, amount, currency)))
                .doOnNext(d -> log.info("Debt {} recorded: {} {} owes {} {} (dispute {})", d.id(), d.debtorType(),
                        d.debtorId(), d.amount(), d.currency(), disputeId))
                .then();
    }

    /** A return paid by the fund was delivered: the fund pays its courier, the debt of the courier at fault opens. */
    private Mono<Void> fundedReturnDelivered(UUID deliveryId) {
        if (compensations == null) {
            return Mono.empty();
        }
        return escrows.intentOfDelivery(deliveryId)
                .filter(i -> i.mode() == PaymentIntent.Mode.FUND)
                .flatMap(i -> compensations.payReturnCourier(i.tenantId(), deliveryId, i.courierActorId(),
                                i.shares().courier(), i.currency())
                        .flatMap(paid -> debts == null ? Mono.empty()
                                : debts.activate(WalletDebt.returnReference(deliveryId), paid.amount()))
                        .onErrorResume(e -> {
                            log.error("RETURN_FUND_PAYMENT_FAILED delivery={} courier={} amount={} {}: {} — the"
                                            + " administrator pays it (admin spec §5.2)", deliveryId,
                                    i.courierActorId(), i.shares().courier(), i.currency(), e.toString());
                            return Mono.empty();
                        }))
                .then();
    }

    private Mono<Void> fundedReturnAbandoned(UUID deliveryId, String note) {
        if (debts == null) {
            return Mono.empty();
        }
        return escrows.intentOfDelivery(deliveryId)
                .filter(i -> i.mode() == PaymentIntent.Mode.FUND)
                .flatMap(i -> debts.cancelPending(WalletDebt.returnReference(deliveryId), note))
                .then();
    }

    private Mono<Void> escrow(UUID deliveryId, Action action, String reason) {
        if (action == Action.NONE) {
            return escrows.find(deliveryId)
                    .filter(e -> !e.status().isFinal())
                    .doOnNext(e -> log.warn("ESCROW_OUTCOME_UNDECIDED delivery={} escrow={} reason={} — the party at "
                            + "fault is not ruled (QO-24): left HELD for support", deliveryId, e.kernelEscrowId(),
                            reason))
                    .then();
        }
        return escrows.apply(deliveryId, action, reason)
                .doOnNext((EscrowMirror e) -> log.info("Escrow of delivery {}: {} → {} ({})", deliveryId, action,
                        e.status(), reason))
                .then();
    }
}
