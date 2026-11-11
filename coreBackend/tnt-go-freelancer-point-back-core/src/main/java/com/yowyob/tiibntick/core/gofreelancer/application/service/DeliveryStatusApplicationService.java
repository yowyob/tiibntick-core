package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ISettlementUseCase;
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
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordCustodyTransferUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordMissionUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordPaymentUseCase;
import com.yowyob.tiibntick.core.trust.domain.model.enums.CustodyTransferType;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
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
 * {@code tnt-delivery-core#depositAtRelayPoint} + inventory/geo via {@link RelayDepositService}.</p>
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

    private io.micrometer.core.instrument.Counter paymentFailures;

    @jakarta.annotation.PostConstruct
    void initMetrics() {
        paymentFailures = io.micrometer.core.instrument.Counter.builder("gofp.delivery.payment.failures")
                .description("Deliveries saved as DELIVERED/AT_RELAY_POINT whose payment then failed (unpaid)")
                .register(meterRegistry);
    }

    /** Placeholder driver id used for blockchain anchors when the delivery has no assigned person. */
    private static final UUID UNKNOWN_DRIVER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /**
     * Moves a delivery through its lifecycle.
     *
     * <p>{@code tnt-delivery-core} is the single authority on the lifecycle: every transition is first
     * applied through {@link IDeliveryLifecyclePort}, whose state machine refuses an illegal move
     * (the error propagates and nothing is written here) and publishes the mission events that
     * realtime, incident, sales, invoicing and notifications react to. Only after the core accepted
     * it does gofp project the resulting state onto its own row and run its product side-effects:
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
                && (DeliveryStatus.DELIVERED.equals(target) || DeliveryStatus.AT_RELAY_POINT.equals(target));
        if (relayDeposit) {
            return depositAtRelay(tenantId, delivery, core, driver, dto);
        }
        return switch (target) {
            case PICKED_UP -> pickUp(tenantId, delivery, driver, dto);
            case IN_TRANSIT -> startOrResumeTransit(tenantId, delivery, core, driver);
            case DELIVERED -> deliverDirect(tenantId, delivery, core, driver, dto);
            case FAILED -> project(delivery, lifecycle.fail(tenantId, deliveryId, driver,
                    dto.getReason() != null && !dto.getReason().isBlank() ? dto.getReason() : "Reported by the delivery person"));
            case CANCELLED -> project(delivery, lifecycle.cancel(tenantId, deliveryId, driver,
                    dto.getReason() != null && !dto.getReason().isBlank() ? dto.getReason() : "Cancelled by the delivery person"));
            case AT_RELAY_POINT -> Mono.error(new IllegalArgumentException(
                    "relayPointId is required to deposit a parcel at a relay point"));
            default -> Mono.error(new IllegalArgumentException(
                    "Status " + target + " cannot be set directly; it is driven by the delivery lifecycle"));
        };
    }

    /** Applies the state the core returned to the gofp row, then saves it. */
    private Mono<Delivery> project(Delivery delivery, Mono<CoreDeliveryState> coreResult) {
        return coreResult.flatMap(state -> {
            CoreDeliveryProjection.apply(delivery, state);
            return deliveryRepository.save(delivery);
        });
    }

    private Mono<Delivery> pickUp(UUID tenantId, Delivery delivery, UUID driver, DeliveryStatusUpdateDTO dto) {
        UUID deliveryId = delivery.getId();
        if (dto.getConfirmationCode() == null || dto.getConfirmationCode().isBlank()) {
            return Mono.error(new IllegalArgumentException(
                    "Un code de confirmation est requis pour confirmer la prise en charge du colis (PICKED_UP). " +
                    "Demandez le code à l'expéditeur."));
        }
        if (!otpService.verifyOtp(dto.getConfirmationCode(), delivery.getPickupOtpHash())) {
            return Mono.error(new IllegalArgumentException(
                    "Code de confirmation incorrect. Vérifiez le code auprès de l'expéditeur."));
        }
        log.info("Pickup OTP verified for delivery {} — confirming pickup in delivery-core", deliveryId);
        return project(delivery, lifecycle.confirmPickup(tenantId, deliveryId, driver))
                .flatMap(saved -> anchorPickupFromSender(saved, deliveryId, driver).thenReturn(saved));
    }

    /** Blockchain: PICKUP_FROM_SENDER, certified by the OTP (best-effort, ADR-018). */
    private Mono<Void> anchorPickupFromSender(Delivery delivery, UUID deliveryId, UUID driver) {
        Instant pickupTime = delivery.getActualPickupTime() != null ? delivery.getActualPickupTime() : Instant.now();
        UUID packetId = delivery.getDeliveryNeedId() != null ? delivery.getDeliveryNeedId() : deliveryId;
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
        boolean resume = DeliveryStatus.AT_RELAY_POINT.name().equals(before.status());
        return project(delivery, lifecycle.startOrResumeTransit(tenantId, deliveryId, driver))
                .flatMap(saved -> resume ? Mono.just(saved)
                        : anchorMissionCreated(deliveryId, driver).thenReturn(saved));
    }

    /**
     * The core refuses {@code PICKED_UP → DELIVERED} and {@code PICKED_UP → AT_RELAY_POINT}: a parcel
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
            return Mono.error(new IllegalArgumentException(
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
                        .thenReturn(saved));
    }

    private Mono<Delivery> depositAtRelay(UUID tenantId, Delivery delivery, CoreDeliveryState core, UUID driver,
                                          DeliveryStatusUpdateDTO dto) {
        UUID deliveryId = delivery.getId();
        if (dto.getClientId() == null) {
            return Mono.error(new IllegalArgumentException("clientId is required when delivering to a relay point"));
        }
        UUID packetId = delivery.getDeliveryNeedId() != null ? delivery.getDeliveryNeedId() : deliveryId;
        return resolveHubId(dto.getRelayPointId()).flatMap(hubId ->
                // The core refuses the deposit unless the parcel is in transit; nothing is written then.
                project(delivery, ensureInTransit(tenantId, deliveryId, core, driver)
                        .then(Mono.defer(() -> lifecycle.depositAtRelayPoint(tenantId, deliveryId, driver, hubId))))
                        .flatMap(saved -> {
                            Mono<Void> createDeposit = relayDepositService
                                    .createRelayDeposit(packetId, dto.getClientId(), hubId, dto.getStorageFee(), driver)
                                    .doOnSuccess(deposit -> log.info("RelayDeposit mirror {} for delivery {} hub={}",
                                            deposit.getId(), deliveryId, hubId))
                                    .flatMap(deposit -> notifyRelayDeposit(hubId, dto.getClientId(), packetId,
                                            delivery.getDeliveryNeedId()))
                                    .then();
                            return Mono.when(createDeposit,
                                            processDeliveryPayment(saved, driver, tenantId),
                                            anchorMissionCompleted(deliveryId, driver),
                                            anchorCustodyTransferToHub(deliveryId, driver, packetId, hubId))
                                    .thenReturn(saved);
                        }));
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

    private Mono<UUID> resolveHubId(UUID relayPointId) {
        return gofpRelayPointRepository.findByCoreRelayPointId(relayPointId)
                .switchIfEmpty(gofpRelayPointRepository.findById(relayPointId))
                .map(GofpRelayPoint::getCoreRelayPointId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "No GofpRelayPoint found for id/coreRelayPointId: " + relayPointId)));
    }

    private Mono<Void> notifyRelayDeposit(UUID hubId, UUID clientId, UUID packetId, UUID deliveryNeedId) {
        Mono<GofpRelayPoint> rpMono = gofpRelayPointRepository.findByCoreRelayPointId(hubId)
                .switchIfEmpty(gofpRelayPointRepository.findById(hubId));

        Mono<Integer> daysMono = deliveryNeedId == null
                ? Mono.just(3)
                : deliveryNeedRepository.findById(deliveryNeedId)
                .map(need -> need.getRequestedStorageDays() != null ? need.getRequestedStorageDays() : 3)
                .defaultIfEmpty(3);

        return rpMono.zipWith(daysMono)
                .flatMap(tuple -> {
                    GofpRelayPoint rp = tuple.getT1();
                    int storageDays = tuple.getT2();
                    String rpName = rp.getName() != null && !rp.getName().isBlank()
                            ? rp.getName() : "Point Relais";

                    Mono<Void> notifyRelay = Mono.empty();
                    if (rp.getCoreFreelancerId() != null) {
                        notifyRelay = pushNotificationPort.sendPushNotification(
                                rp.getCoreFreelancerId(),
                                "Nouveau Dépôt",
                                "Un nouveau colis a été déposé dans votre point relais « "
                                        + rpName + " ». Suivi : " + packetId);
                    } else {
                        log.warn("Relay hub {} has no coreFreelancerId — skip RP push", hubId);
                    }

                    Mono<Void> notifyClient = pushNotificationPort.sendPushNotification(
                            clientId,
                            "Colis Arrivé !",
                            "Votre colis est arrivé au point relais " + rpName
                                    + ". Il sera gardé pendant " + storageDays
                                    + " jours sans frais de pénalité.");

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

        return paymentMethodMono.flatMap(paymentMethod -> {
            log.info("Payment method for delivery {} is {}", delivery.getId(), paymentMethod);
            java.math.BigDecimal amount = java.math.BigDecimal.valueOf(delivery.getTarif())
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            java.math.BigDecimal rate = DeliveryPaymentApplicationService.COMMISSION_RATE;

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
}
