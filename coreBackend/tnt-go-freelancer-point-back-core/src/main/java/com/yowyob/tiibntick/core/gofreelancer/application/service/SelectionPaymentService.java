package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ICashCollectionUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IDebtUseCase;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletDebt;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponsibleParty;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IEscrowUseCase;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowMirror;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AnnouncementUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.SelectionChallengeResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.SelectionConfirmResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.CommissionRatePort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Paid selection of a response (spec §4.1, lot L5): the price is secured before the election.
 * <ol>
 *   <li>{@link #challenge}: the delivery id is chosen now, the split computed and a payment intent created — for
 *       ESCROW the Kernel sends the client a code;</li>
 *   <li>{@link #confirm}: ESCROW holds the escrow with the client's code, CASH opens the cash collection; then the
 *       response is elected and the delivery created <em>with that id</em>. When the election fails before the
 *       delivery exists, the escrow is refunded (collection cancelled) and the error returned.</li>
 * </ol>
 * The client is the announcement's; the courier is the actor behind the response's delivery person.
 *
 * <p>A RETURN announcement whose return is at the courier's or the platform's expense (the parcel's
 * {@code returnCostPayer}, QO-22 / D-115, spec §4.4) is paid by the compensation fund: mode FUND, nothing taken from
 * the sender, the fund pays the return's courier at delivery; when the courier of the outbound leg is at fault, a
 * PENDING debt is recorded on him at the election (opened when the fund pays). A return whose parent's guarantee was
 * captured (parcel not collected at the relay, D-147 / D-151) is paid by the fund too, the guarantee having funded it.
 *
 * <p>Return guarantee (D-147, D-151, D-152): an ESCROW selection of a delivery to a relay hub also holds a second
 * escrow of the same amount for the compensation fund — the challenge returns a second Kernel code, the confirmation
 * requires it and holds the guarantee before the price; any later failure refunds what was held.
 */
@Slf4j
@Service
public class SelectionPaymentService {

    static final BigDecimal DEFAULT_RATE = DeliveryPaymentApplicationService.COMMISSION_RATE;
    static final Duration INTENT_TTL = Duration.ofMinutes(15);

    private final IDeliveryAnnouncementPort announcements;
    private final IAnnouncementRepository gofpAnnouncements;
    private final AnnouncementUseCase announcementUseCase;
    private final DeliveryQueryUseCase deliveryQuery;
    private final IEscrowUseCase escrows;
    private final ICashCollectionUseCase cash;
    private final TenantContextHolder tenants;
    private final ObjectProvider<CommissionRatePort> commissionRates;
    private final ObjectProvider<IDebtUseCase> debts;

    /** Who pays a return through the fund, and the courier at fault when it is him. */
    record FundedReturn(ResponsibleParty payer, UUID courierAtFault) {
    }

    public SelectionPaymentService(IDeliveryAnnouncementPort announcements, IAnnouncementRepository gofpAnnouncements,
                                   AnnouncementUseCase announcementUseCase, DeliveryQueryUseCase deliveryQuery,
                                   IEscrowUseCase escrows, ICashCollectionUseCase cash, TenantContextHolder tenants,
                                   ObjectProvider<CommissionRatePort> commissionRates,
                                   ObjectProvider<IDebtUseCase> debts) {
        this.debts = debts;
        this.announcements = announcements;
        this.gofpAnnouncements = gofpAnnouncements;
        this.announcementUseCase = announcementUseCase;
        this.deliveryQuery = deliveryQuery;
        this.escrows = escrows;
        this.cash = cash;
        this.tenants = tenants;
        this.commissionRates = commissionRates;
    }

    public Mono<SelectionChallengeResponse> challenge(UUID announcementId, UUID responseId, UUID callerActorId,
                                                      String requestedMode) {
        return tenant().flatMap(tenantId -> clientsAnnouncement(tenantId, announcementId, callerActorId)
                .flatMap(a -> {
                    AnnouncementResponseSnapshot response = a.responses().stream()
                            .filter(r -> r.id().equals(responseId)).findFirst().orElse(null);
                    if (response == null) {
                        return Mono.error(conflict("RESPONSE_NOT_FOUND", "Response not found: " + responseId, 404));
                    }
                    if (a.createdDeliveryId() != null) {
                        return Mono.error(conflict("ALREADY_SELECTED", "Announcement " + announcementId
                                + " already has its delivery " + a.createdDeliveryId(), 409));
                    }
                    UUID deliveryId = UUID.randomUUID();
                    return Mono.zip(
                                    announcements.resolveEscrowAmount(tenantId, announcementId, responseId),
                                    deliveryQuery.resolveActorIdForDeliveryPerson(tenantId, response.deliveryPersonId())
                                            .switchIfEmpty(Mono.error(() -> conflict("COURIER_UNKNOWN",
                                                    "No actor for delivery person " + response.deliveryPersonId(),
                                                    409))),
                                    fundedReturn(tenantId, announcementId)
                                            .flatMap(f -> f.isPresent() ? Mono.just(PaymentIntent.Mode.FUND)
                                                    : mode(announcementId, requestedMode)),
                                    rate(tenantId, deliveryId))
                            .flatMap(t -> escrows.createIntent(new IEscrowUseCase.IntentRequest(tenantId,
                                    deliveryId, announcementId, responseId, t.getT3(), t.getT1(),
                                    a.currency() != null ? a.currency() : DeliveryPaymentApplicationService.CURRENCY,
                                    t.getT4(), List.of(), callerActorId, t.getT2(), INTENT_TTL)))
                            .flatMap(ic -> guaranteeRequired(tenantId, announcementId, ic.intent())
                                    .flatMap(required -> !required ? Mono.just(response(ic, null))
                                            : escrows.guaranteeChallenge(ic.intent().id(), callerActorId)
                                            .map(g -> response(ic, g))));
                }));
    }

    private static SelectionChallengeResponse response(IEscrowUseCase.IntentChallenge ic,
            com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Challenge g) {
        PaymentIntent i = ic.intent();
        return new SelectionChallengeResponse(i.id(), i.deliveryId(), i.mode().name(), i.amount(), i.currency(),
                i.shares().platform(), i.shares().courier(), ic.challengeToken(), ic.expiresInSeconds(),
                ic.codePreview(), i.expiresAt(), g != null ? i.amount() : null, g != null ? g.challengeToken() : null,
                g != null ? g.expiresInSeconds() : null, g != null ? g.codePreview() : null);
    }

    /** D-147: an ESCROW payment of an OUTBOUND delivery to a relay hub carries a return guarantee. */
    Mono<Boolean> guaranteeRequired(UUID tenantId, UUID announcementId, PaymentIntent intent) {
        if (intent.mode() != PaymentIntent.Mode.ESCROW) {
            return Mono.just(false);
        }
        return deliveryQuery.findAnnouncementById(tenantId, announcementId)
                .map(a -> a.getKind() != AnnouncementKind.RETURN && a.getDetails() != null
                        && a.getDetails().destinationHubId() != null)
                .defaultIfEmpty(false);
    }

    public Mono<SelectionConfirmResponse> confirm(UUID announcementId, UUID responseId, UUID callerActorId,
                                                  UUID intentId, String challengeToken, String code) {
        return confirm(announcementId, responseId, callerActorId, intentId, challengeToken, code, null, null);
    }

    public Mono<SelectionConfirmResponse> confirm(UUID announcementId, UUID responseId, UUID callerActorId,
                                                  UUID intentId, String challengeToken, String code,
                                                  String guaranteeToken, String guaranteeCode) {
        return escrows.intent(intentId)
                .switchIfEmpty(Mono.error(() -> new WalletSettlementException(
                        WalletSettlementException.PAYMENT_INTENT_NOT_FOUND, 404, "Unknown payment intent " + intentId)))
                .flatMap(intent -> {
                    if (!announcementId.equals(intent.announcementId()) || !responseId.equals(intent.responseId())) {
                        return Mono.error(new WalletSettlementException(
                                WalletSettlementException.PAYMENT_INTENT_INVALID, 409,
                                "The payment intent " + intentId + " is not for this response"));
                    }
                    Mono<java.util.Optional<String>> guarantee = guaranteeRequired(intent.tenantId(), announcementId, intent)
                            .flatMap(required -> {
                                if (!required) {
                                    return Mono.<String>empty();
                                }
                                if (guaranteeToken == null || guaranteeCode == null) {
                                    return Mono.error(conflict("RETURN_GUARANTEE_CODE_REQUIRED", "This delivery goes"
                                            + " to a relay hub: confirm its return guarantee with the second code", 400));
                                }
                                return escrows.holdGuarantee(intentId, callerActorId, guaranteeToken, guaranteeCode)
                                        .map(g -> g.status().name());
                            })
                            .map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty());
                    // deferred: the price is held only after the guarantee (an early refusal holds nothing)
                    Mono<String> priced = Mono.defer(() -> intent.mode() == PaymentIntent.Mode.FUND
                            ? escrows.confirmFunded(intentId, callerActorId).thenReturn("FUNDED")
                            : intent.mode() == PaymentIntent.Mode.CASH
                            ? cash.open(intentId, callerActorId).map(c -> c.status().name())
                            : escrows.hold(intentId, callerActorId, challengeToken, code)
                                    .flatMap(e -> e.status() == EscrowMirror.Status.REFUNDED
                                            ? Mono.<String>error(new WalletSettlementException(
                                                    WalletSettlementException.PAYMENT_INTENT_INVALID, 409,
                                                    "The escrow of this selection was refunded: start again"))
                                            : Mono.just(e.status().name())));
                    return guarantee.flatMap(g -> priced
                            .onErrorResume(e -> refundGuarantee(intent, g.isPresent()).then(Mono.error(e)))
                            .flatMap(status -> announcementUseCase
                                    .assignResponse(announcementId, callerActorId, responseId, intent.deliveryId())
                                    .onErrorResume(e -> undoUnlessElected(intent, e))
                                    .flatMap(dto -> intent.mode() == PaymentIntent.Mode.FUND
                                            ? pendingDebt(intent).thenReturn(dto) : Mono.just(dto))
                                    .map(dto -> new SelectionConfirmResponse(intent.deliveryId(),
                                            intent.mode().name(), status, dto, g.orElse(null)))));
                });
    }

    /**
     * The election failed. When the core already created the delivery of this intent, the payment stays (a retry
     * of the confirmation replays the follow-up); otherwise the escrow is refunded / the collection cancelled.
     */
    private <T> Mono<T> undoUnlessElected(PaymentIntent intent, Throwable error) {
        return tenant()
                .flatMap(tenantId -> announcements.findById(tenantId, intent.announcementId()))
                .map(a -> intent.deliveryId().equals(a.createdDeliveryId()))
                .defaultIfEmpty(false)
                .onErrorReturn(false)
                .flatMap(elected -> {
                    if (elected) {
                        log.warn("Selection of delivery {} elected but its follow-up failed: payment kept ({})",
                                intent.deliveryId(), error.toString());
                        return Mono.<T>error(error);
                    }
                    if (intent.mode() == PaymentIntent.Mode.FUND) {
                        return Mono.<T>error(error);   // nothing was taken; a new challenge starts again
                    }
                    Mono<?> undo = intent.mode() == PaymentIntent.Mode.CASH
                            ? cash.cancel(intent.deliveryId())
                            : escrows.refundAbortedSelection(intent.deliveryId())
                            .then(Mono.defer(() -> refundGuarantee(intent, true)));
                    return undo.onErrorResume(u -> {
                                log.error("SELECTION_PAYMENT_NOT_UNDONE delivery={} mode={} — the election failed "
                                                + "({}) and the payment could not be undone: {}", intent.deliveryId(),
                                        intent.mode(), error.toString(), u.toString());
                                return Mono.empty();
                            })
                            .then(Mono.<T>error(error));
                });
    }

    /** Refunds the return guarantee of an aborted selection (nothing when none was held). */
    private Mono<Void> refundGuarantee(PaymentIntent intent, boolean held) {
        if (!held) {
            return Mono.empty();
        }
        return escrows.applyGuarantee(intent.deliveryId(),
                        com.yowyob.tiibntick.core.billing.wallet.domain.policy.EscrowOutcomePolicy.Action.REFUND,
                        "SELECTION_ABORTED")
                .onErrorResume(u -> {
                    log.error("SELECTION_PAYMENT_NOT_UNDONE delivery={} — the return guarantee could not be refunded:"
                            + " {}", intent.deliveryId(), u.toString());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<AnnouncementSnapshot> clientsAnnouncement(UUID tenantId, UUID announcementId, UUID caller) {
        return announcements.findById(tenantId, announcementId)
                .switchIfEmpty(Mono.error(() -> conflict("ANNOUNCEMENT_NOT_FOUND",
                        "Announcement not found: " + announcementId, 404)))
                .flatMap(a -> caller != null && caller.equals(a.clientId()) ? Mono.just(a)
                        : Mono.error(conflict("NOT_THE_CLIENT", "Only the client of the announcement selects", 403)));
    }

    /**
     * A RETURN announcement (origin {@code RETURN_OF_DELIVERY}) whose parcel's return is at the courier's or the
     * platform's expense; empty otherwise (the sender pays, D-147 / D-151).
     */
    Mono<java.util.Optional<FundedReturn>> fundedReturn(UUID tenantId, UUID announcementId) {
        java.util.Optional<FundedReturn> none = java.util.Optional.empty();
        return deliveryQuery.findAnnouncementById(tenantId, announcementId)
                .filter(a -> a.getKind() == AnnouncementKind.RETURN && a.getDetails() != null
                        && a.getDetails().originRefId() != null)
                .flatMap(a -> deliveryQuery.findDeliveryById(tenantId, a.getDetails().originRefId()))
                .flatMap(parent -> {
                    ResponsibleParty payer = parent.getParcel() != null ? parent.getParcel().getReturnCostPayer()
                            : null;
                    if (parent.getReturnGuaranteeStatus()
                            == com.yowyob.tiibntick.core.delivery.domain.model.enums.ReturnGuaranteeStatus.CAPTURED) {
                        // D-151: the sender's guarantee, paid to the fund at the end of the storage, pays this return
                        return Mono.just(java.util.Optional.of(new FundedReturn(ResponsibleParty.SENDER, null)));
                    }
                    if (payer == ResponsibleParty.PLATFORM) {
                        return Mono.just(java.util.Optional.of(new FundedReturn(payer, null)));
                    }
                    if (payer != ResponsibleParty.COURIER) {
                        return Mono.just(none);
                    }
                    Mono<UUID> atFault = parent.getDeliveryPersonId() == null ? Mono.empty()
                            : deliveryQuery.resolveActorIdForDeliveryPerson(tenantId, parent.getDeliveryPersonId());
                    return atFault.map(c -> java.util.Optional.of(new FundedReturn(payer, c)))
                            .defaultIfEmpty(java.util.Optional.of(new FundedReturn(payer, null)));
                })
                .defaultIfEmpty(none);
    }

    /** QO-22: the courier of the outbound leg owes the return the fund will pay (PENDING until it is delivered). */
    private Mono<Void> pendingDebt(PaymentIntent intent) {
        IDebtUseCase debtUseCase = debts.getIfAvailable();
        if (debtUseCase == null) {
            return Mono.empty();
        }
        return fundedReturn(intent.tenantId(), intent.announcementId())
                .filter(f -> f.isPresent() && f.get().payer() == ResponsibleParty.COURIER)
                .flatMap(f -> debtUseCase.recordPending(new IDebtUseCase.NewDebt(intent.tenantId(),
                        WalletDebt.DebtorType.COURIER, f.get().courierAtFault(), WalletDebt.Reason.RETURN_COST,
                        WalletDebt.returnReference(intent.deliveryId()), intent.deliveryId(),
                        intent.shares().courier(), intent.currency())))
                .onErrorResume(e -> {
                    log.error("RETURN_DEBT_UNRECORDED return={} — the fund pays it but the courier's debt could not be"
                            + " recorded: {}", intent.deliveryId(), e.toString());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<PaymentIntent.Mode> mode(UUID announcementId, String requested) {
        if (requested != null && !requested.isBlank()) {
            try {
                PaymentIntent.Mode m = PaymentIntent.Mode.valueOf(requested.trim().toUpperCase());
                if (m == PaymentIntent.Mode.FUND) {
                    return Mono.error(conflict("PAYMENT_MODE_INVALID",
                            "Only a return at the courier's or the platform's expense is paid by the fund", 400));
                }
                return Mono.just(m);
            } catch (IllegalArgumentException e) {
                return Mono.error(conflict("PAYMENT_MODE_INVALID", "Unknown payment mode " + requested, 400));
            }
        }
        return gofpAnnouncements.findById(announcementId)
                .map(a -> "CASH".equalsIgnoreCase(a.getPaymentMethod()) ? PaymentIntent.Mode.CASH
                        : PaymentIntent.Mode.ESCROW)
                .defaultIfEmpty(PaymentIntent.Mode.ESCROW);
    }

    private Mono<BigDecimal> rate(UUID tenantId, UUID deliveryId) {
        CommissionRatePort port = commissionRates.getIfAvailable();
        if (port == null) {
            return Mono.just(DEFAULT_RATE);
        }
        return Mono.defer(() -> port.deliveryCommissionRate(tenantId, deliveryId))
                .defaultIfEmpty(DEFAULT_RATE)
                .onErrorReturn(DEFAULT_RATE);
    }

    private Mono<UUID> tenant() {
        return tenants.currentTenantId().defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT);
    }

    private static WalletSettlementException conflict(String code, String message, int status) {
        return new WalletSettlementException(code, status, message);
    }
}
