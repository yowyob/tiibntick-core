package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ICompensationUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.PayCompensationCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ICompensationEventPublisher;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IKernelSettlementPort;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletCompensationRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Compensation;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.CompensationRequest;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletDto;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletCompensation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Pays a dispute compensation from the Kernel compensation fund (spec §4.3, lot L5):
 * <ol>
 *   <li>a mirror already exists for the dispute → returned, the Kernel is not called again;</li>
 *   <li>the beneficiary's Kernel wallet (the Core actor id is the Kernel user id) — none: 409;</li>
 *   <li>the fund balance ({@code GET /compensation-fund}) — below the amount: 409 {@code COMPENSATION_FUND_INSUFFICIENT};</li>
 *   <li>{@code POST /compensations} with the reference {@code DISPUTE:<id>};</li>
 *   <li>the mirror and {@code tnt.billing.compensation.paid} are written in a <b>new</b> transaction: once the Kernel
 *       has paid, the record survives a rollback of the caller's transaction (the dispute), and the published
 *       event lets the dispute module catch up (LT9 on {@code compensation.paid}).</li>
 * </ol>
 */
@Slf4j
@Service
public class CompensationService implements ICompensationUseCase {

    private final IKernelSettlementPort kernel;
    private final IWalletCompensationRepository mirrors;
    private final ICompensationEventPublisher events;
    private final ObjectProvider<ReactiveTransactionManager> transactions;

    public CompensationService(IKernelSettlementPort kernel, IWalletCompensationRepository mirrors,
                               ICompensationEventPublisher events,
                               ObjectProvider<ReactiveTransactionManager> transactions) {
        this.kernel = kernel;
        this.mirrors = mirrors;
        this.events = events;
        this.transactions = transactions;
    }

    @Override
    public Mono<WalletCompensation> payDisputeCompensation(PayCompensationCommand cmd) {
        return mirrors.findByDisputeId(cmd.disputeId())
                .switchIfEmpty(Mono.defer(() -> payNow(cmd, WalletCompensation.kernelReference(cmd.disputeId()),
                        true)));
    }

    @Override
    public Mono<WalletCompensation> payReturnCourier(UUID tenantId, UUID returnDeliveryId, UUID courierActorId,
                                                     java.math.BigDecimal amount, String currency) {
        // the mirror key is the Kernel reference itself, so that it never collides with a dispute id
        String key = com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletDebt.returnReference(returnDeliveryId);
        PayCompensationCommand cmd = new PayCompensationCommand(tenantId, key, courierActorId, amount,
                currency != null ? currency : "XAF");
        return mirrors.findByDisputeId(key).switchIfEmpty(Mono.defer(() -> payNow(cmd, key, false)));
    }

    @Override
    public Mono<WalletCompensation> payReplacementCourier(UUID tenantId, UUID deliveryId, int index,
                                                          UUID courierActorId, java.math.BigDecimal amount,
                                                          String currency) {
        String key = com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletDebt.replacementReference(deliveryId,
                index);
        PayCompensationCommand cmd = new PayCompensationCommand(tenantId, key, courierActorId, amount,
                currency != null ? currency : "XAF");
        return mirrors.findByDisputeId(key).switchIfEmpty(Mono.defer(() -> payNow(cmd, key, false)));
    }

    private Mono<WalletCompensation> payNow(PayCompensationCommand cmd, String reference, boolean dispute) {
        return kernel.walletOfOwner(cmd.beneficiaryActorId())
                .onErrorMap(CompensationService::kernelError)
                .switchIfEmpty(Mono.error(() -> new WalletSettlementException(
                        WalletSettlementException.BENEFICIARY_WALLET_MISSING, 409,
                        "The beneficiary " + cmd.beneficiaryActorId() + " has no Kernel wallet")))
                .flatMap(wallet -> kernel.compensationFund()
                        .onErrorMap(CompensationService::kernelError)
                        .switchIfEmpty(Mono.error(() -> new WalletSettlementException(
                                WalletSettlementException.KERNEL_UNAVAILABLE, 503,
                                "The Kernel compensation fund did not answer")))
                        .flatMap(fund -> {
                            if (fund.balance() == null || fund.balance().compareTo(cmd.amount()) < 0) {
                                log.error("COMPENSATION_FUND_INSUFFICIENT dispute={} amount={} {} balance={}",
                                        cmd.disputeId(), cmd.amount(), cmd.currency(), fund.balance());
                                return Mono.error(new WalletSettlementException(
                                        WalletSettlementException.FUND_INSUFFICIENT, 409,
                                        "The compensation fund holds " + fund.balance() + ", " + cmd.amount()
                                                + " is due"));
                            }
                            return kernel.compensate(new CompensationRequest(wallet.id(), cmd.amount(), reference))
                                    .onErrorMap(CompensationService::kernelError)
                                    .switchIfEmpty(Mono.error(() -> new WalletSettlementException(
                                            WalletSettlementException.KERNEL_UNAVAILABLE, 503,
                                            "The Kernel returned no compensation")))
                                    .flatMap(paid -> record(cmd, wallet, paid, dispute));
                        }));
    }

    private Mono<WalletCompensation> record(PayCompensationCommand cmd, KernelWalletDto wallet, Compensation paid,
                                            boolean dispute) {
        WalletCompensation mirror = new WalletCompensation(UUID.randomUUID(), cmd.tenantId(), cmd.disputeId(),
                paid.id(), paid.fundWalletId(), cmd.beneficiaryActorId(), wallet.id(), cmd.amount(), cmd.currency(),
                Instant.now());
        Mono<WalletCompensation> write = mirrors.insert(mirror)
                // lot L6: a return paid by the fund is published too (reason RETURN_COST, no dispute) so that
                // accounting books it; the dispute module ignores it
                .flatMap(saved -> events.compensationPaid(saved).thenReturn(saved));
        ReactiveTransactionManager tm = transactions.getIfAvailable();
        Mono<WalletCompensation> written = tm == null ? write
                : TransactionalOperator.create(tm, requiresNew()).transactional(write);
        return written.doOnError(e -> log.error("COMPENSATION_UNRECORDED dispute={} kernelCompensation={} amount={} {}"
                        + " — the Kernel PAID but the mirror could not be saved; record it by hand", cmd.disputeId(),
                paid.id(), cmd.amount(), cmd.currency(), e));
    }

    private static TransactionDefinition requiresNew() {
        DefaultTransactionDefinition d = new DefaultTransactionDefinition();
        d.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return d;
    }

    private static Throwable kernelError(Throwable e) {
        if (e instanceof WalletSettlementException) {
            return e;
        }
        if (e instanceof WebClientResponseException w && w.getStatusCode().is4xxClientError()) {
            return new WalletSettlementException(WalletSettlementException.KERNEL_REFUSED, 409,
                    "The Kernel refused the compensation: " + w.getStatusCode().value() + " "
                            + w.getResponseBodyAsString(), e);
        }
        return new WalletSettlementException(WalletSettlementException.KERNEL_UNAVAILABLE, 503,
                "The Kernel payment service is unavailable: " + e.getMessage(), e);
    }
}
