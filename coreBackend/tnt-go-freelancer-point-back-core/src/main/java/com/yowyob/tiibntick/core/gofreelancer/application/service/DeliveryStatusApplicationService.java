package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ISettlementUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpNotificationTemplates;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.RecordSettlementCommand;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementMethod;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryStatusUpdateDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort.CoreDeliveryState;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryFailureReason;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordCustodyTransferUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordMissionUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordPaymentUseCase;
import com.yowyob.tiibntick.core.trust.domain.model.enums.CustodyTransferType;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.InvalidHandoverCodeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Application service handling delivery status transitions.
 *
 * <p>Orchestrates wallet, trust, notifications, and for relay deposits:
 * {@code tnt-delivery-core#depositAtRelayHub} + inventory/geo via {@link RelayDepositService}.</p>
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryStatusApplicationService {

    private final DeliveryRepository deliveryRepository;
    private final RelayDepositService relayDepositService;
    private final GofpRelayPointRepository gofpRelayPointRepository;
    private final PushNotificationPort pushNotificationPort;
    private final IDeliveryNeedRepository deliveryNeedRepository;
    private final IAnnouncementRepository announcementRepository;

    // ── OTP ────────────────────────────────────────────────────────────────
    private final OtpService otpService;
    private final DeliveryOtpService deliveryOtpService;

    // ── Settlement tracking (tnt-billing-wallet) — no money moves here any more ──
    private final ISettlementUseCase settlements;

    // ── Blockchain (tnt-trust-core) ────────────────────────────────────
    private final RecordCustodyTransferUseCase custodyTransferUseCase;
    private final RecordMissionUseCase missionUseCase;
    private final RecordPaymentUseCase paymentUseCase;

    // ── Core Logistics (tnt-delivery-core) ─────────────────────────────
    private final com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase deliveryQueryUseCase;
    /** Single authority on the delivery lifecycle: transitions are applied here first. */
    private final IDeliveryLifecyclePort lifecycle;

    // ── Tenant resolution ──────────────────────────────────────────────
    private final TenantContextHolder tenantContextHolder;

    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    /**
     * H5 extension point, shared with {@link DeliveryPaymentApplicationService}: the commission rate of a delivery.
     * Optional (setter): absent, empty or failing, the fixed {@link DeliveryPaymentApplicationService#COMMISSION_RATE}.
     */
    private com.yowyob.tiibntick.core.gofreelancer.application.port.out.CommissionRatePort commissionRates;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCommissionRates(com.yowyob.tiibntick.core.gofreelancer.application.port.out.CommissionRatePort rates) {
        this.commissionRates = rates;
    }

    private Mono<java.math.BigDecimal> commissionRate(UUID tenantId, UUID deliveryId) {
        java.math.BigDecimal fixed = DeliveryPaymentApplicationService.COMMISSION_RATE;
        if (commissionRates == null) return Mono.just(fixed);
        return Mono.defer(() -> commissionRates.deliveryCommissionRate(tenantId, deliveryId))
                .defaultIfEmpty(fixed)
                .onErrorResume(e -> {
                    log.warn("Commission rate lookup failed for delivery {} — using {}", deliveryId, fixed, e);
                    return Mono.just(fixed);
                });
    }

    private io.micrometer.core.instrument.Counter paymentFailures;

    @jakarta.annotation.PostConstruct
    void initMetrics() {
        paymentFailures = io.micrometer.core.instrument.Counter.builder("gofp.delivery.payment.failures")
                .description("Deliveries saved as DELIVERED/AT_RELAY_HUB whose payment then failed (unpaid)")
                .register(meterRegistry);
    }

    /** Placeholder driver id used for blockchain anchors when the delivery has no assigned person. */
    private static final UUID UNKNOWN_DRIVER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /** Lot C-36 — in-app notifications of the journey (absent in narrow tests). */
    private InAppEvents inAppEvents;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setInAppEvents(InAppEvents inAppEvents) {
        this.inAppEvents = inAppEvents;
    }

    private Mono<Void> inApp(java.util.function.Function<InAppEvents, Mono<Void>> event) {
        return inAppEvents == null ? Mono.empty() : event.apply(inAppEvents);
    }

    /**
     * Moves a delivery through its lifecycle.
     *
     * <p>{@code tnt-delivery-core} is the single authority on the lifecycle: every transition is first
     * applied through {@link IDeliveryLifecyclePort}, whose state machine refuses an illegal move
     * (the error propagates and nothing is written here) and publishes the mission events that
     * realtime, incident, sales, invoicing and notifications react to. Only after the core accepted
     * it does gofp read the resulting state back (nothing is copied onto its own row: the status,
     * instants and price live in the core only) and run its product side-effects:
     * <ul>
     *   <li>{@code PICKED_UP} — the sender's pickup OTP is checked, then custody is anchored;</li>
     *   <li>{@code IN_TRANSIT} — mission creation is anchored (also resumes a stop at a relay point);</li>
     *   <li>{@code DELIVERED} (direct) — the recipient's OTP is checked, then settlement,
     *       mission completion and custody to the recipient are recorded;</li>
     *   <li>relay deposit — a RelayDeposit is created, custody to the hub anchored, people notified;</li>
     *   <li>{@code FAILED} / {@code CANCELLED} — recorded in the core.</li>
     * </ul>
     *
     * @param deliveryId UUID of the delivery to update
     * @param dto        DTO containing the new status and optional relay point info
     * @return the updated Delivery
     */
    public Mono<Delivery> updateStatus(UUID deliveryId, DeliveryStatusUpdateDTO dto) {
        // Lot C-56 — la livraison vit dans le tenant de l'expéditeur ; le livreur porte le sien.
        Mono<UUID> deliveryTenant = lifecycle.tenantOf(deliveryId);
        return (deliveryTenant == null ? Mono.<UUID>empty() : deliveryTenant.onErrorResume(e -> Mono.empty()))
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMap(tenant -> TenantContextHolder.underTenant(tenant.orElse(null),
                        updateStatusInCurrentTenant(deliveryId, dto)));
    }

    private Mono<Delivery> updateStatusInCurrentTenant(UUID deliveryId, DeliveryStatusUpdateDTO dto) {
        return tenantContextHolder.currentTenantId()
                .defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT)
                .flatMap(tenantId -> deliveryRepository.findById(deliveryId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Delivery not found: " + deliveryId)))
                        // Lazy OTP init: generate and send codes if not yet done
                        .flatMap(deliveryOtpService::initOtpIfAbsent)
                        .flatMap(delivery -> lifecycle.findState(tenantId, deliveryId)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                        "Core delivery not found: " + deliveryId)))
                                .flatMap(core -> dispatch(tenantId, delivery, core, dto))));
    }

    private Mono<Delivery> dispatch(UUID tenantId, Delivery delivery, CoreDeliveryState core,
                                    DeliveryStatusUpdateDTO dto) {
        DeliveryStatus target = dto.getStatus();
        UUID deliveryId = delivery.getId();
        UUID driver = core.deliveryPersonId() != null ? core.deliveryPersonId()
                : delivery.getFreelancerId() != null ? delivery.getFreelancerId() : UNKNOWN_DRIVER;
        log.info("Updating delivery {} status {} -> {} via delivery-core", deliveryId, core.status(), target);

        boolean relayDeposit = dto.getRelayPointId() != null
                && (DeliveryStatus.DELIVERED.equals(target) || DeliveryStatus.AT_RELAY_HUB.equals(target));
        if (relayDeposit) {
            return depositAtRelay(tenantId, delivery, core, driver, dto);
        }
        return switch (target) {
            case PICKED_UP -> pickUp(tenantId, delivery, driver, dto);
            case IN_TRANSIT -> startOrResumeTransit(tenantId, delivery, core, driver);
            case DELIVERED -> deliverDirect(tenantId, delivery, core, driver, dto);
            // rows D9/D10: a failed hand-over attempt with its reason code; the core decides whether the delivery
            // fails for good (refusal, maximum of attempts) — lot evt-l2, decision [C.2], QO-3
            case FAILED, DELIVERY_ATTEMPT_FAILED -> attemptReason(dto.getReason())
                    .flatMap(reason -> project(delivery,
                            lifecycle.recordFailedAttempt(tenantId, deliveryId, driver, reason)));
            case OUT_FOR_DELIVERY -> project(delivery, lifecycle.startFinalLeg(tenantId, deliveryId, driver));
            case CANCELLED -> project(delivery, lifecycle.cancel(tenantId, deliveryId, driver,
                    dto.getReason() != null && !dto.getReason().isBlank() ? dto.getReason() : "Cancelled by the delivery person"));
            case AT_RELAY_HUB -> Mono.error(new IllegalArgumentException(
                    "relayPointId is required to deposit a parcel at a relay point"));
            default -> Mono.error(new IllegalArgumentException(
                    "Status " + target + " cannot be set directly; it is driven by the delivery lifecycle"));
        };
    }

    /**
     * The reason of a failed attempt: a {@link DeliveryFailureReason} attempt code ({@code CLIENT_*}). A free text
     * is refused: the core counts attempts and decides the return on the code (QO-3, [B.4]).
     */
    static Mono<DeliveryFailureReason> attemptReason(String reason) {
        if (reason != null) {
            try {
                DeliveryFailureReason code = DeliveryFailureReason.valueOf(reason.trim());
                if (code.isAttemptReason()) {
                    return Mono.just(code);
                }
            } catch (IllegalArgumentException notACode) {
                // fall through
            }
        }
        return Mono.error(new IllegalArgumentException("reason must be one of "
                + java.util.Arrays.stream(DeliveryFailureReason.values()).filter(DeliveryFailureReason::isAttemptReason)
                .map(Enum::name).toList() + ", got: " + reason));
    }

    /**
     * Reads the state the core returned onto the delivery to hand back. Nothing is written to the gofp
     * row: the lifecycle facts are the core's, and the transition was already recorded there.
     */
    private Mono<Delivery> project(Delivery delivery, Mono<CoreDeliveryState> coreResult) {
        return coreResult.map(state -> CoreDeliveryAssembler.hydrate(delivery, state));
    }

    private Mono<Delivery> pickUp(UUID tenantId, Delivery delivery, UUID driver, DeliveryStatusUpdateDTO dto) {
        UUID deliveryId = delivery.getId();
        if (dto.getConfirmationCode() == null || dto.getConfirmationCode().isBlank()) {
            return Mono.error(new IllegalArgumentException(
                    "Un code de confirmation est requis pour confirmer la prise en charge du colis (PICKED_UP). " +
                    "Demandez le code à l'expéditeur."));
        }
        if (!otpService.verifyOtp(dto.getConfirmationCode(), delivery.getPickupOtpHash())) {
            return Mono.error(new InvalidHandoverCodeException(
                    "Code de confirmation incorrect. Vérifiez le code auprès de l'expéditeur."));
        }
        log.info("Pickup OTP verified for delivery {} — confirming pickup in delivery-core", deliveryId);
        return lifecycle.confirmPickup(tenantId, deliveryId, driver)
                .flatMap(state -> {
                    Delivery saved = CoreDeliveryAssembler.hydrate(delivery, state);
                    return anchorPickupFromSender(saved, deliveryId, driver, parcelIdOf(state, delivery))
                            .thenReturn(saved);
                })
                .flatMap(saved -> saved.getDeliveryNeedId() == null ? Mono.just(saved)
                        : inApp(n -> n.pickedUp(saved.getDeliveryNeedId())).thenReturn(saved));
    }

    /**
     * The parcel the delivery carries: the id the core reports; for a row the core cannot describe, the
     * delivery need (or, failing that, the delivery) so that an anchor is never keyed on nothing.
     */
    private static UUID parcelIdOf(CoreDeliveryState state, Delivery delivery) {
        if (state != null && state.parcelId() != null) {
            return state.parcelId();
        }
        return delivery.getDeliveryNeedId() != null ? delivery.getDeliveryNeedId() : delivery.getId();
    }

    /** Blockchain: PICKUP_FROM_SENDER, certified by the OTP (best-effort, ADR-018). */
    private Mono<Void> anchorPickupFromSender(Delivery delivery, UUID deliveryId, UUID driver, UUID packetId) {
        Instant pickupTime = delivery.getActualPickupTime() != null ? delivery.getActualPickupTime() : Instant.now();
        LocalDateTime pickupLdt = LocalDateTime.ofInstant(pickupTime, java.time.ZoneOffset.UTC);
        String pocHash = CustodyTransferRecord.computePocHash(
                packetId.toString(), null, driver.toString(),
                CustodyTransferType.PICKUP_FROM_SENDER.name(), pickupLdt.toString(), null, null);
        CustodyTransferRecord record = new CustodyTransferRecord(
                UUID.randomUUID().toString(), packetId.toString(), null, "default",
                null, driver.toString(), CustodyTransferType.PICKUP_FROM_SENDER, null, pickupLdt);
        record.setPocHash(pocHash);
        return custodyTransferUseCase.record(record)
                .doOnSuccess(txHash -> log.info("Blockchain: custody PICKUP_FROM_SENDER anchored — delivery={}, txHash={}",
                        deliveryId, txHash))
                .doOnError(e -> log.error("Blockchain: failed to anchor PICKUP_FROM_SENDER for delivery {} — "
                        + "best-effort, not blocking (ADR-018)", deliveryId, e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private Mono<Delivery> startOrResumeTransit(UUID tenantId, Delivery delivery, CoreDeliveryState before, UUID driver) {
        UUID deliveryId = delivery.getId();
        boolean resume = DeliveryStatus.AT_RELAY_HUB.name().equals(before.status());
        return project(delivery, lifecycle.startOrResumeTransit(tenantId, deliveryId, driver))
                .flatMap(saved -> resume ? Mono.just(saved)
                        : anchorMissionCreated(deliveryId, driver).thenReturn(saved));
    }

    /**
     * The core refuses {@code PICKED_UP → DELIVERED} and {@code PICKED_UP → AT_RELAY_HUB}: a parcel
     * must be in transit first. The livreur's single "delivered" / "deposit" tap therefore also
     * starts the transit when it was not started yet, so no earlier flow is lost.
     */
    private Mono<CoreDeliveryState> ensureInTransit(UUID tenantId, UUID deliveryId, CoreDeliveryState core, UUID driver) {
        if (!DeliveryStatus.PICKED_UP.name().equals(core.status())) {
            return Mono.just(core);
        }
        return lifecycle.startOrResumeTransit(tenantId, deliveryId, driver)
                .flatMap(state -> anchorMissionCreated(deliveryId, driver).thenReturn(state));
    }

    private Mono<Void> anchorMissionCreated(UUID deliveryId, UUID driver) {
        return missionUseCase.recordCreated(deliveryId.toString(), driver.toString(), "default", 1)
                .doOnSuccess(txHash -> log.info("Blockchain: mission CREATED anchored for delivery {} — txHash={}",
                        deliveryId, txHash))
                .doOnError(e -> log.error("Blockchain: failed to anchor mission CREATED for delivery {} — "
                        + "best-effort, not blocking (ADR-018)", deliveryId, e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private Mono<Delivery> deliverDirect(UUID tenantId, Delivery delivery, CoreDeliveryState core, UUID driver,
                                         DeliveryStatusUpdateDTO dto) {
        UUID deliveryId = delivery.getId();
        if (dto.getConfirmationCode() == null || dto.getConfirmationCode().isBlank()) {
            return Mono.error(new IllegalArgumentException(
                    "Un code de confirmation est requis pour confirmer la livraison directe (DELIVERED). "
                            + "Demandez le code au destinataire."));
        }
        if (!otpService.verifyOtp(dto.getConfirmationCode(), delivery.getDeliveryOtpHash())) {
            return Mono.error(new InvalidHandoverCodeException(
                    "Code de confirmation incorrect. Vérifiez le code auprès du destinataire."));
        }
        log.info("Delivery OTP verified for delivery {} — completing in delivery-core (direct)", deliveryId);
        // DELIVERED is persisted BEFORE the payment runs (Lot C-20.3): the parcel was handed over.
        return project(delivery, ensureInTransit(tenantId, deliveryId, core, driver)
                .then(Mono.defer(() -> lifecycle.complete(tenantId, deliveryId, driver))))
                .flatMap(saved -> Mono.when(
                                processDeliveryPayment(saved, driver, tenantId),
                                anchorMissionCompleted(deliveryId, driver),
                                anchorOtpCertifiedDelivery(deliveryId, driver,
                                        saved.getActualDeliveryTime() != null ? saved.getActualDeliveryTime() : Instant.now()))
                        .thenReturn(saved))
                .flatMap(saved -> saved.getDeliveryNeedId() == null ? Mono.just(saved)
                        : inApp(n -> n.handedOver(saved.getDeliveryNeedId(), saved.getFreelancerId(), null))
                                .thenReturn(saved));
    }

    /**
     * Deposit at a relay point. The parcel is deposited only at the relay point the sender chose
     * ({@code DeliveryNeed.targetRelayPointId}), only if that relay point is approved and active, and
     * the deposit is recorded for the owner of the delivery need — the request never names who will
     * collect the parcel. (The caller is the assigned delivery person: checked by the controller.)
     */
    private Mono<Delivery> depositAtRelay(UUID tenantId, Delivery delivery, CoreDeliveryState core, UUID driver,
                                          DeliveryStatusUpdateDTO dto) {
        UUID deliveryId = delivery.getId();
        UUID needId = delivery.getDeliveryNeedId();
        if (needId == null) {
            return Mono.error(new IllegalArgumentException(
                    "Only a delivery created from a delivery need can be deposited at a relay point"));
        }
        return deliveryNeedRepository.findById(needId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Delivery need not found: " + needId)))
                .flatMap(need -> {
                    if (need.getTargetRelayPointId() == null) {
                        return Mono.error(new IllegalArgumentException(
                                "The sender did not choose a relay point for this delivery"));
                    }
                    if (need.getUserId() == null) {
                        return Mono.error(new IllegalArgumentException(
                                "The delivery need has no owner to collect the parcel"));
                    }
                    // The deposit is keyed on the parcel (same id from the need to the hub inventory), not on
                    // the need: the core's parcel id, else the one the need was registered with.
                    UUID parcelId = core.parcelId() != null ? core.parcelId()
                            : need.getPacketId() != null ? need.getPacketId() : needId;
                    return findRelayPoint(dto.getRelayPointId())
                            .flatMap(rp -> checkDepositTarget(rp, need.getTargetRelayPointId()))
                            .flatMap(hubId -> depositAt(tenantId, delivery, core, driver, dto, parcelId,
                                    need.getUserId(), hubId, need.getRequestedStorageDays()));
                });
    }

    /** The relay point must be the one chosen by the sender, approved and active; returns its hub id. */
    static Mono<UUID> checkDepositTarget(GofpRelayPoint rp, UUID target) {
        boolean chosen = target.equals(rp.getId()) || target.equals(rp.getCoreRelayPointId());
        if (!chosen) {
            return Mono.error(new IllegalArgumentException(
                    "The parcel must be deposited at the relay point chosen by the sender"));
        }
        if (rp.getStatus() != RelayPointStatus.APPROVED || !Boolean.TRUE.equals(rp.getIsActive())) {
            return Mono.error(new IllegalArgumentException("This relay point does not accept deposits"));
        }
        if (rp.getCoreRelayPointId() == null) {
            return Mono.error(new IllegalArgumentException("This relay point has no hub yet"));
        }
        return Mono.just(rp.getCoreRelayPointId());
    }

    private Mono<Delivery> depositAt(UUID tenantId, Delivery delivery, CoreDeliveryState core, UUID driver,
                                     DeliveryStatusUpdateDTO dto, UUID parcelId, UUID clientId, UUID hubId,
                                     Integer requestedStorageDays) {
        UUID deliveryId = delivery.getId();
        java.util.concurrent.atomic.AtomicReference<CoreDeliveryState> deposited =
                new java.util.concurrent.atomic.AtomicReference<>();
        // The core refuses the deposit unless the parcel is in transit; nothing is written then.
        // QO-30: the core computes the storage = min(requested, storage of the relay) — no default here.
        return project(delivery, ensureInTransit(tenantId, deliveryId, core, driver)
                .then(Mono.defer(() -> lifecycle.depositAtRelayHub(tenantId, deliveryId, driver, hubId,
                        requestedStorageDays)))
                .doOnNext(deposited::set))
                .flatMap(saved -> {
                    Mono<Void> createDeposit = relayDepositService
                            .createRelayDeposit(parcelId, clientId, hubId, dto.getStorageFee(), driver)
                            .doOnSuccess(deposit -> log.info("RelayDeposit mirror {} for delivery {} hub={}",
                                    deposit.getId(), deliveryId, hubId))
                            .flatMap(deposit -> notifyRelayDeposit(hubId, clientId, deposited.get()))
                            .then();
                    // D-149: the deposit ends the courier's mission (anchoring + payment) only when the relay is
                    // the destination of the delivery; a relay on the way (transfer) ends nothing.
                    boolean destination = deposited.get() != null && deposited.get().atDestinationRelay();
                    if (!destination) {
                        log.info("Delivery {} deposited at hub {} on the way: mission not ended (D-149)",
                                deliveryId, hubId);
                    }
                    return Mono.when(createDeposit,
                                    destination ? processDeliveryPayment(saved, driver, tenantId) : Mono.empty(),
                                    destination ? anchorMissionCompleted(deliveryId, driver) : Mono.empty(),
                                    anchorCustodyTransferToHub(deliveryId, driver, parcelId, hubId))
                            // C-36: sender and relay owner are told once the relay is the destination (D-149).
                            .then(destination && saved.getDeliveryNeedId() != null
                                    ? inApp(n -> n.depositedAtRelay(saved.getDeliveryNeedId(), hubId))
                                    : Mono.empty())
                            .thenReturn(saved);
                });
    }

    private Mono<Void> anchorMissionCompleted(UUID deliveryId, UUID driver) {
        return missionUseCase.recordCompleted(deliveryId.toString(), driver.toString(), "default")
                .doOnSuccess(txHash -> log.info("Blockchain: mission COMPLETED anchored for delivery {} — txHash={}",
                        deliveryId, txHash))
                .doOnError(e -> log.error("Blockchain: failed to anchor mission COMPLETED for delivery {} — "
                        + "best-effort, not blocking (ADR-018)", deliveryId, e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private Mono<GofpRelayPoint> findRelayPoint(UUID relayPointId) {
        return gofpRelayPointRepository.findByCoreRelayPointId(relayPointId)
                .switchIfEmpty(gofpRelayPointRepository.findById(relayPointId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "No GofpRelayPoint found for id/coreRelayPointId: " + relayPointId)));
    }

    /**
     * Notifies the relay and the client of a deposit. The storage days are those the core computed for this deposit
     * (QO-30: min(requested, storage of the relay)); the code shown is the parcel tracking code (D-136).
     */
    private Mono<Void> notifyRelayDeposit(UUID hubId, UUID clientId, CoreDeliveryState deposited) {
        Mono<GofpRelayPoint> rpMono = gofpRelayPointRepository.findByCoreRelayPointId(hubId)
                .switchIfEmpty(gofpRelayPointRepository.findById(hubId));
        String trackingCode = deposited != null && deposited.trackingCode() != null ? deposited.trackingCode()
                : deposited != null ? String.valueOf(deposited.parcelId()) : "";
        Integer storageDays = deposited != null ? deposited.relayStorageDays() : null;

        return rpMono
                .flatMap(rp -> {
                    String rpName = rp.getName() != null && !rp.getName().isBlank()
                            ? rp.getName() : "Point Relais";

                    Mono<Void> notifyRelay = Mono.empty();
                    if (rp.getCoreFreelancerId() != null) {
                        notifyRelay = pushNotificationPort.sendLocalized(
                                rp.getCoreFreelancerId(),
                                GofpNotificationTemplates.RELAY_PARCEL_DEPOSITED,
                                java.util.Map.of("relayName", rpName, "trackingCode", trackingCode));
                    } else {
                        log.warn("Relay hub {} has no coreFreelancerId — skip RP push", hubId);
                    }

                    if (storageDays == null) {
                        log.warn("Deposit at hub {}: the core returned no storage (relay not the destination?) — "
                                + "client not notified of a storage deadline", hubId);
                        return notifyRelay;
                    }
                    Mono<Void> notifyClient = pushNotificationPort.sendLocalized(
                            clientId,
                            GofpNotificationTemplates.RELAY_PARCEL_ARRIVED,
                            java.util.Map.of("relayName", rpName, "storageDays", storageDays));

                    return Mono.when(notifyRelay, notifyClient);
                })
                .doOnError(e -> log.error("Failed to send deposit notifications hub={}", hubId, e))
                .onErrorResume(e -> Mono.empty());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Ownership guard
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Verifies that {@code callerActorId} is the actorId of the delivery person
     * assigned to {@code deliveryId}. Emits empty on success; signals
     * {@link AccessDeniedException} on mismatch. Signals
     * {@link IllegalArgumentException} when the delivery or person is not found.
     *
     * <p>The delivery's {@code freelancerId} is a profile ID
     * ({@code tnt_delivery_persons.id}), not a kernel actorId.
     * {@link com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase#resolveActorIdForDeliveryPerson}
     * bridges the two so we compare the right identifiers.</p>
     */
    public Mono<Void> assertCallerIsAssignedDeliveryPerson(UUID deliveryId, UUID callerActorId) {
        return tenantContextHolder.currentTenantId()
                .defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT)
                .flatMap(tenantId -> deliveryRepository.findById(deliveryId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                "Delivery not found: " + deliveryId)))
                        .flatMap(delivery -> {
                            UUID profileId = delivery.getFreelancerId();
                            if (profileId == null) {
                                return Mono.error(new AccessDeniedException(
                                        "Delivery " + deliveryId + " has no assigned delivery person"));
                            }
                            return deliveryQueryUseCase
                                    .resolveActorIdForDeliveryPerson(tenantId, profileId)
                                    .flatMap(resolvedActorId -> {
                                        if (!resolvedActorId.equals(callerActorId)) {
                                            return Mono.error(new AccessDeniedException(
                                                    "Caller " + callerActorId
                                                    + " is not the delivery person assigned to delivery "
                                                    + deliveryId));
                                        }
                                        return Mono.empty();
                                    });
                        }));
    }

    /**
     * Guards {@code PATCH /{id}/cancel}: the assigned delivery person OR the
     * original sender (announcement's {@code clientId}) may cancel.
     *
     * <p><strong>Authorisation decision (Lot C-12):</strong> both parties have a
     * legitimate reason to cancel — the delivery person if the pickup is
     * impossible, the sender if they no longer need the delivery. Neither party
     * should be able to cancel the other's delivery without consent.</p>
     *
     * <p>If the delivery has no {@code announcementId} (e.g. created from a
     * {@code DeliveryNeed} with no linked announcement), only the assigned
     * delivery person is checked; the sender identity is not resolvable and
     * access is restricted to the livreur.</p>
     */
    public Mono<Void> assertCallerCanCancelDelivery(UUID deliveryId, UUID callerActorId) {
        return tenantContextHolder.currentTenantId()
                .defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT)
                .flatMap(tenantId -> deliveryRepository.findById(deliveryId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                "Delivery not found: " + deliveryId)))
                        .flatMap(delivery -> {
                            // Check 1: is the caller the assigned delivery person?
                            UUID profileId = delivery.getFreelancerId();
                            Mono<Boolean> isDeliveryPerson = (profileId != null)
                                    ? deliveryQueryUseCase
                                            .resolveActorIdForDeliveryPerson(tenantId, profileId)
                                            .map(resolvedId -> resolvedId.equals(callerActorId))
                                            .doOnError(e -> log.warn(
                                                    "cancel: cannot resolve actorId for profile={} "
                                                    + "(delivery={}, caller={}) — treating as non-match: {}",
                                                    profileId, deliveryId, callerActorId, e.getMessage()))
                                            .onErrorReturn(false)
                                    : Mono.just(false);

                            // Check 2: is the caller the sender (announcement clientId)?
                            UUID annId = delivery.getAnnouncementId();
                            Mono<Boolean> isSender = (annId != null)
                                    ? announcementRepository.findById(annId)
                                            .map(ann -> callerActorId.equals(ann.getClientId()))
                                            .defaultIfEmpty(false)
                                            .doOnError(e -> log.warn(
                                                    "cancel: cannot fetch announcement={} "
                                                    + "(delivery={}, caller={}) — treating as non-sender: {}",
                                                    annId, deliveryId, callerActorId, e.getMessage()))
                                            .onErrorReturn(false)
                                    : Mono.just(false);

                            return Mono.zip(isDeliveryPerson, isSender)
                                    .flatMap(tuple -> {
                                        if (tuple.getT1() || tuple.getT2()) {
                                            return Mono.empty();
                                        }
                                        return Mono.error(new AccessDeniedException(
                                                "Caller " + callerActorId
                                                + " is neither the assigned delivery person"
                                                + " nor the sender of delivery " + deliveryId));
                                    });
                        }));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════════════════

    private Mono<Void> processDeliveryPayment(Delivery delivery, UUID freelancerId, UUID tenantId) {
        if (delivery.getTarif() == null || delivery.getTarif() <= 0) {
            log.warn("Delivery {} has no tarif set — skipping payment processing", delivery.getId());
            return Mono.empty();
        }

        log.info("Processing payment for delivery {} — amount: {} XAF", delivery.getId(), delivery.getTarif());

        // Resolve the kernel actorId from the delivery-person profile ID.
        // freelancerId is tnt_delivery_persons.id (profile ID); wallets are keyed on
        // tnt_delivery_persons.actor_id (kernel actorId). Using the wrong ID causes
        // WalletNotFoundException when the wallet is looked up by owner_id.
        return deliveryQueryUseCase.resolveActorIdForDeliveryPerson(tenantId, freelancerId)
                .doOnNext(actorId -> log.debug("Resolved actorId={} for freelancerId={} (delivery {})",
                        actorId, freelancerId, delivery.getId()))
                .flatMap(actorId -> processDeliveryPaymentWithActor(delivery, actorId, tenantId))
                .doOnError(e -> reportUnpaidDelivered(delivery, freelancerId, e));
    }

    /**
     * Lot C-20.3 — the DELIVERED status is persisted BEFORE the payment runs (no transaction
     * spans both, and none should: the parcel was handed over and the recipient's OTP was
     * verified — that fact must not be rolled back). A payment failure therefore leaves a
     * delivered-but-unpaid delivery. The error still propagates to the caller (HTTP 5xx), but
     * that alone is invisible to operations once the client gives up, so it is also counted
     * ({@code gofp.delivery.payment.failures}) and logged with a greppable
     * {@code UNPAID_DELIVERED} marker carrying everything needed to pay it by hand.
     */
    private void reportUnpaidDelivered(Delivery delivery, UUID freelancerId, Throwable e) {
        paymentFailures.increment();
        log.error("UNPAID_DELIVERED delivery={} freelancerProfile={} tarif={} — status DELIVERED is saved "
                        + "but the payment failed ({}: {}); no compensation runs, pay or retry manually",
                delivery.getId(), freelancerId, delivery.getTarif(),
                e.getClass().getSimpleName(), e.getMessage());
    }

    /**
     * Kernel wallet migration: completing a delivery no longer MOVES money. The client pays from
     * their own Kernel wallet through the challenge/confirm flow ({@code DeliveryPaymentController}),
     * which records a PAID settlement; this method only reconciles what happened at delivery time:
     * <ul>
     *   <li>already PAID by transfer → nothing to do;</li>
     *   <li>CASH → the delivery person kept the cash: record it and the platform commission as DUE
     *       (the Core cannot debit their Kernel wallet without their confirmation code);</li>
     *   <li>digital but not paid yet → record UNPAID, count it and log the greppable
     *       {@code UNPAID_DELIVERED} marker; the client can still pay afterwards. The HTTP call
     *       succeeds: the parcel was handed over and that fact must not be rolled back.</li>
     * </ul>
     */
    private Mono<Void> processDeliveryPaymentWithActor(Delivery delivery, UUID actorId, UUID tenantId) {
        Mono<String> paymentMethodMono;
        if (delivery.getDeliveryNeedId() != null) {
            paymentMethodMono = deliveryNeedRepository.findById(delivery.getDeliveryNeedId())
                    .map(need -> need.getPaymentMethod() != null ? need.getPaymentMethod() : "UNKNOWN");
        } else if (delivery.getAnnouncementId() != null) {
            paymentMethodMono = Mono.just("ORANGE_MONEY");
        } else {
            paymentMethodMono = Mono.just("UNKNOWN");
        }

        return paymentMethodMono.zipWith(commissionRate(tenantId, delivery.getId())).flatMap(methodAndRate -> {
            String paymentMethod = methodAndRate.getT1();
            log.info("Payment method for delivery {} is {}", delivery.getId(), paymentMethod);
            java.math.BigDecimal amount = java.math.BigDecimal.valueOf(delivery.getTarif())
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            java.math.BigDecimal rate = methodAndRate.getT2();

            Mono<String> settle;
            if ("CASH".equalsIgnoreCase(paymentMethod)) {
                settle = settlements.record(new RecordSettlementCommand(
                                tenantId, DeliveryPaymentApplicationService.REFERENCE_TYPE, delivery.getId(),
                                null, actorId, amount, DeliveryPaymentApplicationService.CURRENCY,
                                SettlementMethod.CASH, SettlementStatus.CASH_COLLECTED, null, rate))
                        .doOnSuccess(st -> log.info("Cash delivery {}: commission {} due by actorId={}",
                                delivery.getId(), st.getCommissionAmount(), actorId))
                        .thenReturn("CASH_COLLECTED_COMMISSION_DUE");
            } else {
                settle = settlements.record(new RecordSettlementCommand(
                                tenantId, DeliveryPaymentApplicationService.REFERENCE_TYPE, delivery.getId(),
                                null, actorId, amount, DeliveryPaymentApplicationService.CURRENCY,
                                SettlementMethod.WALLET_TRANSFER, SettlementStatus.UNPAID, null, rate))
                        .map(st -> {
                            if (st.getStatus() != SettlementStatus.PAID) {
                                paymentFailures.increment();
                                log.warn("UNPAID_DELIVERED delivery={} actor={} amount={} — delivered but the client "
                                                + "has not paid from their Kernel wallet yet (method {})",
                                        delivery.getId(), actorId, amount, paymentMethod);
                            }
                            return st.getStatus() == SettlementStatus.PAID
                                    ? "DIGITAL_WALLET_TRANSFER" : "";
                        });
            }

            // Blockchain trust-anchoring is best-effort (ADR-018); settlement recording is not.
            return settle.flatMap(action -> action.isEmpty() ? Mono.<Void>empty()
                    : paymentUseCase.record(
                                    delivery.getId().toString(), actorId.toString(), actorId.toString(),
                                    "default", action, delivery.getId().toString(),
                                    String.valueOf(delivery.getTarif()), "XAF")
                            .doOnSuccess(txHash -> log.info("Blockchain: payment anchored for delivery {} — txHash={}",
                                    delivery.getId(), txHash))
                            .doOnError(e -> log.error("Blockchain: failed to anchor payment for delivery {} — "
                                    + "best-effort, not blocking (ADR-018)", delivery.getId(), e))
                            .onErrorResume(e -> Mono.empty())
                            .then())
                    .doOnError(e -> log.error("Settlement recording failed for delivery {} — propagating",
                            delivery.getId(), e));
        });
    }

    private Mono<Void> anchorCustodyTransferToHub(UUID deliveryId, UUID freelancerId, UUID packetId, UUID relayPointId) {
        CustodyTransferRecord record = new CustodyTransferRecord(
                UUID.randomUUID().toString(),
                packetId.toString(),
                null,
                "default",
                freelancerId.toString(),
                relayPointId.toString(),
                CustodyTransferType.TRANSFER_TO_HUB,
                relayPointId.toString(),
                LocalDateTime.now()
        );

        return custodyTransferUseCase.record(record)
                .doOnSuccess(txHash -> log.info("Blockchain: custody TRANSFER_TO_HUB anchored — delivery={}, hub={}, txHash={}",
                        deliveryId, relayPointId, txHash))
                .doOnError(e -> log.error(
                        "Blockchain: failed to anchor custody transfer to hub for delivery {} — "
                                + "best-effort, not blocking (ADR-018)",
                        deliveryId, e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private Mono<Void> anchorCustodyTransferToRecipient(UUID deliveryId, UUID freelancerId) {
        CustodyTransferRecord record = new CustodyTransferRecord(
                UUID.randomUUID().toString(),
                deliveryId.toString(),
                null,
                "default",
                freelancerId.toString(),
                "RECIPIENT",
                CustodyTransferType.TRANSFER_TO_RECIPIENT,
                null,
                LocalDateTime.now()
        );

        return custodyTransferUseCase.record(record)
                .doOnSuccess(txHash -> log.info("Blockchain: custody TRANSFER_TO_RECIPIENT anchored — delivery={}, txHash={}",
                        deliveryId, txHash))
                .doOnError(e -> log.error(
                        "Blockchain: failed to anchor custody transfer to recipient for delivery {} — "
                                + "best-effort, not blocking (ADR-018)",
                        deliveryId, e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    /**
     * Anchors a TRANSFER_TO_RECIPIENT custody record that is OTP-certified.
     *
     * @param deliveryId   UUID of the delivery
     * @param freelancerId UUID of the delivery person handing over the parcel
     * @param deliveryTime the exact instant the OTP was validated (recipient confirmed)
     */
    private Mono<Void> anchorOtpCertifiedDelivery(UUID deliveryId, UUID freelancerId, Instant deliveryTime) {
        LocalDateTime deliveryLdt = LocalDateTime.ofInstant(deliveryTime, java.time.ZoneOffset.UTC);

        String pocHash = CustodyTransferRecord.computePocHash(
                deliveryId.toString(),
                freelancerId.toString(),
                "RECIPIENT",
                CustodyTransferType.TRANSFER_TO_RECIPIENT.name(),
                deliveryLdt.toString(),
                null, null
        );

        CustodyTransferRecord record = new CustodyTransferRecord(
                UUID.randomUUID().toString(),
                deliveryId.toString(),
                null,
                "default",
                freelancerId.toString(),
                "RECIPIENT",
                CustodyTransferType.TRANSFER_TO_RECIPIENT,
                null,
                deliveryLdt
        );
        record.setPocHash(pocHash);

        return custodyTransferUseCase.record(record)
                .doOnSuccess(txHash -> log.info(
                        "Blockchain: custody TRANSFER_TO_RECIPIENT anchored (OTP-certified) — " +
                        "delivery={}, freelancer={}, deliveryTime={}, pocHash={}, txHash={}",
                        deliveryId, freelancerId, deliveryLdt, pocHash, txHash))
                .doOnError(e -> log.error(
                        "Blockchain: failed to anchor OTP-certified delivery for delivery {} — "
                                + "best-effort, not blocking (ADR-018)",
                        deliveryId, e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Lot C-45 — who may read a delivery, who may change its price
    // ══════════════════════════════════════════════════════════════════════

    /** Platform staff: may read any delivery and is the only one allowed to change a price. */
    public static boolean isStaff(com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext ctx) {
        return ctx != null && (ctx.hasRole("TNT_ADMIN") || ctx.hasRole("SUPPORT_AGENT"));
    }

    /**
     * A delivery is readable by its client (announcement {@code clientId} or delivery-need
     * {@code userId}), its assigned delivery person, or staff. Unknown delivery → 404.
     */
    public Mono<Void> assertCallerCanReadDelivery(UUID deliveryId,
            com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext ctx) {
        return deliveryRepository.findById(deliveryId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Delivery not found: " + deliveryId)))
                .flatMap(delivery -> assertParty(delivery, ctx));
    }

    /** Same rule, the delivery being found through its announcement. */
    public Mono<Void> assertCallerCanReadDeliveryOfAnnouncement(UUID announcementId,
            com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext ctx) {
        return deliveryRepository.findByAnnouncementId(announcementId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Delivery not found for announcement: " + announcementId)))
                .flatMap(delivery -> assertParty(delivery, ctx));
    }

    /** Same rule, the delivery being found through its delivery need. */
    public Mono<Void> assertCallerCanReadDeliveryOfNeed(UUID deliveryNeedId,
            com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext ctx) {
        return deliveryRepository.findByDeliveryNeedId(deliveryNeedId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Delivery not found for delivery need: " + deliveryNeedId)))
                .flatMap(delivery -> assertParty(delivery, ctx));
    }

    /**
     * Price ({@code tarif}): staff only, and never once a payment has started — the delivery person
     * generated a receive code, or a settlement exists for this delivery. Other editable fields
     * (time windows) stay open to the parties of the delivery and staff.
     */
    public Mono<Void> assertCallerCanUpdateDelivery(UUID deliveryId, boolean changesPrice,
            com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext ctx) {
        return deliveryRepository.findById(deliveryId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Delivery not found: " + deliveryId)))
                .flatMap(delivery -> {
                    if (!changesPrice) {
                        return assertParty(delivery, ctx);
                    }
                    if (!isStaff(ctx)) {
                        return Mono.<Void>error(new AccessDeniedException(
                                "Only platform staff can change the price of a delivery"));
                    }
                    Mono<Boolean> escrowed = escrows == null ? Mono.just(false)
                            : escrows.intentOfDelivery(deliveryId).hasElement();
                    return escrowed.flatMap(started -> started ? Mono.<Void>error(paymentStarted(deliveryId))
                            : tenantContextHolder.currentTenantId()
                            .defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT)
                            .flatMap(tenantId -> settlements.find(tenantId,
                                    DeliveryPaymentApplicationService.REFERENCE_TYPE, deliveryId))
                            .hasElement()
                            .flatMap(settled -> settled
                                    ? Mono.<Void>error(paymentStarted(deliveryId))
                                    : Mono.<Void>empty()));
                });
    }

    /** Lot L5: a payment intent (escrow or cash) freezes the price. Optional for module tests. */
    private com.yowyob.tiibntick.core.billing.wallet.application.port.in.IEscrowUseCase escrows;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setEscrows(com.yowyob.tiibntick.core.billing.wallet.application.port.in.IEscrowUseCase escrows) {
        this.escrows = escrows;
    }

    private static org.springframework.web.server.ResponseStatusException paymentStarted(UUID deliveryId) {
        return new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                "The payment of delivery " + deliveryId + " has started: its price can no longer change");
    }

    private Mono<Void> assertParty(Delivery delivery,
            com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext ctx) {
        if (isStaff(ctx)) {
            return Mono.empty();
        }
        UUID caller = ctx != null ? ctx.userId() : null;
        if (caller == null) {
            return Mono.error(new AccessDeniedException("Authentication required to read a delivery"));
        }
        Mono<Boolean> viaAnnouncement = delivery.getAnnouncementId() == null ? Mono.just(false)
                : announcementRepository.findById(delivery.getAnnouncementId())
                        .map(a -> caller.equals(a.getClientId())).defaultIfEmpty(false);
        Mono<Boolean> viaNeed = delivery.getDeliveryNeedId() == null ? Mono.just(false)
                : deliveryNeedRepository.findById(delivery.getDeliveryNeedId())
                        .map(n -> caller.equals(n.getUserId())).defaultIfEmpty(false);
        Mono<Boolean> courier = Mono.defer(() -> assertCallerIsAssignedDeliveryPerson(delivery.getId(), caller)
                .thenReturn(true)
                .onErrorReturn(false));
        return viaAnnouncement.zipWith(viaNeed, (a, n) -> a || n)
                .flatMap(client -> client ? Mono.just(true) : courier)
                .flatMap(allowed -> allowed ? Mono.<Void>empty()
                        : Mono.<Void>error(new AccessDeniedException(
                                "Only the client, the assigned delivery person or staff can access delivery "
                                        + delivery.getId())));
    }
}
