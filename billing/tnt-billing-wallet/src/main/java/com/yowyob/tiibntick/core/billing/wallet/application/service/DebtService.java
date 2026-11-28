package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IDebtUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IDebtRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IKernelSettlementPort;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.CashCollection;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.CashCollectionRequest;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Challenge;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Share;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletDebt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Debts towards the compensation fund (decisions QO-22 / D-115 and QO-26 / D-120, admin spec §5.2 points 2 and 4).
 *
 * <p>Recovery "like unpaid cash": a Kernel cash collection with {@code subjectType = DEBT}, {@code subjectId = debt id},
 * opened by the platform on the debtor's wallet with one {@code PLATFORM} share — the fund wallet — of the whole
 * amount, then marked collected at once (the debtor holds the money). The debtor remits it with the code the Kernel
 * sends him ({@code /remit/challenge} then {@code /remit}, his own bearer): the debt is REPAID. No automatic blocking:
 * the administrator sees the debts and decides on a suspension.
 *
 * <p>A debt whose recovery cannot be opened (debtor unknown — a relay or agency the event does not name —, without
 * wallet, Kernel down) stays OPEN without collection, logged {@code DEBT_RECOVERY_NOT_OPENED}; the administrator names
 * the debtor or retries.
 */
@Slf4j
@Service
public class DebtService implements IDebtUseCase {

    public static final String SUBJECT_DEBT = "DEBT";
    private static final int MAX_LIST = 500;

    private final IKernelSettlementPort kernel;
    private final IDebtRepository debts;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public DebtService(IKernelSettlementPort kernel, IDebtRepository debts) {
        this(kernel, debts, Clock.systemUTC());
    }

    public DebtService(IKernelSettlementPort kernel, IDebtRepository debts, Clock clock) {
        this.kernel = kernel;
        this.debts = debts;
        this.clock = clock;
    }

    @Override
    public Mono<WalletDebt> record(NewDebt d) {
        return debts.findByReference(d.reference())
                .flatMap(existing -> existing.status() == WalletDebt.Status.PENDING
                        ? activate(d.reference(), d.amount()) : Mono.just(existing))
                .switchIfEmpty(Mono.defer(() -> insert(d, WalletDebt.Status.OPEN).flatMap(this::openRecovery)));
    }

    @Override
    public Mono<WalletDebt> recordPending(NewDebt d) {
        return debts.findByReference(d.reference())
                .switchIfEmpty(Mono.defer(() -> insert(d, WalletDebt.Status.PENDING)));
    }

    @Override
    public Mono<WalletDebt> activate(String reference, BigDecimal amount) {
        return debts.findByReference(reference).flatMap(debt -> debt.status() != WalletDebt.Status.PENDING
                ? Mono.just(debt)
                : debts.save(debt.with(WalletDebt.Status.OPEN, amount, null, clock.instant()), false)
                .flatMap(this::openRecovery));
    }

    @Override
    public Mono<WalletDebt> cancelPending(String reference, String note) {
        return debts.findByReference(reference).flatMap(debt -> debt.status() != WalletDebt.Status.PENDING
                ? Mono.just(debt)
                : debts.save(debt.with(WalletDebt.Status.CANCELLED, null, note, clock.instant()), false));
    }

    @Override
    public Flux<WalletDebt> mine(UUID callerActorId, UUID callerOrganizationId) {
        Flux<WalletDebt> own = callerActorId == null ? Flux.empty() : debts.byDebtor(callerActorId);
        Flux<WalletDebt> org = callerOrganizationId == null || callerOrganizationId.equals(callerActorId)
                ? Flux.empty() : debts.byDebtor(callerOrganizationId);
        return Flux.concat(own, org);
    }

    @Override
    public Mono<Challenge> repayChallenge(UUID debtId, UUID callerActorId, UUID callerOrganizationId) {
        return recoverable(debtId, callerActorId, callerOrganizationId)
                .flatMap(debt -> kernel.remitChallenge(debt.kernelCollectionId()).onErrorMap(EscrowService::kernelError));
    }

    @Override
    public Mono<WalletDebt> repay(UUID debtId, UUID callerActorId, UUID callerOrganizationId, String token,
                                  String code) {
        return debts.findById(debtId)
                .flatMap(debt -> debt.status() == WalletDebt.Status.REPAID && owns(debt, callerActorId,
                        callerOrganizationId) ? Mono.just(debt)
                        : recoverable(debtId, callerActorId, callerOrganizationId)
                        .flatMap(d -> kernel.remit(d.kernelCollectionId(), token, code)
                                .onErrorMap(EscrowService::kernelError)
                                .flatMap(k -> "REMITTED".equalsIgnoreCase(k.status())
                                        ? debts.save(d.with(WalletDebt.Status.REPAID, null, null, clock.instant()),
                                        false)
                                        : Mono.just(d))))
                .switchIfEmpty(Mono.error(() -> notFound(debtId)));
    }

    @Override
    public Flux<WalletDebt> list(WalletDebt.Status status, int limit) {
        return debts.byStatus(status, limit <= 0 ? 100 : Math.min(limit, MAX_LIST));
    }

    @Override
    public Mono<WalletDebt> assign(UUID debtId, UUID debtorId) {
        return debts.findById(debtId).switchIfEmpty(Mono.error(() -> notFound(debtId))).flatMap(debt -> {
            if (debt.status().isFinal() || debt.kernelCollectionId() != null) {
                return Mono.error(new WalletSettlementException(WalletSettlementException.DEBT_NOT_RECOVERABLE, 409,
                        "The debt " + debtId + " is " + debt.status() + (debt.kernelCollectionId() != null
                        ? " and its recovery is already open" : "")));
            }
            WalletDebt named = debt.withDebtor(debtorId, clock.instant());
            return debts.save(named, false).flatMap(saved -> saved.status() == WalletDebt.Status.OPEN
                    ? openRecovery(saved) : Mono.just(saved));
        });
    }

    @Override
    public Mono<WalletDebt> retryRecovery(UUID debtId) {
        return debts.findById(debtId).switchIfEmpty(Mono.error(() -> notFound(debtId)))
                .flatMap(debt -> debt.status() == WalletDebt.Status.OPEN && debt.kernelCollectionId() == null
                        ? openRecovery(debt) : Mono.just(debt));
    }

    @Override
    public Mono<WalletDebt> writeOff(UUID debtId, String note) {
        return debts.findById(debtId).switchIfEmpty(Mono.error(() -> notFound(debtId))).flatMap(debt -> {
            if (debt.status() == WalletDebt.Status.CANCELLED) {
                return Mono.just(debt);
            }
            if (debt.status() == WalletDebt.Status.REPAID) {
                return Mono.error(new WalletSettlementException(WalletSettlementException.DEBT_NOT_RECOVERABLE, 409,
                        "The debt " + debtId + " is already repaid"));
            }
            Mono<?> cancel = debt.kernelCollectionId() == null ? Mono.empty()
                    : kernel.cancelCashCollection(debt.kernelCollectionId()).onErrorMap(EscrowService::kernelError);
            return cancel.then(debts.save(debt.with(WalletDebt.Status.CANCELLED, null,
                    note != null ? "WRITTEN_OFF: " + note : "WRITTEN_OFF", clock.instant()), false));
        });
    }

    // ── recovery ────────────────────────────────────────────────────────────

    private Mono<WalletDebt> openRecovery(WalletDebt debt) {
        if (debt.debtorId() == null) {
            log.warn("DEBT_RECOVERY_NOT_OPENED debt={} reference={} type={} amount={} {} reason=DEBTOR_UNKNOWN — the"
                            + " administrator names the debtor (admin spec §5.2)", debt.id(), debt.reference(),
                    debt.debtorType(), debt.amount(), debt.currency());
            return Mono.just(debt);
        }
        return kernel.walletOfOwner(debt.debtorId())
                .switchIfEmpty(Mono.error(() -> new WalletSettlementException(WalletSettlementException.WALLET_MISSING,
                        409, "The debtor " + debt.debtorId() + " has no Kernel wallet")))
                .zipWith(kernel.compensationFund().switchIfEmpty(Mono.error(() -> new WalletSettlementException(
                        WalletSettlementException.KERNEL_UNAVAILABLE, 503, "The Kernel compensation fund did not answer"))))
                .flatMap(t -> kernel.openCashCollection(new CashCollectionRequest(SUBJECT_DEBT, debt.id(),
                                t.getT1().id(), debt.currency(),
                                List.of(new Share(Share.PLATFORM, t.getT2().walletId(), debt.amount()))))
                        .flatMap(k -> collected(k, debt.amount()))
                        .flatMap(k -> debts.save(debt.withRecovery(t.getT1().id(), k.id(), clock.instant()), false)))
                .onErrorResume(e -> {
                    log.error("DEBT_RECOVERY_NOT_OPENED debt={} reference={} debtor={} amount={} {}: {} — the"
                                    + " administrator retries (admin spec §5.2)", debt.id(), debt.reference(),
                            debt.debtorId(), debt.amount(), debt.currency(), e.toString());
                    return Mono.just(debt);
                });
    }

    /** The debtor holds the money: the collection is marked collected for the whole amount at once. */
    private Mono<CashCollection> collected(CashCollection k, BigDecimal amount) {
        if (!"OPEN".equalsIgnoreCase(k.status())) {
            return Mono.just(k);
        }
        return kernel.markCollected(k.id(), amount).defaultIfEmpty(k);
    }

    private Mono<WalletDebt> recoverable(UUID debtId, UUID callerActorId, UUID callerOrganizationId) {
        return debts.findById(debtId).switchIfEmpty(Mono.error(() -> notFound(debtId))).flatMap(debt -> {
            if (!owns(debt, callerActorId, callerOrganizationId)) {
                return Mono.error(new WalletSettlementException(WalletSettlementException.DEBT_FORBIDDEN, 403,
                        "Only the debtor repays this debt"));
            }
            if (debt.status() != WalletDebt.Status.OPEN || debt.kernelCollectionId() == null) {
                return Mono.error(new WalletSettlementException(WalletSettlementException.DEBT_NOT_RECOVERABLE, 409,
                        "The debt " + debtId + " cannot be repaid now (" + debt.status()
                                + (debt.kernelCollectionId() == null ? ", recovery not open" : "") + ")"));
            }
            return Mono.just(debt);
        });
    }

    private static boolean owns(WalletDebt debt, UUID callerActorId, UUID callerOrganizationId) {
        return debt.debtorId() != null && (debt.debtorId().equals(callerActorId)
                || debt.debtorId().equals(callerOrganizationId));
    }

    private Mono<WalletDebt> insert(NewDebt d, WalletDebt.Status status) {
        var now = clock.instant();
        return debts.save(new WalletDebt(UUID.randomUUID(), d.tenantId(), d.debtorType(), d.debtorId(), d.reason(),
                d.reference(), d.deliveryId(), d.amount(), d.currency() != null ? d.currency() : "XAF", status, null,
                null, null, now, now), true);
    }

    private static WalletSettlementException notFound(UUID debtId) {
        return new WalletSettlementException(WalletSettlementException.DEBT_NOT_FOUND, 404, "Unknown debt " + debtId);
    }
}
