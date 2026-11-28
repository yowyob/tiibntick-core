package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ICashCollectionUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ISettlementUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.RecordSettlementCommand;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementMethod;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IKernelSettlementPort;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IPaymentMirrorRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.CashCollection;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.CashCollectionRequest;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Challenge;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Share;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.CashCollectionMirror;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cash collected by a courier for the Kernel (spec §4.1 mode CASH, §4.2 K1–K5). The Kernel computes what the courier
 * owes back (platform and merchant parts); {@code billing.wallet_cash_collections} mirrors it.
 */
@Slf4j
@Service
public class CashCollectionService implements ICashCollectionUseCase {

    private final IKernelSettlementPort kernel;
    private final IPaymentMirrorRepository mirrors;
    private final ISettlementUseCase settlements;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CashCollectionService(IKernelSettlementPort kernel, IPaymentMirrorRepository mirrors,
                                 ISettlementUseCase settlements) {
        this(kernel, mirrors, settlements, Clock.systemUTC());
    }

    public CashCollectionService(IKernelSettlementPort kernel, IPaymentMirrorRepository mirrors,
                                 ISettlementUseCase settlements, Clock clock) {
        this.kernel = kernel;
        this.mirrors = mirrors;
        this.settlements = settlements;
        this.clock = clock;
    }

    @Override
    public Mono<CashCollectionMirror> open(UUID intentId, UUID callerActorId) {
        return mirrors.findIntent(intentId)
                .switchIfEmpty(Mono.error(() -> new WalletSettlementException(
                        WalletSettlementException.PAYMENT_INTENT_NOT_FOUND, 404, "Unknown payment intent " + intentId)))
                .flatMap(intent -> {
                    if (callerActorId == null || !callerActorId.equals(intent.payerActorId())) {
                        return Mono.error(new WalletSettlementException(
                                WalletSettlementException.PAYMENT_INTENT_FORBIDDEN, 403,
                                "Only the payer confirms this payment"));
                    }
                    if (intent.mode() != PaymentIntent.Mode.CASH) {
                        return Mono.error(new WalletSettlementException(
                                WalletSettlementException.PAYMENT_INTENT_INVALID, 409,
                                "The intent " + intentId + " is not a cash payment"));
                    }
                    return mirrors.findCashCollection(intent.deliveryId())
                            .switchIfEmpty(Mono.defer(() -> openNow(intent)));
                });
    }

    private Mono<CashCollectionMirror> openNow(PaymentIntent intent) {
        List<Share> shares = new ArrayList<>();
        if (intent.shares().platform().signum() > 0) {
            shares.add(new Share(Share.PLATFORM, intent.platformWalletId(), intent.shares().platform()));
        }
        intent.shares().merchants().forEach(m -> shares.add(new Share(Share.MERCHANT, m.walletId(), m.amount())));
        shares.add(new Share(Share.COURIER, intent.courierWalletId(), intent.shares().courier()));
        return kernel.openCashCollection(new CashCollectionRequest(EscrowService.SUBJECT_DELIVERY,
                        intent.deliveryId(), intent.courierWalletId(), intent.currency(), shares))
                .onErrorMap(EscrowService::kernelError)
                .switchIfEmpty(Mono.error(() -> new WalletSettlementException(
                        WalletSettlementException.KERNEL_UNAVAILABLE, 503, "The Kernel returned no cash collection")))
                .flatMap(k -> {
                    Instant now = clock.instant();
                    CashCollectionMirror.Status status = CashCollectionMirror.Status.of(k.status());
                    return mirrors.saveCashCollection(new CashCollectionMirror(UUID.randomUUID(), intent.tenantId(),
                            intent.deliveryId(), k.id(), intent.courierActorId(), intent.courierWalletId(),
                            k.expectedAmount() != null ? k.expectedAmount() : intent.amount(), k.collectedAmount(),
                            k.amountToRemit(), intent.currency(),
                            status != null ? status : CashCollectionMirror.Status.OPEN, now, now), true);
                })
                .flatMap(m -> mirrors.saveIntent(intent.consume(clock.instant()), false).thenReturn(m));
    }

    @Override
    public Mono<CashCollectionMirror> collected(UUID deliveryId, BigDecimal amount) {
        return mirrors.findCashCollection(deliveryId).flatMap(m -> {
            if (m.status() != CashCollectionMirror.Status.OPEN) {
                return Mono.just(m);
            }
            BigDecimal collected = amount != null ? amount : m.expectedAmount();
            return kernel.markCollected(m.kernelCollectionId(), collected)
                    .onErrorMap(EscrowService::kernelError)
                    .flatMap(k -> update(m, k))
                    .switchIfEmpty(Mono.defer(() -> mirrors.saveCashCollection(
                            m.with(CashCollectionMirror.Status.COLLECTED, collected, null, clock.instant()), false)))
                    .flatMap(saved -> recordSettlement(saved).thenReturn(saved));
        });
    }

    @Override
    public Mono<CashCollectionMirror> cancel(UUID deliveryId) {
        return mirrors.findCashCollection(deliveryId).flatMap(m -> {
            if (m.status() != CashCollectionMirror.Status.OPEN) {
                if (m.status() != CashCollectionMirror.Status.CANCELLED) {
                    log.warn("CASH_COLLECTION_NOT_CANCELLABLE delivery={} collection={} status={} — left as is",
                            deliveryId, m.kernelCollectionId(), m.status());
                }
                return Mono.just(m);
            }
            return kernel.cancelCashCollection(m.kernelCollectionId())
                    .onErrorMap(EscrowService::kernelError)
                    .flatMap(k -> update(m, k))
                    .switchIfEmpty(Mono.defer(() -> mirrors.saveCashCollection(
                            m.with(CashCollectionMirror.Status.CANCELLED, null, null, clock.instant()), false)));
        });
    }

    @Override
    public Mono<Challenge> remitChallenge(UUID deliveryId, UUID courierActorId) {
        return ownCollection(deliveryId, courierActorId)
                .flatMap(m -> kernel.remitChallenge(m.kernelCollectionId()).onErrorMap(EscrowService::kernelError));
    }

    @Override
    public Mono<CashCollectionMirror> remit(UUID deliveryId, UUID courierActorId, String token, String code) {
        return ownCollection(deliveryId, courierActorId).flatMap(m -> {
            if (m.status() == CashCollectionMirror.Status.REMITTED) {
                return Mono.just(m);
            }
            return kernel.remit(m.kernelCollectionId(), token, code)
                    .onErrorMap(EscrowService::kernelError)
                    .flatMap(k -> update(m, k))
                    .flatMap(saved -> saved.status() == CashCollectionMirror.Status.REMITTED
                            ? commissionRemitted(saved).thenReturn(saved) : Mono.just(saved));
        });
    }

    @Override
    public Flux<CashCollectionMirror> outstanding(UUID courierActorId) {
        return mirrors.outstandingCashCollections(courierActorId);
    }

    /** K2: the delivery is settled in cash (commission DUE from the courier until remitted). */
    private Mono<Void> recordSettlement(CashCollectionMirror c) {
        return mirrors.findIntentByDelivery(c.deliveryId())
                .flatMap(intent -> settlements.record(new RecordSettlementCommand(c.tenantId(),
                        EscrowService.SUBJECT_DELIVERY, c.deliveryId(), intent.payerActorId(), c.courierActorId(),
                        c.collectedAmount() != null ? c.collectedAmount() : c.expectedAmount(), c.currency(),
                        SettlementMethod.CASH, SettlementStatus.CASH_COLLECTED, c.kernelCollectionId().toString(),
                        EscrowService.rate(intent))))
                .onErrorResume(e -> {
                    log.error("CASH_SETTLEMENT_UNRECORDED delivery={} collection={}: {}", c.deliveryId(),
                            c.kernelCollectionId(), e.toString());
                    return Mono.empty();
                })
                .then();
    }

    /** K4: the courier remitted the platform's part — the commission is settled. */
    private Mono<Void> commissionRemitted(CashCollectionMirror c) {
        return settlements.find(c.tenantId(), EscrowService.SUBJECT_DELIVERY, c.deliveryId())
                .flatMap(s -> settlements.markCommissionSettled(c.tenantId(), s.getId()))
                .onErrorResume(e -> {
                    log.error("CASH_COMMISSION_UNRECORDED delivery={} collection={} — remitted in the Kernel: {}",
                            c.deliveryId(), c.kernelCollectionId(), e.toString());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<CashCollectionMirror> ownCollection(UUID deliveryId, UUID courierActorId) {
        return mirrors.findCashCollection(deliveryId)
                .switchIfEmpty(Mono.error(() -> new WalletSettlementException(
                        WalletSettlementException.CASH_COLLECTION_NOT_FOUND, 404,
                        "No cash collection for delivery " + deliveryId)))
                .flatMap(m -> courierActorId != null && courierActorId.equals(m.courierActorId()) ? Mono.just(m)
                        : Mono.error(new WalletSettlementException(WalletSettlementException.CASH_COLLECTION_FORBIDDEN,
                        403, "Only the courier who collected the cash remits it")));
    }

    private Mono<CashCollectionMirror> update(CashCollectionMirror m, CashCollection k) {
        CashCollectionMirror.Status s = CashCollectionMirror.Status.of(k.status());
        return mirrors.saveCashCollection(m.with(s != null ? s : m.status(), k.collectedAmount(), k.amountToRemit(),
                clock.instant()), false);
    }
}
