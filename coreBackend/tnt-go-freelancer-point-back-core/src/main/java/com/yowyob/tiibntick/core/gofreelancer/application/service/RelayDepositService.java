package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.logistics.RelayDepositStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.RelayDepositRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain service for relay deposit operations.
 * Handles creation, retrieval, and state transitions of relay deposits.
 *
 * @author François-Charles ATANGA
 */
import com.yowyob.tiibntick.core.trust.application.port.in.RecordCustodyTransferUseCase;
import com.yowyob.tiibntick.core.trust.domain.model.enums.CustodyTransferType;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.SplitMissionRevenueCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.DebitWalletCommand;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;


import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.RelayPointSubscriptionRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointSubscriptionType;
import java.time.LocalDateTime;

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

    /**
     * Creates a new RelayDeposit representing a parcel deposited at a relay point.
     * Status is set to DELIVERED (waiting for client pickup).
     *
     * @param packetId     UUID of the parcel
     * @param clientId     UUID of the client who will pick it up
     * @param relayPointId UUID of the relay point
     * @param storageFee   storage fee charged by the relay point
     * @return the persisted RelayDeposit
     */
    public Mono<RelayDeposit> createRelayDeposit(UUID packetId,
                                                  UUID clientId,
                                                  UUID relayPointId,
                                                  Double storageFee) {
        RelayDeposit deposit = RelayDeposit.builder()
                .id(UUID.randomUUID())
                .packetId(packetId)
                .clientId(clientId)
                .relayPointId(relayPointId)
                .storageFee(storageFee != null ? storageFee : 0.0)
                .currency("XAF")
                .status(RelayDepositStatus.DELIVERED)
                .createdAt(Instant.now())
                .build();

        log.info("Creating RelayDeposit for packet={} at relayPoint={} for client={}",
                packetId, relayPointId, clientId);
        return relayDepositRepository.save(deposit);
    }

    public Flux<RelayDeposit> getDepositsByRelayPointId(UUID relayPointId) {
        return relayDepositRepository.findByRelayPointId(relayPointId);
    }

    public Flux<RelayDeposit> getDepositsByClientId(UUID clientId) {
        return relayDepositRepository.findByClientId(clientId);
    }

    /**
     * Marks a relay deposit as RETRIEVED by the client,
     * after validating the OTP and anchoring the custody transfer.
     * Triggers evaluation notifications for both Client and Relay Point.
     */
    public Mono<RelayDeposit> markAsRetrieved(UUID id, String otpCode) {
        return relayDepositRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("RelayDeposit not found: " + id)))
                .flatMap(deposit -> {
                    if (otpCode == null || otpCode.isBlank()) {
                        return Mono.error(new IllegalArgumentException("Un code OTP est requis pour récupérer le colis."));
                    }

                    // 1. Find the associated Delivery to verify OTP
                    return deliveryRepository.findByDeliveryNeedId(deposit.getPacketId())
                            .switchIfEmpty(deliveryRepository.findByAnnouncementId(deposit.getPacketId()))
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("No delivery found for packet: " + deposit.getPacketId())))
                            .flatMap(delivery -> {
                                // 2. Verify OTP
                                if (!otpService.verifyOtp(otpCode, delivery.getDeliveryOtpHash())) {
                                    return Mono.error(new IllegalArgumentException("Code OTP incorrect pour le retrait."));
                                }

                                Instant retrievedTime = Instant.now();
                                deposit.setStatus(RelayDepositStatus.RETRIEVED);
                                deposit.setRetrievedAt(retrievedTime);

                                // 3. Anchor on Blockchain (TRANSFER_TO_RECIPIENT)
                                LocalDateTime retrievedLdt = LocalDateTime.ofInstant(retrievedTime, java.time.ZoneOffset.UTC);
                                
                                String pocHash = CustodyTransferRecord.computePocHash(
                                        deposit.getPacketId().toString(),
                                        deposit.getRelayPointId().toString(), // From Relay Point
                                        deposit.getClientId().toString(),     // To Client
                                        CustodyTransferType.TRANSFER_TO_RECIPIENT.name(),
                                        retrievedLdt.toString(),
                                        null, null
                                );

                                CustodyTransferRecord record = new CustodyTransferRecord(
                                        UUID.randomUUID().toString(),
                                        deposit.getPacketId().toString(),
                                        null,
                                        "default",
                                        deposit.getRelayPointId().toString(),
                                        deposit.getClientId().toString(),
                                        CustodyTransferType.TRANSFER_TO_RECIPIENT,
                                        null,
                                        retrievedLdt
                                );
                                record.setPocHash(pocHash);

                                Mono<Void> blockchainRetrieval = custodyTransferUseCase.record(record)
                                        .doOnSuccess(txHash -> log.info(
                                                "Blockchain: Relay deposit RETRIEVED anchored (OTP-certified) — deposit={}, pocHash={}, txHash={}",
                                                id, pocHash, txHash))
                                        .onErrorResume(e -> {
                                            log.error("Blockchain: failed to anchor retrieval for deposit {}", id, e);
                                            return Mono.empty();
                                        })
                                        .then();

                                // Determine payment method (cash/digital) for the storage fee
                                Mono<String> paymentMethodMono = deliveryNeedRepository.findById(deposit.getPacketId())
                                        .map(need -> need.getPaymentMethod() != null ? need.getPaymentMethod() : "UNKNOWN")
                                        .defaultIfEmpty("ORANGE_MONEY");

                                return relayDepositRepository.save(deposit)
                                    .flatMap(saved -> paymentMethodMono.flatMap(paymentMethod -> {
                                        Mono<Void> processPayment = processRelayPayment(saved, paymentMethod)
                                                .onErrorResume(e -> {
                                                    log.error("Payment processing failed for relay deposit {}", id, e);
                                                    return Mono.empty();
                                                });
                                        return Mono.when(blockchainRetrieval, processPayment)
                                                .then(triggerEvaluationNotifications(saved))
                                                .thenReturn(saved);
                                    }));
                            });
                });
    }

    private Mono<Void> processRelayPayment(RelayDeposit deposit, String paymentMethod) {
        if (deposit.getStorageFee() == null || deposit.getStorageFee() <= 0) {
            return Mono.empty();
        }

        return gofpRelayPointRepository.findByCoreRelayPointId(deposit.getRelayPointId())
                .switchIfEmpty(gofpRelayPointRepository.findById(deposit.getRelayPointId()))
                .flatMap(rp -> {
                    if (rp.getCoreFreelancerId() == null) {
                        log.warn("Relay point {} has no freelancer owner to pay", rp.getId());
                        return Mono.empty();
                    }

                    UUID ownerId = rp.getCoreFreelancerId();
                    
                    return relayPointSubscriptionRepository.findByRelayPointId(rp.getCoreRelayPointId())
                            .map(sub -> sub.getSubscriptionType().getCommissionPercent() / 100.0)
                            .defaultIfEmpty(RelayPointSubscriptionType.BASIC.getCommissionPercent() / 100.0)
                            .flatMap(commissionRate -> {
                                double platformCommission = deposit.getStorageFee() * commissionRate;

                                if ("CASH".equalsIgnoreCase(paymentMethod)) {
                                    // Client pays physical cash at the relay point. Debit the owner's wallet for platform commission.
                                    return walletUseCase.debitWallet(
                                            new DebitWalletCommand(
                                                    ownerId,
                                                    TenantContextHolder.SYSTEM_TENANT,
                                                    Money.of(java.math.BigDecimal.valueOf(platformCommission), deposit.getCurrency() != null ? deposit.getCurrency() : "XAF"),
                                                    deposit.getId().toString(),
                                                    PaymentChannel.CASH_ON_DELIVERY,
                                                    "Commission logicielle (" + (commissionRate * 100) + "%) pour frais de stockage payés en espèces : " + deposit.getId(),
                                                    UUID.randomUUID().toString()
                                            )
                                    ).doOnSuccess(tx -> log.info("Debited commission {} from relay owner {} for CASH storage fee",
                                            platformCommission, ownerId)).then();
                                } else {
                                    // Digital payment. Split the revenue and credit the relay point owner.
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
                                    ).doOnSuccess(split -> log.info("Revenue split executed for storage fee {} (Method: {}) — platform: {}, rpOwner: {}",
                                            deposit.getId(), paymentMethod, split.platformCommission(), split.orgRevenue())).then();
                                }
                            });
                });
    }

    private Mono<Void> triggerEvaluationNotifications(RelayDeposit deposit) {
        // Fetch Client info
        Mono<GofpUser> clientMono = gofpUserRepository.findByCoreUserId(deposit.getClientId())
                .switchIfEmpty(gofpUserRepository.findById(deposit.getClientId()));

        // Fetch Relay Point info
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
                                "Vous avez récupéré votre colis au " + rpName + ". Merci de donner votre avis et de noter le point relais."
                        );
                    }

                    Mono<Void> rpNotif = Mono.empty();
                    if (rp.getCoreFreelancerId() != null) {
                        rpNotif = notificationService.sendEvaluationRequest(
                                rp.getCoreFreelancerId(),
                                deposit.getPacketId(),
                                rp.getOwnerEmail(),
                                "Retrait effectué — Évaluez le client !",
                                "Le colis a été remis au client. Prenez un instant pour évaluer votre interaction avec le client."
                        );
                    }

                    return Mono.when(clientNotif, rpNotif).onErrorResume(e -> {
                        log.error("Failed to send evaluation notifications after parcel retrieval for deposit {}", deposit.getId(), e);
                        return Mono.empty();
                    });
                });
    }
}
