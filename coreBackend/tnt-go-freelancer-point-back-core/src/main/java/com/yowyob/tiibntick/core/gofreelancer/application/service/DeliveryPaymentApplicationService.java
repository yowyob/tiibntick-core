package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IKernelWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ISettlementUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.RecordSettlementCommand;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementMethod;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.PaymentChallengeResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.PaymentStatusResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos.ReceiveCodeResponse;
import com.yowyob.tiibntick.core.gofreelancer.application.exception.DeliveryPaymentException;
import com.yowyob.tiibntick.core.gofreelancer.application.exception.DeliveryPaymentException.Reason;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryPaymentUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryPaymentApplicationService implements DeliveryPaymentUseCase {

    /** Settlement reference type, shared with the incident freeze adapter (a mission is a delivery). */
    public static final String REFERENCE_TYPE = "DELIVERY";
    public static final String CURRENCY = "XAF";
    /** Platform commission, tracked as due until the Kernel can split a transfer (request R2). */
    public static final BigDecimal COMMISSION_RATE = new BigDecimal("0.05");

    private final DeliveryRepository deliveryRepository;
    private final IAnnouncementRepository announcementRepository;
    private final IDeliveryNeedRepository deliveryNeedRepository;
    private final DeliveryStatusApplicationService deliveryStatusService;
    private final IKernelWalletUseCase kernelWallet;
    private final ISettlementUseCase settlements;
    private final TenantContextHolder tenantContextHolder;

    // ── 1. Delivery person: receive code ───────────────────────────────────

    @Override
    public Mono<ReceiveCodeResponse> generateReceiveCode(UUID deliveryId, UUID callerUserId) {
        return findDelivery(deliveryId)
                .flatMap(delivery -> deliveryStatusService
                        .assertCallerIsAssignedDeliveryPerson(deliveryId, callerUserId)
                        .onErrorMap(org.springframework.security.access.AccessDeniedException.class,
                                e -> new DeliveryPaymentException(Reason.FORBIDDEN, e.getMessage()))
                        .then(Mono.defer(() -> {
                            if (delivery.getStatus() == DeliveryStatus.CANCELLED) {
                                return Mono.<ReceiveCodeResponse>error(new DeliveryPaymentException(
                                        Reason.CONFLICT, "Delivery " + deliveryId + " is cancelled"));
                            }
                            // Generated with the DRIVER's bearer: the Kernel binds the code to their wallet.
                            return kernelWallet.generateReceiveCode()
                                    .flatMap(rc -> {
                                        delivery.setDriverUserId(callerUserId);
                                        delivery.setReceiveCode(rc.code());
                                        delivery.setReceiveCodeExpiresAt(rc.expiresAt());
                                        return deliveryRepository.save(delivery)
                                                .thenReturn(new ReceiveCodeResponse(
                                                        deliveryId, rc.code(), rc.expiresAt()));
                                    });
                        })));
    }

    // ── 2. Client: challenge ───────────────────────────────────────────────

    @Override
    public Mono<PaymentChallengeResponse> startPayment(UUID deliveryId, UUID callerUserId) {
        return findPayableDelivery(deliveryId, callerUserId)
                .flatMap(delivery -> {
                    BigDecimal amount = amountOf(delivery);
                    return kernelWallet.transferChallenge(delivery.getReceiveCode(), amount, reference(deliveryId))
                            .map(ch -> new PaymentChallengeResponse(deliveryId, amount, CURRENCY,
                                    ch.challengeToken(), ch.expiresInSeconds(), ch.codePreview()));
                });
    }

    // ── 3. Client: confirm → money moves ───────────────────────────────────

    @Override
    public Mono<PaymentStatusResponse> confirmPayment(UUID deliveryId, UUID callerUserId,
                                                      String challengeToken, String code) {
        return findPayableDelivery(deliveryId, callerUserId)
                .flatMap(delivery -> {
                    BigDecimal amount = amountOf(delivery);
                    return tenantContextHolder.currentTenantId()
                            .defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT)
                            .flatMap(tenantId -> kernelWallet
                                    .transfer(delivery.getReceiveCode(), amount, reference(deliveryId),
                                            challengeToken, code)
                                    .flatMap(tx -> recordPaid(tenantId, delivery, callerUserId, amount, tx)));
                });
    }

    private Mono<PaymentStatusResponse> recordPaid(UUID tenantId, Delivery delivery, UUID payerUserId,
                                                   BigDecimal amount, JsonNode kernelTx) {
        RecordSettlementCommand cmd = new RecordSettlementCommand(
                tenantId, REFERENCE_TYPE, delivery.getId(), payerUserId, delivery.getDriverUserId(),
                amount, CURRENCY, SettlementMethod.WALLET_TRANSFER, SettlementStatus.PAID,
                kernelReference(kernelTx, delivery.getId()), COMMISSION_RATE);
        return settlements.record(cmd)
                .doOnError(e -> log.error("PAYMENT_UNRECORDED delivery={} payer={} amount={} {} — the Kernel "
                                + "transfer SUCCEEDED but the settlement could not be saved ({}); record it by hand",
                        delivery.getId(), payerUserId, amount, CURRENCY, e.toString()))
                .map(s -> toResponse(delivery, s));
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
                            return tenantContextHolder.currentTenantId()
                                    .defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT)
                                    .flatMap(tenantId -> settlements.find(tenantId, REFERENCE_TYPE, deliveryId))
                                    .map(s -> toResponse(delivery, s))
                                    .switchIfEmpty(Mono.fromSupplier(() -> notSettled(delivery)));
                        }));
    }

    // ── Guards ─────────────────────────────────────────────────────────────

    /** Delivery exists, caller is its client, payable (tarif, not cancelled, not paid/cash, valid code). */
    private Mono<Delivery> findPayableDelivery(UUID deliveryId, UUID callerUserId) {
        return findDelivery(deliveryId).flatMap(delivery ->
                isClient(delivery, callerUserId).flatMap(isClient -> {
                    if (!isClient) {
                        return Mono.<Delivery>error(new DeliveryPaymentException(Reason.FORBIDDEN,
                                "Only the client of delivery " + deliveryId + " can pay it"));
                    }
                    if (delivery.getStatus() == DeliveryStatus.CANCELLED) {
                        return Mono.<Delivery>error(new DeliveryPaymentException(Reason.CONFLICT,
                                "Delivery " + deliveryId + " is cancelled"));
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
                        return tenantContextHolder.currentTenantId()
                                .defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT)
                                .flatMap(tenantId -> settlements.find(tenantId, REFERENCE_TYPE, deliveryId)
                                        .map(s -> s.getStatus() == SettlementStatus.PAID)
                                        .defaultIfEmpty(false))
                                .flatMap(alreadyPaid -> {
                                    if (alreadyPaid) {
                                        return Mono.<Delivery>error(new DeliveryPaymentException(Reason.CONFLICT,
                                                "Delivery " + deliveryId + " is already paid"));
                                    }
                                    if (!delivery.hasValidReceiveCode()) {
                                        return Mono.<Delivery>error(new DeliveryPaymentException(
                                                Reason.RECEIVE_CODE_MISSING,
                                                "The delivery person has no valid receive code: ask them to "
                                                        + "regenerate it (POST /api/v1/deliveries/" + deliveryId
                                                        + "/payment/receive-code)"));
                                    }
                                    return Mono.just(delivery);
                                });
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

    /** Best-effort extraction of the Kernel transaction id from its (loosely specified) response. */
    static String kernelReference(JsonNode tx, UUID deliveryId) {
        if (tx != null) {
            for (String field : new String[]{"transactionId", "id", "reference", "txId"}) {
                JsonNode n = tx.path(field);
                if (!n.isMissingNode() && !n.isNull() && !n.asText().isBlank()) {
                    return n.asText();
                }
            }
        }
        return reference(deliveryId);
    }

    private static PaymentStatusResponse toResponse(Delivery delivery, WalletSettlement s) {
        return new PaymentStatusResponse(delivery.getId(), s.getStatus().name(), s.getAmount(), s.getCurrency(),
                s.getMethod().name(), s.getCommissionStatus().name(), s.getCommissionAmount(),
                s.getStatus() != SettlementStatus.PAID && s.getMethod() == SettlementMethod.WALLET_TRANSFER
                        && delivery.hasValidReceiveCode(),
                s.getPaidAt() == null ? null : s.getPaidAt().atZone(java.time.ZoneId.systemDefault()).toInstant());
    }

    private static PaymentStatusResponse notSettled(Delivery delivery) {
        boolean payable = delivery.getTarif() != null && delivery.getTarif() > 0
                && delivery.getStatus() != DeliveryStatus.CANCELLED && delivery.hasValidReceiveCode();
        return new PaymentStatusResponse(delivery.getId(), "NOT_SETTLED",
                delivery.getTarif() == null ? null : amountOf(delivery), CURRENCY,
                null, null, null, payable, null);
    }
}
