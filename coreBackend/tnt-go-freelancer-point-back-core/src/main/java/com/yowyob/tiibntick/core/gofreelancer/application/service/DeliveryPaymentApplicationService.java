package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.invoice.application.port.in.SystemInvoicingUseCase;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.GenerateInvoiceCommand;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.InvoiceLine;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.LineItemType;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IEscrowUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ISettlementUseCase;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowMirror;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
import com.yowyob.tiibntick.core.billing.wallet.domain.policy.EscrowOutcomePolicy;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.PaymentChallengeResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.PaymentStatusResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.exception.DeliveryPaymentException;
import com.yowyob.tiibntick.core.gofreelancer.application.exception.DeliveryPaymentException.Reason;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryPaymentUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryPaymentApplicationService implements DeliveryPaymentUseCase {

    /** Settlement reference type, shared with the incident freeze adapter (a mission is a delivery). */
    public static final String REFERENCE_TYPE = "DELIVERY";
    public static final String CURRENCY = "XAF";
    /** Default platform commission rate, taken by the escrow at release (lot L5). */
    public static final BigDecimal COMMISSION_RATE = new BigDecimal("0.05");

    private final DeliveryRepository deliveryRepository;
    private final IAnnouncementRepository announcementRepository;
    private final IDeliveryNeedRepository deliveryNeedRepository;
    private final DeliveryStatusApplicationService deliveryStatusService;
    private final IEscrowUseCase escrows;
    private final ISettlementUseCase settlements;
    private final com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase deliveryQuery;
    private final TenantContextHolder tenantContextHolder;
    private final SystemInvoicingUseCase invoicing;
    private final GofpUserRepository gofpUserRepository;

    /**
     * H5 extension point: the commission rate of a delivery. Optional (setter): absent, empty or failing, the fixed
     * {@link #COMMISSION_RATE} applies.
     */
    private com.yowyob.tiibntick.core.gofreelancer.application.port.out.CommissionRatePort commissionRates;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCommissionRates(com.yowyob.tiibntick.core.gofreelancer.application.port.out.CommissionRatePort rates) {
        this.commissionRates = rates;
    }

    private Mono<BigDecimal> commissionRate(UUID tenantId, UUID deliveryId) {
        if (commissionRates == null) return Mono.just(COMMISSION_RATE);
        return Mono.defer(() -> commissionRates.deliveryCommissionRate(tenantId, deliveryId))
                .defaultIfEmpty(COMMISSION_RATE)
                .onErrorResume(e -> {
                    log.warn("Commission rate lookup failed for delivery {} — using {}", deliveryId, COMMISSION_RATE, e);
                    return Mono.just(COMMISSION_RATE);
                });
    }

    /** Printed on invoices: the platform brand and the market the gofp module serves. */
    static final String INVOICE_TENANT_CODE = "TNT";
    static final String INVOICE_COUNTRY_CODE = "CM";

    // ── 1. Client: challenge (escrow of an elected delivery) ───────────────

    /**
     * Lot L5: a delivery elected without payment (Go need, accepted invitation, legacy assign) is paid into the same
     * Kernel escrow as a paid selection (spec §4.1): payer = the client, COURIER = the assigned courier's wallet,
     * PLATFORM = the commission. The Kernel sends the client a code.
     */
    @Override
    public Mono<PaymentChallengeResponse> startPayment(UUID deliveryId, UUID callerUserId) {
        return findPayableDelivery(deliveryId, callerUserId)
                .flatMap(delivery -> tenant().flatMap(tenantId -> courierActor(tenantId, delivery)
                        .flatMap(courier -> commissionRate(tenantId, deliveryId)
                                .flatMap(rate -> escrows.createIntent(new IEscrowUseCase.IntentRequest(tenantId,
                                        deliveryId, delivery.getAnnouncementId(), null, PaymentIntent.Mode.ESCROW,
                                        amountOf(delivery), CURRENCY, rate, List.of(), callerUserId, courier,
                                        null))))
                        .map(ic -> new PaymentChallengeResponse(deliveryId, ic.intent().amount(), CURRENCY,
                                ic.challengeToken(), ic.expiresInSeconds(), ic.codePreview()))));
    }

    // ── 2. Client: confirm → the Kernel holds the money ────────────────────

    @Override
    public Mono<PaymentStatusResponse> confirmPayment(UUID deliveryId, UUID callerUserId,
                                                      String challengeToken, String code) {
        return findDelivery(deliveryId)
                .flatMap(delivery -> isClient(delivery, callerUserId).flatMap(isClient -> !isClient
                        ? Mono.<PaymentStatusResponse>error(new DeliveryPaymentException(Reason.FORBIDDEN,
                                "Only the client of delivery " + deliveryId + " can pay it"))
                        : escrows.intentOfDelivery(deliveryId)
                                .switchIfEmpty(Mono.error(() -> new DeliveryPaymentException(Reason.CONFLICT,
                                        "No payment started for delivery " + deliveryId
                                                + ": POST /api/v1/deliveries/" + deliveryId + "/payment/challenge")))
                                .flatMap(intent -> escrows.hold(intent.id(), callerUserId, challengeToken, code)
                                        // paid after the hand-over: the work is done, released at once (E4)
                                        .flatMap(e -> delivery.getStatus() == DeliveryStatus.DELIVERED
                                                ? escrows.apply(deliveryId, EscrowOutcomePolicy.Action.RELEASE,
                                                        "PAID_AFTER_DELIVERY")
                                                : Mono.just(e))
                                        .flatMap(e -> tenant().flatMap(tenantId -> {
                                            delivery.setDriverUserId(intent.courierActorId());
                                            return issueInvoice(tenantId, delivery, callerUserId, intent.amount(),
                                                    e.kernelEscrowId().toString())
                                                    .then(status(tenantId, delivery, e));
                                        })))));
    }

    /**
     * The paid invoice of this delivery: the client's receipt and the driver's statement of income.
     * Best effort by design — the money has already moved and the settlement is recorded, so a failure
     * here (invoice sequence, PDF storage…) is logged, never turned into a payment error. The invoice
     * module issues at most one invoice per delivery, so a retried confirmation cannot duplicate it.
     */
    private Mono<Void> issueInvoice(UUID tenantId, Delivery delivery, UUID payerUserId, BigDecimal amount,
                                    String paymentRef) {
        UUID driverUserId = delivery.getDriverUserId();
        return driverTradeName(driverUserId)
                .flatMap(tradeName -> {
                    InvoiceLine line = InvoiceLine.of(1, "Livraison " + delivery.getId(), 1.0,
                            com.yowyob.tiibntick.core.billing.invoice.domain.model.Money.of(amount.doubleValue(), CURRENCY),
                            BigDecimal.ZERO, LineItemType.DELIVERY_FEE);
                    GenerateInvoiceCommand command = new GenerateInvoiceCommand(
                            tenantId, INVOICE_TENANT_CODE, INVOICE_COUNTRY_CODE,
                            delivery.getId().toString(), null, payerUserId.toString(),
                            List.of(line), List.of(), CURRENCY, LocalDateTime.now(),
                            "FREELANCER_ORG", driverUserId != null ? driverUserId.toString() : null,
                            tradeName, false, List.of(), false, null);
                    return invoicing.issuePaidInvoiceAsSystem(command, paymentRef);
                })
                .doOnNext(inv -> log.info("Invoice {} issued for delivery {}", inv.getNumber(), delivery.getId()))
                .onErrorResume(e -> {
                    log.error("INVOICE_NOT_ISSUED delivery={} — payment is recorded, invoice must be issued by hand: {}",
                            delivery.getId(), e.toString());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<String> driverTradeName(UUID driverUserId) {
        if (driverUserId == null) return Mono.just("Livreur");
        return gofpUserRepository.findByCoreUserId(driverUserId)
                .map(u -> ((u.getFirstName() != null ? u.getFirstName() : "") + " "
                        + (u.getLastName() != null ? u.getLastName() : "")).trim())
                .filter(n -> !n.isBlank())
                .defaultIfEmpty("Livreur")
                .onErrorReturn("Livreur");
    }

    // ── Status ─────────────────────────────────────────────────────────────

    @Override
    public Mono<PaymentStatusResponse> getPaymentStatus(UUID deliveryId, UUID callerUserId) {
        return findDelivery(deliveryId)
                .flatMap(delivery -> isClient(delivery, callerUserId)
                        .zipWith(isDriver(delivery, callerUserId))
                        .flatMap(roles -> {
                            if (!roles.getT1() && !roles.getT2()) {
                                return Mono.<PaymentStatusResponse>error(new DeliveryPaymentException(
                                        Reason.FORBIDDEN, "Only the client or the assigned delivery person "
                                        + "can read the payment of delivery " + deliveryId));
                            }
                            return tenant().flatMap(tenantId -> escrows.find(deliveryId)
                                    .flatMap(e -> status(tenantId, delivery, e))
                                    .switchIfEmpty(Mono.defer(() -> settlements.find(tenantId, REFERENCE_TYPE, deliveryId)
                                            .map(s -> toResponse(delivery, s, null))))
                                    .switchIfEmpty(Mono.fromSupplier(() -> notSettled(delivery))));
                        }));
    }

    /** The escrow state; once released, the settlement it produced (PAID, commission). */
    private Mono<PaymentStatusResponse> status(UUID tenantId, Delivery delivery, EscrowMirror e) {
        return settlements.find(tenantId, REFERENCE_TYPE, delivery.getId())
                .map(s -> toResponse(delivery, s, e.status().name()))
                .switchIfEmpty(Mono.fromSupplier(() -> new PaymentStatusResponse(delivery.getId(),
                        e.status().name(), e.amount(), e.currency(), "ESCROW", null, null, false, null,
                        e.status().name())));
    }

    // ── Guards ─────────────────────────────────────────────────────────────

    /** Delivery exists, caller is its client, payable (tarif, not cancelled/failed, not cash, no escrow yet). */
    private Mono<Delivery> findPayableDelivery(UUID deliveryId, UUID callerUserId) {
        return findDelivery(deliveryId).flatMap(delivery ->
                isClient(delivery, callerUserId).flatMap(isClient -> {
                    if (!isClient) {
                        return Mono.<Delivery>error(new DeliveryPaymentException(Reason.FORBIDDEN,
                                "Only the client of delivery " + deliveryId + " can pay it"));
                    }
                    if (delivery.getStatus() == DeliveryStatus.CANCELLED || delivery.getStatus() == DeliveryStatus.FAILED) {
                        return Mono.<Delivery>error(new DeliveryPaymentException(Reason.CONFLICT,
                                "Delivery " + deliveryId + " is " + delivery.getStatus().name().toLowerCase()));
                    }
                    if (delivery.getTarif() == null || delivery.getTarif() <= 0) {
                        return Mono.<Delivery>error(new DeliveryPaymentException(Reason.CONFLICT,
                                "Delivery " + deliveryId + " has no tarif to pay"));
                    }
                    return paymentMethodOf(delivery).flatMap(method -> {
                        if ("CASH".equalsIgnoreCase(method)) {
                            return Mono.<Delivery>error(new DeliveryPaymentException(Reason.CONFLICT,
                                    "Delivery " + deliveryId + " is paid in cash to the delivery person"));
                        }
                        return escrows.find(deliveryId)
                                .map(e -> e.status() != EscrowMirror.Status.REFUNDED)
                                .defaultIfEmpty(false)
                                .flatMap(alreadyPaid -> alreadyPaid
                                        ? Mono.<Delivery>error(new DeliveryPaymentException(Reason.CONFLICT,
                                                "Delivery " + deliveryId + " is already paid"))
                                        : Mono.just(delivery));
                    });
                }));
    }

    private Mono<Delivery> findDelivery(UUID deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .switchIfEmpty(Mono.error(new DeliveryPaymentException(
                        Reason.NOT_FOUND, "Delivery not found: " + deliveryId)));
    }

    /** The client is the announcement's clientId or the delivery need's userId. */
    private Mono<Boolean> isClient(Delivery delivery, UUID callerUserId) {
        Mono<Boolean> viaAnnouncement = delivery.getAnnouncementId() == null ? Mono.just(false)
                : announcementRepository.findById(delivery.getAnnouncementId())
                        .map(a -> callerUserId.equals(a.getClientId())).defaultIfEmpty(false);
        Mono<Boolean> viaNeed = delivery.getDeliveryNeedId() == null ? Mono.just(false)
                : deliveryNeedRepository.findById(delivery.getDeliveryNeedId())
                        .map(n -> callerUserId.equals(n.getUserId())).defaultIfEmpty(false);
        return viaAnnouncement.zipWith(viaNeed, (a, n) -> a || n);
    }

    private Mono<Boolean> isDriver(Delivery delivery, UUID callerUserId) {
        return deliveryStatusService.assertCallerIsAssignedDeliveryPerson(delivery.getId(), callerUserId)
                .thenReturn(true)
                .onErrorReturn(false);
    }

    private Mono<String> paymentMethodOf(Delivery delivery) {
        if (delivery.getDeliveryNeedId() != null) {
            return deliveryNeedRepository.findById(delivery.getDeliveryNeedId())
                    .map(n -> n.getPaymentMethod() != null ? n.getPaymentMethod() : "UNKNOWN")
                    .defaultIfEmpty("UNKNOWN");
        }
        if (delivery.getAnnouncementId() != null) {
            return announcementRepository.findById(delivery.getAnnouncementId())
                    .map(a -> a.getPaymentMethod() != null ? a.getPaymentMethod() : "UNKNOWN")
                    .defaultIfEmpty("UNKNOWN");
        }
        return Mono.just("UNKNOWN");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    static BigDecimal amountOf(Delivery delivery) {
        return BigDecimal.valueOf(delivery.getTarif()).setScale(2, RoundingMode.HALF_UP);
    }

    static String reference(UUID deliveryId) {
        return "DELIVERY:" + deliveryId;
    }

    private Mono<UUID> tenant() {
        return tenantContextHolder.currentTenantId().defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT);
    }

    /** The actor (Kernel user) behind the delivery's assigned courier. */
    private Mono<UUID> courierActor(UUID tenantId, Delivery delivery) {
        if (delivery.getFreelancerId() == null) {
            return Mono.error(new DeliveryPaymentException(Reason.CONFLICT,
                    "Delivery " + delivery.getId() + " has no courier yet"));
        }
        return deliveryQuery.resolveActorIdForDeliveryPerson(tenantId, delivery.getFreelancerId())
                .switchIfEmpty(Mono.error(() -> new DeliveryPaymentException(Reason.CONFLICT,
                        "No account for the courier of delivery " + delivery.getId())));
    }

    private static PaymentStatusResponse toResponse(Delivery delivery, WalletSettlement s, String escrowStatus) {
        return new PaymentStatusResponse(delivery.getId(), s.getStatus().name(), s.getAmount(), s.getCurrency(),
                s.getMethod().name(), s.getCommissionStatus().name(), s.getCommissionAmount(), false,
                s.getPaidAt() == null ? null : s.getPaidAt().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                escrowStatus);
    }

    private static PaymentStatusResponse notSettled(Delivery delivery) {
        boolean payable = delivery.getTarif() != null && delivery.getTarif() > 0
                && delivery.getStatus() != DeliveryStatus.CANCELLED && delivery.getStatus() != DeliveryStatus.FAILED
                && delivery.getFreelancerId() != null;
        return new PaymentStatusResponse(delivery.getId(), "NOT_SETTLED",
                delivery.getTarif() == null ? null : amountOf(delivery), CURRENCY,
                null, null, null, payable, null, null);
    }
}
