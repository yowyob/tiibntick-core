package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ISettlementUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.RecordSettlementCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ISettlementRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.CommissionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService implements ISettlementUseCase {

    private final ISettlementRepository repository;

    @Override
    public Mono<WalletSettlement> record(RecordSettlementCommand c) {
        return repository.findByReference(c.tenantId(), c.referenceType(), c.referenceId())
                .map(existing -> merge(existing, c))
                .switchIfEmpty(Mono.fromSupplier(() -> create(c)))
                .flatMap(repository::save)
                .doOnSuccess(s -> log.info("[Settlement] {} {} → status={} method={} commission={} {} ({})",
                        s.getReferenceType(), s.getReferenceId(), s.getStatus(), s.getMethod(),
                        s.getCommissionAmount(), s.getCurrency(), s.getCommissionStatus()));
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
                        .updatedAt(LocalDateTime.now()).build()));
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
                .commissionStatus(commissionStatus(commission, c.status()))
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
                        : commissionStatus(commission, c.status()))
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

    /** Commission is owed once money has actually been received (transfer or cash), not while unpaid. */
    private static CommissionStatus commissionStatus(BigDecimal commission, SettlementStatus status) {
        if (commission.signum() <= 0 || status == SettlementStatus.UNPAID) {
            return CommissionStatus.NONE;
        }
        return CommissionStatus.DUE;
    }
}
