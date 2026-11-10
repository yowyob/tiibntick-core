package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletHoldUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ISettlementRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletHoldRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.HoldStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletHold;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletHoldService implements IWalletHoldUseCase {

    private final IWalletHoldRepository holds;
    private final ISettlementRepository settlements;

    @Override
    public Mono<WalletHold> placeHold(UUID tenantId, UUID ownerUserId, String referenceType, UUID referenceId,
                                      BigDecimal amount, String currency, String reason) {
        return holds.findActiveByReference(referenceType, referenceId)
                .filter(h -> h.getOwnerUserId().equals(ownerUserId))
                .next()
                .switchIfEmpty(Mono.defer(() -> holds.save(WalletHold.builder()
                        .id(UUID.randomUUID()).tenantId(tenantId).ownerUserId(ownerUserId)
                        .referenceType(referenceType).referenceId(referenceId)
                        .amount(amount).currency(currency).reason(truncate(reason))
                        .status(HoldStatus.ACTIVE).createdAt(LocalDateTime.now())
                        .persisted(false).build())
                        .doOnSuccess(h -> log.info("[Hold] placed {} {} on user={} for {} {}",
                                amount, currency, ownerUserId, referenceType, referenceId))));
    }

    @Override
    public Mono<WalletHold> holdSettlement(String referenceType, UUID referenceId, String reason) {
        return settlements.findByReference(referenceType, referenceId)
                .filter(s -> s.getStatus() == SettlementStatus.PAID && s.getPayeeUserId() != null)
                .switchIfEmpty(Mono.fromRunnable(() -> log.warn(
                        "[Hold] no PAID settlement for {} {} — nothing to hold", referenceType, referenceId)))
                .flatMap(s -> placeHold(s.getTenantId(), s.getPayeeUserId(), referenceType, referenceId,
                        s.getAmount(), s.getCurrency(), reason));
    }

    @Override
    public Mono<Long> releaseHolds(String referenceType, UUID referenceId) {
        return holds.findActiveByReference(referenceType, referenceId)
                .concatMap(h -> holds.save(h.toBuilder()
                        .status(HoldStatus.RELEASED).releasedAt(LocalDateTime.now()).build()))
                .count()
                .doOnNext(n -> {
                    if (n > 0) {
                        log.info("[Hold] released {} hold(s) for {} {}", n, referenceType, referenceId);
                    }
                });
    }

    @Override
    public Flux<WalletHold> listActive(UUID ownerUserId) {
        return holds.findActiveByOwner(ownerUserId);
    }

    @Override
    public Mono<BigDecimal> activeHeldAmount(UUID ownerUserId, String currency) {
        return holds.findActiveByOwner(ownerUserId)
                .filter(h -> currency == null || currency.equalsIgnoreCase(h.getCurrency()))
                .map(WalletHold::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String truncate(String reason) {
        return reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
    }
}
