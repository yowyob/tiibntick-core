package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.DebitWalletCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.SplitMissionRevenueCommand;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CompleteDeliveryCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IRelayHubPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.RelayDepositRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.RelayPointSubscriptionRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.logistics.RelayDepositStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointSubscriptionType;
import com.yowyob.tiibntick.core.inventory.application.port.in.DepositHubPackageCommand;
import com.yowyob.tiibntick.core.inventory.application.port.in.DepositHubPackageUseCase;
import com.yowyob.tiibntick.core.inventory.application.port.in.PickupHubPackageUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordCustodyTransferUseCase;
import com.yowyob.tiibntick.core.trust.domain.model.enums.CustodyTransferType;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Product-side mirror of a parcel deposited at a relay point.
 *
 * <p><strong>Source of truth:</strong> stock → {@code tnt-inventory-core}
 * ({@link DepositHubPackageUseCase}), occupancy → {@code tnt-geo-core}
 * ({@link IRelayHubPort}), delivery step → {@code tnt-delivery-core}
 * ({@code depositAtRelayPoint}). This local {@link RelayDeposit} remains as a
 * temporary GOFP UX/billing mirror until full cut-over.</p>
 *
 * @author MANFOUO BRAUN
 * @deprecated Prefer inventory {@code HubPackageEntry} + delivery lifecycle; keep as sync mirror only.
 */
@Deprecated
@Slf4j
@Service
@RequiredArgsConstructor
public class RelayDepositService {

    private final RelayDepositRepository relayDepositRepository;
    private final DeliveryRepository deliveryRepository;
    private final OtpService otpService;
    private final RecordCustodyTransferUseCase custodyTransferUseCase;
    private final NotificationService notificationService;
    private final GofpRelayPointRepository gofpRelayPointRepository;
    private final GofpUserRepository gofpUserRepository;
    private final IWalletUseCase walletUseCase;
    private final IDeliveryNeedRepository deliveryNeedRepository;
    private final RelayPointSubscriptionRepository relayPointSubscriptionRepository;
    private final DepositHubPackageUseCase depositHubPackageUseCase;
    private final PickupHubPackageUseCase pickupHubPackageUseCase;
    private final IRelayHubPort relayHubPort;
    private final DeliveryLifecycleUseCase deliveryLifecycleUseCase;

    /**
     * Creates a product mirror deposit and synchronises inventory + geo occupancy.
     *
     * @param packetId           parcel / delivery-need id
     * @param clientId           client who will pick up
     * @param relayPointId       GofpRelayPoint id <em>or</em> {@code coreRelayPointId} (RelayHub.id)
     * @param storageFee         storage fee
     * @param depositedByActorId delivery person / actor depositing the parcel
     */
    public Mono<RelayDeposit> createRelayDeposit(UUID packetId,
                                                  UUID clientId,
                                                  UUID relayPointId,
                                                  Double storageFee,
                                                  UUID depositedByActorId) {
        UUID tenantId = TenantContextHolder.SYSTEM_TENANT;

        return resolveRelayPoint(relayPointId)
                .flatMap(rp -> {
                    UUID hubId = rp.getCoreRelayPointId();
                    RelayDeposit deposit = RelayDeposit.builder()
                            .id(UUID.randomUUID())
                            .packetId(packetId)
                            .clientId(clientId)
                            .relayPointId(hubId)
                            .storageFee(storageFee != null ? storageFee : 0.0)
                            .currency("XAF")
                            .status(RelayDepositStatus.DELIVERED)
                            .createdAt(Instant.now())
                            .build();

                    log.info("Creating RelayDeposit (mirror) packet={} hub={} client={}",
                            packetId, hubId, clientId);

                    Mono<Void> inventoryDeposit = depositHubPackageUseCase.depositPackage(
                                    new DepositHubPackageCommand(
                                            tenantId,
                                            hubId,
                                            packetId,
                                            packetId.toString(),
                                            null,
                                            depositedByActorId,
                                            null))
                            .doOnSuccess(e -> log.info("Inventory HubPackageEntry {} deposited at hub={}",
                                    e.id(), hubId))
                            .doOnError(e -> log.error(
                                    "Inventory deposit FAILED for packet={} hub={} — not masking as success",
                                    packetId, hubId, e))
                            .then();

                    // Soft: geo hub may be missing during migration — warn, don't invent success occupancy
                    Mono<Void> geoOccupancy = relayHubPort.incrementOccupancy(hubId, tenantId)
                            .doOnError(e -> log.error(
                                    "Geo occupancy increment FAILED for hub={} — propagating", hubId, e))
                            .then()
                            .onErrorResume(e -> {
                                // If hub simply not provisioned in geo yet, continue with inventory+mirror
                                if (e instanceof IllegalStateException
                                        && e.getMessage() != null
                                        && e.getMessage().contains("not found")) {
                                    log.warn("RelayHub {} absent in geo-core — skipping occupancy (migration)", hubId);
                                    return Mono.empty();
                                }
                                return Mono.error(e);
                            });

                    return Mono.when(inventoryDeposit, geoOccupancy)
                            .then(relayDepositRepository.save(deposit))
                            .flatMap(saved -> bumpDepositCounters(rp).thenReturn(saved));
                });
    }

    /** Backward-compatible overload (no actor id). */
    public Mono<RelayDeposit> createRelayDeposit(UUID packetId,
                                                  UUID clientId,
                                                  UUID relayPointId,
                                                  Double storageFee) {
        return createRelayDeposit(packetId, clientId, relayPointId, storageFee, null);
    }

    public Flux<RelayDeposit> getDepositsByRelayPointId(UUID relayPointId) {
        return relayDepositRepository.findByRelayPointId(relayPointId);
    }

    public Flux<RelayDeposit> getDepositsByClientId(UUID clientId) {
        return relayDepositRepository.findByClientId(clientId);
    }

    /**
     * Marks a relay deposit as RETRIEVED.
     *
     * <p><strong>Ordering matters:</strong> the parcel is only persisted as RETRIEVED
     * once the steps that represent an irreversible, real-world/system-of-record fact
     * have all succeeded — physical hand-over is confirmed by OTP, {@code tnt-inventory-core}
     * stock is decremented, and {@code tnt-delivery-core} is transitioned to
     * {@code DELIVERED} (closing the loop with the authoritative delivery state machine).
     * If any of those fail, nothing is persisted and the caller correctly sees an error.
     *
     * <p>Everything after that point — storage-fee collection, blockchain custody
     * anchoring, evaluation notifications — is best-effort: the retrieval already
     * genuinely happened, so a wallet/blockchain/notification hiccup must not turn an
     * already-successful retrieval into an API error for the courier (same ADR-018
     * "trust-anchoring never blocks" principle, extended here to the whole post-retrieval
     * tail). Failures are logged loudly for manual reconciliation, never silently dropped.
     */
    public Mono<RelayDeposit> markAsRetrieved(UUID id, String otpCode) {
        UUID tenantId = TenantContextHolder.SYSTEM_TENANT;

        return relayDepositRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("RelayDeposit not found: " + id)))
                .flatMap(deposit -> {
                    if (otpCode == null || otpCode.isBlank()) {
                        return Mono.error(new IllegalArgumentException(
                                "Un code OTP est requis pour récupérer le colis."));
                    }

                    return deliveryRepository.findByDeliveryNeedId(deposit.getPacketId())
                            .switchIfEmpty(deliveryRepository.findByAnnouncementId(deposit.getPacketId()))
                            .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                    "No delivery found for packet: " + deposit.getPacketId())))
                            .flatMap(delivery -> {
                                if (!otpService.verifyOtp(otpCode, delivery.getDeliveryOtpHash())) {
                                    return Mono.error(new IllegalArgumentException(
                                            "Code OTP incorrect pour le retrait."));
                                }

                                Instant retrievedTime = Instant.now();
                                deposit.setStatus(RelayDepositStatus.RETRIEVED);
                                deposit.setRetrievedAt(retrievedTime);

                                // ── Irreversible / system-of-record steps — hard-fail ────────
                                Mono<Void> inventoryPickup = pickupHubPackageUseCase
                                        .pickupPackage(deposit.getPacketId().toString(), deposit.getClientId())
                                        .doOnError(e -> log.error(
                                                "Inventory pickup FAILED for packet={} — propagating",
                                                deposit.getPacketId(), e));

                                Mono<Void> coreComplete = deliveryLifecycleUseCase.completeDelivery(
                                                new CompleteDeliveryCommand(tenantId, delivery.getId(),
                                                        delivery.getFreelancerId(), null, null, null, null, null))
                                        .doOnSuccess(d -> log.info(
                                                "delivery-core completeDelivery (relay retrieval) ok delivery={}",
                                                delivery.getId()))
                                        .doOnError(e -> log.error(
                                                "delivery-core completeDelivery FAILED delivery={} — propagating",
                                                delivery.getId(), e))
                                        .then();

                                // Geo occupancy stays soft: an under/over-counted hub must never
                                // block a confirmed, OTP-certified physical retrieval.
                                Mono<Void> geoDec = relayHubPort
                                        .decrementOccupancy(deposit.getRelayPointId(), tenantId)
                                        .doOnError(e -> log.warn(
                                                "Geo occupancy decrement FAILED hub={} — soft-fail, not propagating",
                                                deposit.getRelayPointId(), e))
                                        .then()
                                        .onErrorResume(e -> Mono.empty());

                                return Mono.when(inventoryPickup, coreComplete, geoDec)
                                        .then(relayDepositRepository.save(deposit))
                                        .flatMap(saved -> postRetrievalBestEffortTail(saved, retrievedTime)
                                                .thenReturn(saved));
                            });
                });
    }

    /**
     * Storage-fee collection + custody blockchain anchoring + evaluation notifications.
     * Runs only after the deposit is durably RETRIEVED — see {@link #markAsRetrieved}.
     * Best-effort: failures are logged at ERROR for reconciliation but never propagate.
     */
    private Mono<Void> postRetrievalBestEffortTail(RelayDeposit saved, Instant retrievedTime) {
        LocalDateTime retrievedLdt = LocalDateTime.ofInstant(retrievedTime, java.time.ZoneOffset.UTC);

        String pocHash = CustodyTransferRecord.computePocHash(
                saved.getPacketId().toString(),
                saved.getRelayPointId().toString(),
                saved.getClientId().toString(),
                CustodyTransferType.TRANSFER_TO_RECIPIENT.name(),
                retrievedLdt.toString(),
                null, null);

        CustodyTransferRecord record = new CustodyTransferRecord(
                UUID.randomUUID().toString(),
                saved.getPacketId().toString(),
                null,
                "default",
                saved.getRelayPointId().toString(),
                saved.getClientId().toString(),
                CustodyTransferType.TRANSFER_TO_RECIPIENT,
                null,
                retrievedLdt);
        record.setPocHash(pocHash);

        Mono<Void> blockchainRetrieval = custodyTransferUseCase.record(record)
                .doOnSuccess(txHash -> log.info(
                        "Blockchain: Relay deposit RETRIEVED — deposit={}, txHash={}", saved.getId(), txHash))
                .doOnError(e -> log.error(
                        "Blockchain: failed to anchor retrieval for deposit {} — best-effort, not blocking (ADR-018)",
                        saved.getId(), e))
                .onErrorResume(e -> Mono.empty())
                .then();

        Mono<String> paymentMethodMono = deliveryNeedRepository
                .findById(saved.getPacketId())
                .map(need -> need.getPaymentMethod() != null ? need.getPaymentMethod() : "UNKNOWN")
                .defaultIfEmpty("ORANGE_MONEY");

        Mono<Void> payment = paymentMethodMono
                .flatMap(paymentMethod -> processRelayPayment(saved, paymentMethod))
                .doOnError(e -> log.error(
                        "Storage-fee payment FAILED for already-retrieved deposit {} — "
                                + "requires manual reconciliation, not blocking the retrieval response",
                        saved.getId(), e))
                .onErrorResume(e -> Mono.empty());

        return Mono.when(blockchainRetrieval, payment)
                .then(triggerEvaluationNotifications(saved));
    }

    private Mono<GofpRelayPoint> resolveRelayPoint(UUID relayPointId) {
        return gofpRelayPointRepository.findByCoreRelayPointId(relayPointId)
                .switchIfEmpty(gofpRelayPointRepository.findById(relayPointId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "GofpRelayPoint not found for id/coreId: " + relayPointId)));
    }

    private Mono<Void> bumpDepositCounters(GofpRelayPoint rp) {
        rp.setTotalDeposits(rp.getTotalDeposits() == null ? 1 : rp.getTotalDeposits() + 1);
        rp.setDepositsUsed(rp.getDepositsUsed() == null ? 1 : rp.getDepositsUsed() + 1);
        rp.setUpdatedAt(Instant.now());
        return gofpRelayPointRepository.save(rp).then();
    }

    private Mono<Void> processRelayPayment(RelayDeposit deposit, String paymentMethod) {
        if (deposit.getStorageFee() == null || deposit.getStorageFee() <= 0) {
            return Mono.empty();
        }

        return gofpRelayPointRepository.findByCoreRelayPointId(deposit.getRelayPointId())
                .switchIfEmpty(gofpRelayPointRepository.findById(deposit.getRelayPointId()))
                .flatMap(rp -> {
                    if (rp.getCoreFreelancerId() == null) {
                        log.error("Relay point {} has no freelancer owner — cannot process storage fee",
                                rp.getId());
                        return Mono.error(new IllegalStateException(
                                "Relay point owner missing for wallet split: " + rp.getId()));
                    }

                    UUID ownerId = rp.getCoreFreelancerId();

                    return relayPointSubscriptionRepository.findByRelayPointId(rp.getCoreRelayPointId())
                            .map(sub -> sub.getSubscriptionType().getCommissionPercent() / 100.0)
                            .defaultIfEmpty(RelayPointSubscriptionType.BASIC.getCommissionPercent() / 100.0)
                            .flatMap(commissionRate -> {
                                double platformCommission = deposit.getStorageFee() * commissionRate;

                                if ("CASH".equalsIgnoreCase(paymentMethod)) {
                                    return walletUseCase.debitWallet(
                                            new DebitWalletCommand(
                                                    ownerId,
                                                    TenantContextHolder.SYSTEM_TENANT,
                                                    Money.of(java.math.BigDecimal.valueOf(platformCommission),
                                                            deposit.getCurrency() != null
                                                                    ? deposit.getCurrency() : "XAF"),
                                                    deposit.getId().toString(),
                                                    PaymentChannel.CASH_ON_DELIVERY,
                                                    "Commission logicielle (" + (commissionRate * 100)
                                                            + "%) frais stockage cash : " + deposit.getId(),
                                                    "relay-storage-fee:" + deposit.getId()
                                            )
                                    ).doOnSuccess(tx -> log.info(
                                            "Debited commission {} from relay owner {}",
                                            platformCommission, ownerId))
                                    .doOnError(e -> log.error(
                                            "Wallet debit FAILED for deposit {} — propagating",
                                            deposit.getId(), e))
                                    .then();
                                }
                                return walletUseCase.splitMissionRevenue(
                                                new SplitMissionRevenueCommand(
                                                        deposit.getId().toString(),
                                                        java.math.BigDecimal.valueOf(deposit.getStorageFee()),
                                                        ownerId.toString(),
                                                        TenantContextHolder.SYSTEM_TENANT,
                                                        null,
                                                        commissionRate,
                                                        0.0
                                                )
                                        )
                                        .doOnSuccess(split -> log.info(
                                                "Revenue split storage fee {} — platform={}, owner={}",
                                                deposit.getId(), split.platformCommission(), split.orgRevenue()))
                                        .doOnError(e -> log.error(
                                                "Wallet split FAILED for deposit {} — propagating",
                                                deposit.getId(), e))
                                        .then();
                            });
                });
    }

    private Mono<Void> triggerEvaluationNotifications(RelayDeposit deposit) {
        Mono<GofpUser> clientMono = gofpUserRepository.findByCoreUserId(deposit.getClientId())
                .switchIfEmpty(gofpUserRepository.findById(deposit.getClientId()));

        Mono<GofpRelayPoint> rpMono = gofpRelayPointRepository.findByCoreRelayPointId(deposit.getRelayPointId())
                .switchIfEmpty(gofpRelayPointRepository.findById(deposit.getRelayPointId()));

        return Mono.zip(clientMono.defaultIfEmpty(new GofpUser()), rpMono.defaultIfEmpty(new GofpRelayPoint()))
                .flatMap(tuple -> {
                    GofpUser client = tuple.getT1();
                    GofpRelayPoint rp = tuple.getT2();

                    String rpName = (rp.getName() != null && !rp.getName().isBlank())
                            ? rp.getName() : "Point Relais";

                    Mono<Void> clientNotif = Mono.empty();
                    UUID clientPersonId = client.getCoreUserId() != null ? client.getCoreUserId() : client.getId();
                    if (clientPersonId != null) {
                        clientNotif = notificationService.sendEvaluationRequest(
                                clientPersonId,
                                deposit.getPacketId(),
                                client.getEmail(),
                                "Colis récupéré — Évaluez le Point Relais !",
                                "Vous avez récupéré votre colis au " + rpName
                                        + ". Merci de donner votre avis et de noter le point relais.");
                    }

                    Mono<Void> rpNotif = Mono.empty();
                    if (rp.getCoreFreelancerId() != null) {
                        rpNotif = notificationService.sendEvaluationRequest(
                                rp.getCoreFreelancerId(),
                                deposit.getPacketId(),
                                rp.getOwnerEmail(),
                                "Retrait effectué — Évaluez le client !",
                                "Le colis a été remis au client. Prenez un instant pour évaluer votre interaction.");
                    }

                    return Mono.when(clientNotif, rpNotif)
                            .doOnError(e -> log.error(
                                    "Failed evaluation notifications after retrieval deposit={} — best-effort",
                                    deposit.getId(), e))
                            .onErrorResume(e -> Mono.empty());
                });
    }
}
