package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.InitiatePaymentCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.SplitMissionRevenueCommand;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordCustodyTransferUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordMissionUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordPaymentUseCase;
import com.yowyob.tiibntick.core.trust.domain.model.enums.CustodyTransferType;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryStatusUpdateDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.RelayPointPricingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Application service handling delivery status transitions.
 *
 * <p>Orchestrates three cross-cutting concerns on delivery completion:
 * <ol>
 *   <li><strong>Wallet / Payment</strong> — Initiates payment via {@code tnt-billing-wallet}
 *       and splits revenue between platform, FreelancerOrg, and sub-deliverer.</li>
 *   <li><strong>Blockchain</strong> — Anchors custody transfers and mission completion
 *       on Hyperledger Fabric via {@code tnt-trust-core}.</li>
 *   <li><strong>Notifications</strong> — Sends push notifications to relay point managers
 *       and clients upon deposit.</li>
 * </ol>
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryStatusApplicationService {

    private final DeliveryRepository deliveryRepository;
    private final RelayDepositService relayDepositService;
    private final AdminRelayPointUseCase adminRelayPointUseCase;
    private final PushNotificationPort pushNotificationPort;
    private final RelayPointPricingRepository relayPointPricingRepository;
    private final IDeliveryNeedRepository deliveryNeedRepository;

    // ── OTP ────────────────────────────────────────────────────────────────
    private final OtpService otpService;
    private final DeliveryOtpService deliveryOtpService;

    // ── Wallet & Payment (tnt-billing-wallet) ──────────────────────────
    private final IWalletUseCase walletUseCase;

    // ── Blockchain (tnt-trust-core) ────────────────────────────────────
    private final RecordCustodyTransferUseCase custodyTransferUseCase;
    private final RecordMissionUseCase missionUseCase;
    private final RecordPaymentUseCase paymentUseCase;

    // ── Core Logistics (tnt-delivery-core) ─────────────────────────────
    private final com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase deliveryQueryUseCase;
    private final com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase deliveryLifecycleUseCase;

    /**
     * Updates the status of a delivery and triggers side-effects based on the new status.
     *
     * <p>Side-effects by status:
     * <ul>
     *   <li>{@code IN_TRANSIT} — Anchors mission creation on the blockchain.</li>
     *   <li>{@code DELIVERED} (direct) — Triggers payment + revenue split + blockchain mission completion
     *       + custody transfer to recipient.</li>
     *   <li>{@code DELIVERED} (via relay point) — Creates a RelayDeposit, anchors custody transfer
     *       to hub, sends push notifications, triggers payment.</li>
     * </ul>
     *
     * @param deliveryId UUID of the delivery to update
     * @param dto        DTO containing the new status and optional relay point info
     * @return the updated Delivery
     */
    public Mono<Delivery> updateStatus(UUID deliveryId, DeliveryStatusUpdateDTO dto) {
        return deliveryRepository.findById(deliveryId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Delivery not found: " + deliveryId)))
                // ── Lazy OTP init: generate and send codes if not yet done ──────────
                .flatMap(deliveryOtpService::initOtpIfAbsent)
                .flatMap(delivery -> {
                    // ── OTP validation for PICKED_UP ─────────────────────────────────
                    if (DeliveryStatus.PICKED_UP.equals(dto.getStatus())) {
                        if (dto.getConfirmationCode() == null || dto.getConfirmationCode().isBlank()) {
                            return Mono.error(new IllegalArgumentException(
                                    "Un code de confirmation est requis pour confirmer la prise en charge du colis (PICKED_UP). " +
                                    "Demandez le code à l'expéditeur."));
                        }
                        if (!otpService.verifyOtp(dto.getConfirmationCode(), delivery.getPickupOtpHash())) {
                            return Mono.error(new IllegalArgumentException(
                                    "Code de confirmation incorrect. Vérifiez le code auprès de l'expéditeur."));
                        }
                        log.info("Pickup OTP verified for delivery {} — setting PICKED_UP", deliveryId);
                        Instant pickupTime = Instant.now();
                        delivery.setStatus(DeliveryStatus.PICKED_UP);
                        delivery.setActualPickupTime(pickupTime);

                        // ── Blockchain: PICKUP_FROM_SENDER ─────────────────────────
                        // Triggered only after OTP validation → certifies that the shipper
                        // was physically present and handed the parcel to the delivery person.
                        UUID packetIdForPickup = delivery.getDeliveryNeedId() != null
                                ? delivery.getDeliveryNeedId()
                                : deliveryId;

                        UUID freelancerIdForPickup = delivery.getFreelancerId() != null
                                ? delivery.getFreelancerId()
                                : UUID.fromString("00000000-0000-0000-0000-000000000002");

                        LocalDateTime pickupLdt = LocalDateTime.ofInstant(pickupTime,
                                java.time.ZoneOffset.UTC);

                        // Compute PoC hash: certifies the exact content of this transfer
                        String pocHash = CustodyTransferRecord.computePocHash(
                                packetIdForPickup.toString(),
                                null,                                  // from: sender (anonymous at this point)
                                freelancerIdForPickup.toString(),      // to: delivery person
                                CustodyTransferType.PICKUP_FROM_SENDER.name(),
                                pickupLdt.toString(),
                                null, null                             // GPS not available here
                        );

                        CustodyTransferRecord pickupRecord = new CustodyTransferRecord(
                                UUID.randomUUID().toString(),
                                packetIdForPickup.toString(),
                                null,
                                "default",
                                null,                                  // from: sender (no actor ID)
                                freelancerIdForPickup.toString(),      // to: delivery person
                                CustodyTransferType.PICKUP_FROM_SENDER,
                                null,
                                pickupLdt
                        );
                        pickupRecord.setPocHash(pocHash);

                        Mono<Void> blockchainPickup = custodyTransferUseCase.record(pickupRecord)
                                .doOnSuccess(txHash -> log.info(
                                        "Blockchain: custody PICKUP_FROM_SENDER anchored (OTP-certified) — " +
                                        "delivery={}, freelancer={}, pocHash={}, txHash={}",
                                        deliveryId, freelancerIdForPickup, pocHash, txHash))
                                .onErrorResume(e -> {
                                    log.error("Blockchain: failed to anchor PICKUP_FROM_SENDER for delivery {}",
                                            deliveryId, e);
                                    return Mono.empty();
                                })
                                .then();

                        return deliveryRepository.save(delivery)
                                .flatMap(saved -> blockchainPickup.thenReturn(saved));
                    }

                    log.info("Updating delivery {} status to {} via core port", deliveryId, dto.getStatus());

                    UUID tenantId = TenantContextHolder.SYSTEM_TENANT;
                    
                    return deliveryQueryUseCase.findDeliveryById(tenantId, deliveryId)
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("Core delivery not found: " + deliveryId)))
                            .flatMap(coreDelivery -> {
                                Mono<Delivery> saveDelivery = deliveryRepository.save(delivery); // Save local changes
                                
                                UUID freelancerId = coreDelivery.getDeliveryPersonId() != null 
                                        ? coreDelivery.getDeliveryPersonId() 
                                        : UUID.fromString("00000000-0000-0000-0000-000000000002"); // Fallback

                                // ═══════════════════════════════════════════════════════════
                                // 1. IN_TRANSIT → Ancrer la création de mission sur la blockchain
                                // ═══════════════════════════════════════════════════════════
                                if (DeliveryStatus.IN_TRANSIT.equals(dto.getStatus())) {
                                    Mono<Void> blockchainMissionCreated = missionUseCase.recordCreated(
                                                    deliveryId.toString(),
                                                    freelancerId.toString(),
                                                    "default",  // tenantId
                                                    1            // packageCount
                                            )
                                            .doOnSuccess(txHash -> log.info("Blockchain: mission CREATED anchored for delivery {} — txHash={}",
                                                    deliveryId, txHash))
                                            .onErrorResume(e -> {
                                                log.error("Blockchain: failed to anchor mission CREATED for delivery {}", deliveryId, e);
                                                return Mono.empty();
                                            })
                                            .then();

                                    return saveDelivery.flatMap(saved -> blockchainMissionCreated.thenReturn(saved));
                                }

                                // ═══════════════════════════════════════════════════════════
                                // 2. DELIVERED → Paiement + Blockchain + Notifications
                                // ═══════════════════════════════════════════════════════════
                                if (DeliveryStatus.DELIVERED.equals(dto.getStatus())) {

                                    // ── 2a. Paiement via Wallet ────────────────────────────
                                    Mono<Void> processPayment = processDeliveryPayment(delivery, freelancerId)
                                            .onErrorResume(e -> {
                                                log.error("Payment processing failed for delivery {}", deliveryId, e);
                                                return Mono.empty();
                                            });

                                    // ── 2b. Blockchain: mission COMPLETED ──────────────────
                                    Mono<Void> blockchainMissionCompleted = missionUseCase.recordCompleted(
                                                    deliveryId.toString(),
                                                    freelancerId.toString(),
                                                    "default"
                                            )
                                            .doOnSuccess(txHash -> log.info("Blockchain: mission COMPLETED anchored for delivery {} — txHash={}",
                                                    deliveryId, txHash))
                                            .onErrorResume(e -> {
                                                log.error("Blockchain: failed to anchor mission COMPLETED for delivery {}", deliveryId, e);
                                                return Mono.empty();
                                            })
                                            .then();

                                    // ── 2c. Si livré en point-relais → dépôt + custody blockchain ──
                                    if (dto.getRelayPointId() != null) {

                                        if (dto.getClientId() == null) {
                                            return Mono.error(new IllegalArgumentException(
                                                    "clientId is required when delivering to a relay point"));
                                        }

                                        UUID packetId = delivery.getDeliveryNeedId() != null
                                                ? delivery.getDeliveryNeedId()
                                                : deliveryId;

                                        // Blockchain: custody transfer → TRANSFER_TO_HUB
                                        Mono<Void> blockchainCustody = anchorCustodyTransferToHub(
                                                deliveryId, freelancerId, packetId, dto.getRelayPointId());

                                        // Créer le RelayDeposit + notifications
                                        Mono<Void> createDeposit = relayDepositService
                                                .createRelayDeposit(packetId, dto.getClientId(), dto.getRelayPointId(), dto.getStorageFee())
                                                .doOnSuccess(deposit ->
                                                        log.info("RelayDeposit {} created for delivery {} at relay point {}",
                                                                deposit.getId(), deliveryId, dto.getRelayPointId()))
                                                .flatMap(deposit ->
                                                        adminRelayPointUseCase.getRelayPointDetails(dto.getRelayPointId())
                                                                .zipWith(
                                                                    deliveryNeedRepository.findById(delivery.getDeliveryNeedId())
                                                                        .map(need -> need.getRequestedStorageDays() != null ? need.getRequestedStorageDays() : 3)
                                                                        .defaultIfEmpty(3)
                                                                )
                                                                .flatMap(tuple -> {
                                                                    var relayPoint = tuple.getT1();
                                                                    var storageDays = tuple.getT2();

                                                                    // Notifier le gérant du point relais
                                                                    Mono<Void> notifyRelay = pushNotificationPort.sendPushNotification(
                                                                            relayPoint.getFreelancerId(),
                                                                            "Nouveau Dépôt",
                                                                            "Un nouveau colis a été déposé dans votre point relais. Numéro de suivi : " + packetId
                                                                    );

                                                                    // Notifier le client final
                                                                    Mono<Void> notifyClient = pushNotificationPort.sendPushNotification(
                                                                            dto.getClientId(),
                                                                            "Colis Arrivé !",
                                                                            "Votre colis est arrivé au point relais " + relayPoint.getName() + ". Conformément à votre demande, il sera gardé pendant " + storageDays + " jours sans frais de pénalité."
                                                                    );

                                                                    return Mono.when(notifyRelay, notifyClient);
                                                                })
                                                                .onErrorResume(e -> {
                                                                    log.error("Failed to send notifications upon deposit", e);
                                                                    return Mono.empty();
                                                                })
                                                )
                                                .then();

                                        return saveDelivery.flatMap(saved ->
                                                Mono.when(createDeposit, processPayment, blockchainMissionCompleted, blockchainCustody)
                                                        .thenReturn(saved));
                                    }

                                    // ── 2d. Livraison directe (sans point-relais) ──────────
                                    // OTP validation: the delivery person must provide the code given by the recipient
                                    if (dto.getConfirmationCode() == null || dto.getConfirmationCode().isBlank()) {
                                        return Mono.error(new IllegalArgumentException(
                                                "Un code de confirmation est requis pour confirmer la livraison directe (DELIVERED). " +
                                                "Demandez le code au destinataire."));
                                    }
                                    if (!otpService.verifyOtp(dto.getConfirmationCode(), delivery.getDeliveryOtpHash())) {
                                        return Mono.error(new IllegalArgumentException(
                                                "Code de confirmation incorrect. Vérifiez le code auprès du destinataire."));
                                    }
                                    log.info("Delivery OTP verified for delivery {} — setting DELIVERED (direct)", deliveryId);
                                    Instant deliveryTime = Instant.now();
                                    delivery.setActualDeliveryTime(deliveryTime);

                                    // ── Blockchain: TRANSFER_TO_RECIPIENT (OTP-certified) ──
                                    // Unlike the non-OTP path, this record is anchored AFTER
                                    // the recipient has confirmed receipt — making it a
                                    // certified proof of delivery, not a unilateral declaration.
                                    Mono<Void> blockchainCustodyDirect = anchorOtpCertifiedDelivery(
                                            deliveryId, freelancerId, deliveryTime);

                                    return saveDelivery.flatMap(saved ->
                                            Mono.when(processPayment, blockchainMissionCompleted, blockchainCustodyDirect)
                                                    .thenReturn(saved));
                                }

                                return saveDelivery;
                            });
                });
    }

    // ══════════════════════════════════════════════════════════════════════
    // OTP initialisation (public, callable from controller)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Explicitly initialises OTP codes for the given delivery.
     * Idempotent: if codes already exist, returns the delivery unchanged.
     */
    public Mono<Delivery> initOtpForDelivery(UUID deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Delivery not found: " + deliveryId)))
                .flatMap(deliveryOtpService::initOtpIfAbsent);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════════════════

    private Mono<Void> processDeliveryPayment(Delivery delivery, UUID freelancerId) {
        if (delivery.getTarif() == null || delivery.getTarif() <= 0) {
            log.warn("Delivery {} has no tarif set — skipping payment processing", delivery.getId());
            return Mono.empty();
        }

        log.info("Processing payment for delivery {} — amount: {} XAF", delivery.getId(), delivery.getTarif());

        // 1. Retrieve the payment method from DeliveryNeed or Announcement
        Mono<String> paymentMethodMono = Mono.empty();
        if (delivery.getDeliveryNeedId() != null) {
            paymentMethodMono = deliveryNeedRepository.findById(delivery.getDeliveryNeedId())
                    .map(need -> need.getPaymentMethod() != null ? need.getPaymentMethod() : "UNKNOWN");
        } else if (delivery.getAnnouncementId() != null) {
            // Note: If you have an AnnouncementRepository injected, use it here.
            // For now, we default to digital if we can't find it.
            paymentMethodMono = Mono.just("ORANGE_MONEY"); 
        } else {
            paymentMethodMono = Mono.just("UNKNOWN");
        }

        return paymentMethodMono.flatMap(paymentMethod -> {
            log.info("Payment method for delivery {} is {}", delivery.getId(), paymentMethod);
            
            Mono<Void> walletAction;
            double platformCommissionAmount = delivery.getTarif() * 0.05; // 5% commission

            if ("CASH".equalsIgnoreCase(paymentMethod)) {
                // Freelancer collected physical cash. We must DEBIT their wallet for the platform commission.
                walletAction = walletUseCase.debitWallet(
                        new com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.DebitWalletCommand(
                                freelancerId,
                                TenantContextHolder.SYSTEM_TENANT,
                                com.yowyob.tiibntick.core.billing.wallet.domain.model.Money.of(java.math.BigDecimal.valueOf(platformCommissionAmount), "XAF"),
                                delivery.getId().toString(),
                                com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel.CASH_ON_DELIVERY,
                                "Commission logicielle (5%) pour la course payée en espèces : " + delivery.getId(),
                                java.util.UUID.randomUUID().toString()
                        )
                ).doOnSuccess(tx -> log.info("Debited commission {} from freelancer {} for CASH delivery", 
                        platformCommissionAmount, freelancerId)).then();
            } else {
                // Digital payments (ORANGE_MONEY, MTN_MOBILE_MONEY, CARD, etc.)
                // The platform holds the funds, so we split the revenue and credit the freelancer.
                walletAction = walletUseCase.splitMissionRevenue(
                                new SplitMissionRevenueCommand(
                                        delivery.getId().toString(),
                                        java.math.BigDecimal.valueOf(delivery.getTarif()),
                                        freelancerId.toString(),
                                        TenantContextHolder.SYSTEM_TENANT,
                                        null,
                                        0.05,
                                        0.0
                                )
                        )
                        .doOnSuccess(split -> log.info("Revenue split executed for delivery {} (Method: {}) — platform: {}, org: {}",
                                delivery.getId(), paymentMethod, split.platformCommission(), split.orgRevenue()))
                        .then();
            }

            // Record the payment action on the blockchain for traceability
            Mono<Void> anchorPayment = paymentUseCase.record(
                            delivery.getId().toString(),
                            freelancerId.toString(),
                            freelancerId.toString(),
                            "default",
                            "CASH".equalsIgnoreCase(paymentMethod) ? "CASH_COLLECTED_COMMISSION_DEBITED" : "DIGITAL_WALLET_SPLIT",
                            delivery.getId().toString(),
                            String.valueOf(delivery.getTarif()),
                            "XAF"
                    )
                    .doOnSuccess(txHash -> log.info("Blockchain: payment anchored for delivery {} — txHash={}",
                            delivery.getId(), txHash))
                    .onErrorResume(e -> {
                        log.error("Blockchain: failed to anchor payment for delivery {}", delivery.getId(), e);
                        return Mono.empty();
                    })
                    .then();

            return Mono.when(walletAction, anchorPayment);
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
                .onErrorResume(e -> {
                    log.error("Blockchain: failed to anchor custody transfer to hub for delivery {}", deliveryId, e);
                    return Mono.empty();
                })
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
                .onErrorResume(e -> {
                    log.error("Blockchain: failed to anchor custody transfer to recipient for delivery {}", deliveryId, e);
                    return Mono.empty();
                })
                .then();
    }

    /**
     * Anchors a TRANSFER_TO_RECIPIENT custody record that is OTP-certified.
     *
     * <p>Unlike {@link #anchorCustodyTransferToRecipient}, this variant:
     * <ul>
     *   <li>Uses the exact {@code actualDeliveryTime} confirmed by the OTP exchange
     *       rather than the current server time — making the timestamp tamper-evident.</li>
     *   <li>Computes and attaches a <strong>Proof of Content (PoC) hash</strong>
     *       that binds package ID, actors, transfer type, and timestamp into a
     *       SHA-256 digest anchored on-chain. Any post-hoc alteration of these
     *       fields will invalidate the PoC and be immediately detectable.</li>
     * </ul>
     *
     * <p>This makes the on-chain record a genuine <em>certified proof of delivery</em>
     * rather than a unilateral declaration by the delivery person.
     *
     * @param deliveryId   UUID of the delivery
     * @param freelancerId UUID of the delivery person handing over the parcel
     * @param deliveryTime the exact instant the OTP was validated (recipient confirmed)
     */
    private Mono<Void> anchorOtpCertifiedDelivery(UUID deliveryId, UUID freelancerId, Instant deliveryTime) {
        LocalDateTime deliveryLdt = LocalDateTime.ofInstant(deliveryTime, java.time.ZoneOffset.UTC);

        // PoC hash: binds packageId + actors + type + OTP-certified timestamp
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
                .onErrorResume(e -> {
                    log.error("Blockchain: failed to anchor OTP-certified delivery for delivery {}",
                            deliveryId, e);
                    return Mono.empty();
                })
                .then();
    }
}
