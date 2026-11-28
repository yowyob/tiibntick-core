package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ISettlementUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.RecordSettlementCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ISettlementEventPublisher;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ISettlementRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.CommissionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementMethod;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Records settlements and — since the 2026-11-26 decision on risk R1 of the Kafka audit — publishes
 * the wallet events the Core's consumers expect, because the Kernel wallet emits none of them:
 * <ul>
 *   <li>{@code tnt.billing.wallet.payment.confirmed} when a settlement <em>becomes</em> PAID;</li>
 *   <li>{@code tnt.billing.wallet.commission.calculated} when a platform commission becomes DUE (or
 *       its DUE amount changes).</li>
 * </ul>
 * Only transitions publish: {@link #record} is an idempotent upsert, so a retried call that changes
 * nothing emits nothing. The events go through the yow-event-kernel outbox (automatic relay). A
 * failure to enqueue them never fails the settlement, which is the source of truth for money that
 * has already moved on the Kernel: it is logged with the marker {@code SETTLEMENT_EVENT_NOT_PUBLISHED}.
 */
@Slf4j
@Service
public class SettlementService implements ISettlementUseCase {

    /** Publishes nothing — for callers (tests, tooling) that only need the settlement bookkeeping. */
    public static final ISettlementEventPublisher NO_EVENTS = new ISettlementEventPublisher() {
        @Override
        public Mono<Void> paymentConfirmed(WalletSettlement settlement) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> commissionCalculated(WalletSettlement settlement) {
            return Mono.empty();
        }
    };

    private final ISettlementRepository repository;
    private final ISettlementEventPublisher events;

    @Autowired
    public SettlementService(ISettlementRepository repository, ISettlementEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    /** Bookkeeping only, no event publication (see {@link #NO_EVENTS}). */
    public SettlementService(ISettlementRepository repository) {
        this(repository, NO_EVENTS);
    }

    @Override
    public Mono<WalletSettlement> record(RecordSettlementCommand c) {
        return repository.findByReference(c.tenantId(), c.referenceType(), c.referenceId())
                .flatMap(existing -> repository.save(merge(existing, c))
                        .flatMap(saved -> publishTransitions(existing, saved).thenReturn(saved)))
                .switchIfEmpty(Mono.defer(() -> repository.save(create(c))
                        .flatMap(saved -> publishTransitions(null, saved).thenReturn(saved))))
                .doOnSuccess(s -> log.info("[Settlement] {} {} → status={} method={} commission={} {} ({})",
                        s.getReferenceType(), s.getReferenceId(), s.getStatus(), s.getMethod(),
                        s.getCommissionAmount(), s.getCurrency(), s.getCommissionStatus()));
    }

    /** Publishes the events of the transitions from {@code before} (null when created) to {@code after}. */
    private Mono<Void> publishTransitions(WalletSettlement before, WalletSettlement after) {
        Mono<Void> payment = becamePaid(before, after)
                ? safely(() -> events.paymentConfirmed(after), "payment.confirmed", after) : Mono.empty();
        Mono<Void> commission = commissionBecameDue(before, after)
                ? safely(() -> events.commissionCalculated(after), "commission.calculated", after) : Mono.empty();
        return payment.then(commission);
    }

    static boolean becamePaid(WalletSettlement before, WalletSettlement after) {
        return after.getStatus() == SettlementStatus.PAID
                && (before == null || before.getStatus() != SettlementStatus.PAID);
    }

    static boolean commissionBecameDue(WalletSettlement before, WalletSettlement after) {
        if (after.getMethod() == SettlementMethod.ESCROW && after.getCommissionStatus() == CommissionStatus.SETTLED
                && after.getCommissionAmount() != null && after.getCommissionAmount().signum() > 0) {
            // lot L5: the levy taken by the escrow release is the platform's commission (accounting, §4.6)
            return before == null || before.getCommissionStatus() != CommissionStatus.SETTLED;
        }
        if (after.getCommissionStatus() != CommissionStatus.DUE || after.getCommissionAmount() == null
                || after.getCommissionAmount().signum() <= 0) {
            return false;
        }
        return before == null || before.getCommissionStatus() != CommissionStatus.DUE
                || before.getCommissionAmount() == null
                || before.getCommissionAmount().compareTo(after.getCommissionAmount()) != 0;
    }

    private Mono<Void> safely(Supplier<Mono<Void>> publication, String event, WalletSettlement s) {
        return Mono.defer(publication)
                .onErrorResume(e -> {
                    log.error("SETTLEMENT_EVENT_NOT_PUBLISHED event={} settlement={} {} {} — the settlement is "
                                    + "recorded but its event could not be enqueued: {}",
                            event, s.getId(), s.getReferenceType(), s.getReferenceId(), e.toString());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<WalletSettlement> find(UUID tenantId, String referenceType, UUID referenceId) {
        return repository.findByReference(tenantId, referenceType, referenceId);
    }

    @Override
    public Flux<WalletSettlement> listCommissionsDue(UUID tenantId) {
        return repository.findByTenantAndCommissionStatus(tenantId, CommissionStatus.DUE);
    }

    @Override
    public Mono<WalletSettlement> markCommissionSettled(UUID tenantId, UUID settlementId) {
        return repository.findById(settlementId)
                .filter(s -> s.getTenantId().equals(tenantId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Settlement not found: " + settlementId)))
                .flatMap(s -> repository.save(s.toBuilder()
                                .commissionStatus(CommissionStatus.SETTLED)
                                .updatedAt(LocalDateTime.now()).build())
                        // lot L6: the commission remitted in cash (K4) is now the platform's — published again,
                        // SETTLED, so that accounting books it (a DUE commission is not booked)
                        .flatMap(saved -> s.getCommissionStatus() != CommissionStatus.SETTLED
                                && saved.getCommissionAmount() != null && saved.getCommissionAmount().signum() > 0
                                ? safely(() -> events.commissionCalculated(saved), "commission.calculated", saved)
                                .thenReturn(saved)
                                : Mono.just(saved)));
    }

    private static WalletSettlement create(RecordSettlementCommand c) {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal rate = rate(c);
        BigDecimal commission = commission(c.amount(), rate);
        return WalletSettlement.builder()
                .id(UUID.randomUUID()).tenantId(c.tenantId())
                .referenceType(c.referenceType()).referenceId(c.referenceId())
                .payerUserId(c.payerUserId()).payeeUserId(c.payeeUserId())
                .amount(c.amount()).currency(c.currency())
                .method(c.method()).status(c.status()).kernelReference(c.kernelReference())
                .commissionRate(rate).commissionAmount(commission)
                .commissionStatus(commissionStatus(commission, c.status(), c.method()))
                .createdAt(now).updatedAt(now)
                .paidAt(c.status() == SettlementStatus.PAID ? now : null)
                .persisted(false)
                .build();
    }

    private static WalletSettlement merge(WalletSettlement existing, RecordSettlementCommand c) {
        // A settlement already PAID is final: a later UNPAID/CASH report must not downgrade it.
        if (existing.getStatus() == SettlementStatus.PAID && c.status() != SettlementStatus.PAID) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        BigDecimal rate = rate(c);
        BigDecimal commission = commission(c.amount(), rate);
        boolean commissionFinal = existing.getCommissionStatus() == CommissionStatus.SETTLED
                || existing.getCommissionStatus() == CommissionStatus.WAIVED;
        return existing.toBuilder()
                .payerUserId(c.payerUserId() != null ? c.payerUserId() : existing.getPayerUserId())
                .payeeUserId(c.payeeUserId() != null ? c.payeeUserId() : existing.getPayeeUserId())
                .amount(c.amount()).currency(c.currency())
                .method(c.method()).status(c.status())
                .kernelReference(c.kernelReference() != null ? c.kernelReference() : existing.getKernelReference())
                .commissionRate(commissionFinal ? existing.getCommissionRate() : rate)
                .commissionAmount(commissionFinal ? existing.getCommissionAmount() : commission)
                .commissionStatus(commissionFinal ? existing.getCommissionStatus()
                        : commissionStatus(commission, c.status(), c.method()))
                .paidAt(c.status() == SettlementStatus.PAID && existing.getPaidAt() == null ? now : existing.getPaidAt())
                .updatedAt(now)
                .build();
    }

    private static BigDecimal rate(RecordSettlementCommand c) {
        return c.commissionRate() == null ? BigDecimal.ZERO : c.commissionRate();
    }

    private static BigDecimal commission(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Commission is owed once money has actually been received (transfer or cash), not while unpaid; an escrow
     * release pays the platform share at source (lot L5), so its commission is SETTLED at once.
     */
    private static CommissionStatus commissionStatus(BigDecimal commission, SettlementStatus status,
                                                     SettlementMethod method) {
        if (commission.signum() <= 0 || status == SettlementStatus.UNPAID) {
            return CommissionStatus.NONE;
        }
        return method == SettlementMethod.ESCROW && status == SettlementStatus.PAID
                ? CommissionStatus.SETTLED : CommissionStatus.DUE;
    }
}
