package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ICashCollectionUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ICompensationUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IDebtUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IEscrowUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IDebtRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IKernelSettlementPort;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.CashCollection;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.CashCollectionRequest;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Challenge;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Fund;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Share;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletDto;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowShares;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletCompensation;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletDebt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("Lot L5: debts towards the compensation fund (QO-22, QO-26) and returns paid by the fund")
class DebtAndFundedReturnTest {

    static final UUID TENANT = UUID.randomUUID();
    static final UUID COURIER = UUID.randomUUID();
    static final UUID COURIER_WALLET = UUID.randomUUID();
    static final UUID FUND_WALLET = UUID.randomUUID();

    final IKernelSettlementPort kernel = mock(IKernelSettlementPort.class);
    final InMemoryDebts repo = new InMemoryDebts();
    DebtService debts;

    static final class InMemoryDebts implements IDebtRepository {
        final Map<UUID, WalletDebt> rows = new ConcurrentHashMap<>();

        public Mono<WalletDebt> findById(UUID id) {
            return Mono.justOrEmpty(rows.get(id));
        }

        public Mono<WalletDebt> findByReference(String reference) {
            return Flux.fromIterable(rows.values()).filter(d -> d.reference().equals(reference)).next();
        }

        public Mono<WalletDebt> save(WalletDebt debt, boolean isNew) {
            if (isNew && rows.values().stream().anyMatch(d -> d.reference().equals(debt.reference()))) {
                return Mono.error(new IllegalStateException("duplicate reference"));
            }
            rows.put(debt.id(), debt);
            return Mono.just(debt);
        }

        public Flux<WalletDebt> byDebtor(UUID debtorId) {
            return Flux.fromIterable(rows.values()).filter(d -> debtorId.equals(d.debtorId()));
        }

        public Flux<WalletDebt> byStatus(WalletDebt.Status status, int limit) {
            return Flux.fromIterable(rows.values()).filter(d -> status == null || d.status() == status)
                    .sort(Comparator.comparing(WalletDebt::createdAt)).take(limit);
        }
    }

    @BeforeEach
    void setUp() {
        debts = new DebtService(kernel, repo);
        when(kernel.walletOfOwner(COURIER)).thenReturn(Mono.just(new KernelWalletDto(COURIER_WALLET, COURIER, "USER",
                "c", BigDecimal.ZERO, null, null)));
        when(kernel.compensationFund()).thenReturn(Mono.just(new Fund(FUND_WALLET, new BigDecimal("1000000"))));
        when(kernel.openCashCollection(any())).thenAnswer(inv -> {
            CashCollectionRequest r = inv.getArgument(0);
            return Mono.just(collection(r.subjectId(), "OPEN"));
        });
        when(kernel.markCollected(any(), any())).thenAnswer(inv -> Mono.just(collection(inv.getArgument(0),
                "COLLECTED")));
        when(kernel.remitChallenge(any())).thenReturn(Mono.just(new Challenge("tok", 300L, "***9")));
        when(kernel.remit(any(), eq("tok"), eq("4242"))).thenAnswer(inv -> Mono.just(collection(inv.getArgument(0),
                "REMITTED")));
        when(kernel.cancelCashCollection(any())).thenAnswer(inv -> Mono.just(collection(inv.getArgument(0),
                "CANCELLED")));
    }

    static CashCollection collection(UUID id, String status) {
        return new CashCollection(id, "DEBT", id, COURIER_WALLET, new BigDecimal("15000"), null, null, "XAF", status,
                List.of(), Instant.now(), null, null);
    }

    static IDebtUseCase.NewDebt debt(WalletDebt.DebtorType type, UUID debtor, String reference) {
        return new IDebtUseCase.NewDebt(TENANT, type, debtor, WalletDebt.Reason.DISPUTE_COMPENSATION, reference,
                UUID.randomUUID(), new BigDecimal("15000"), "XAF");
    }

    @Test
    @DisplayName("recorded OPEN, recovered like unpaid cash: a DEBT collection on the debtor's wallet, one PLATFORM share to the fund, collected at once; idempotent per reference")
    void recordedAndRecoveryOpened() {
        WalletDebt d = debts.record(debt(WalletDebt.DebtorType.COURIER, COURIER, "DISPUTE:1")).block();
        assertThat(d.status()).isEqualTo(WalletDebt.Status.OPEN);
        assertThat(d.kernelCollectionId()).isNotNull();
        assertThat(d.debtorWalletId()).isEqualTo(COURIER_WALLET);
        ArgumentCaptor<CashCollectionRequest> sent = ArgumentCaptor.forClass(CashCollectionRequest.class);
        verify(kernel).openCashCollection(sent.capture());
        assertThat(sent.getValue().subjectType()).isEqualTo("DEBT");
        assertThat(sent.getValue().subjectId()).isEqualTo(d.id());
        assertThat(sent.getValue().courierWalletId()).isEqualTo(COURIER_WALLET);
        assertThat(sent.getValue().shares()).containsExactly(new Share(Share.PLATFORM, FUND_WALLET,
                new BigDecimal("15000")));
        verify(kernel).markCollected(d.kernelCollectionId(), new BigDecimal("15000"));

        assertThat(debts.record(debt(WalletDebt.DebtorType.COURIER, COURIER, "DISPUTE:1")).block().id())
                .isEqualTo(d.id());
        verify(kernel, times(1)).openCashCollection(any());
    }

    @Test
    @DisplayName("only the debtor repays, with the Kernel code: REPAID; a second repay is a no-op")
    void repaidByTheDebtor() {
        WalletDebt d = debts.record(debt(WalletDebt.DebtorType.COURIER, COURIER, "DISPUTE:2")).block();
        StepVerifier.create(debts.repayChallenge(d.id(), UUID.randomUUID(), null))
                .expectErrorMatches(e -> e instanceof WalletSettlementException w && w.getHttpStatus() == 403).verify();
        assertThat(debts.repayChallenge(d.id(), COURIER, null).block().challengeToken()).isEqualTo("tok");
        assertThat(debts.repay(d.id(), COURIER, null, "tok", "4242").block().status())
                .isEqualTo(WalletDebt.Status.REPAID);
        assertThat(debts.repay(d.id(), COURIER, null, "tok", "4242").block().status())
                .isEqualTo(WalletDebt.Status.REPAID);
        verify(kernel, times(1)).remit(any(), any(), any());
        assertThat(debts.mine(COURIER, null).collectList().block()).extracting(WalletDebt::id).contains(d.id());
    }

    @Test
    @DisplayName("relay/agency debt without debtor: OPEN, no recovery; the administrator names the debtor (an organisation) which then repays")
    void organisationDebtNamedByTheAdministrator() {
        WalletDebt d = debts.record(debt(WalletDebt.DebtorType.RELAY, null, "DISPUTE:3")).block();
        assertThat(d.kernelCollectionId()).isNull();
        verify(kernel, never()).openCashCollection(any());
        StepVerifier.create(debts.repayChallenge(d.id(), COURIER, null))
                .expectErrorMatches(e -> e instanceof WalletSettlementException w && w.getHttpStatus() == 403).verify();

        UUID relayOrg = UUID.randomUUID();
        when(kernel.walletOfOwner(relayOrg)).thenReturn(Mono.just(new KernelWalletDto(UUID.randomUUID(), relayOrg,
                "ORGANIZATION", "relay", BigDecimal.ZERO, null, null)));
        WalletDebt named = debts.assign(d.id(), relayOrg).block();
        assertThat(named.debtorId()).isEqualTo(relayOrg);
        assertThat(named.kernelCollectionId()).isNotNull();
        StepVerifier.create(debts.assign(d.id(), UUID.randomUUID()))
                .expectErrorMatches(e -> e instanceof WalletSettlementException w && w.getHttpStatus() == 409).verify();
        // the manager acting for the relay organisation repays it
        UUID manager = UUID.randomUUID();
        assertThat(debts.repay(d.id(), manager, relayOrg, "tok", "4242").block().status())
                .isEqualTo(WalletDebt.Status.REPAID);
    }

    @Test
    @DisplayName("a recovery that cannot be opened (no wallet) leaves the debt OPEN; the administrator retries; a write-off cancels the Kernel collection")
    void retryAndWriteOff() {
        UUID broke = UUID.randomUUID();
        when(kernel.walletOfOwner(broke)).thenReturn(Mono.empty());
        WalletDebt d = debts.record(debt(WalletDebt.DebtorType.COURIER, broke, "DISPUTE:4")).block();
        assertThat(d.status()).isEqualTo(WalletDebt.Status.OPEN);
        assertThat(d.kernelCollectionId()).isNull();

        when(kernel.walletOfOwner(broke)).thenReturn(Mono.just(new KernelWalletDto(UUID.randomUUID(), broke, "USER",
                "b", BigDecimal.ZERO, null, null)));
        WalletDebt retried = debts.retryRecovery(d.id()).block();
        assertThat(retried.kernelCollectionId()).isNotNull();

        WalletDebt off = debts.writeOff(d.id(), "insolvent").block();
        assertThat(off.status()).isEqualTo(WalletDebt.Status.CANCELLED);
        assertThat(off.note()).isEqualTo("WRITTEN_OFF: insolvent");
        verify(kernel).cancelCashCollection(retried.kernelCollectionId());
        assertThat(debts.list(WalletDebt.Status.CANCELLED, 10).collectList().block()).extracting(WalletDebt::id)
                .containsExactly(d.id());
    }

    @Test
    @DisplayName("PENDING until the fund pays: activated with the amount paid; cancelled when the return does not happen")
    void pendingDebts() {
        IDebtUseCase.NewDebt pending = new IDebtUseCase.NewDebt(TENANT, WalletDebt.DebtorType.COURIER, COURIER,
                WalletDebt.Reason.RETURN_COST, "RETURN:a", null, new BigDecimal("2700"), "XAF");
        assertThat(debts.recordPending(pending).block().status()).isEqualTo(WalletDebt.Status.PENDING);
        verify(kernel, never()).openCashCollection(any());
        WalletDebt open = debts.activate("RETURN:a", new BigDecimal("2650")).block();
        assertThat(open.status()).isEqualTo(WalletDebt.Status.OPEN);
        assertThat(open.amount()).isEqualByComparingTo("2650");
        assertThat(open.kernelCollectionId()).isNotNull();

        debts.recordPending(new IDebtUseCase.NewDebt(TENANT, WalletDebt.DebtorType.COURIER, COURIER,
                WalletDebt.Reason.RETURN_COST, "RETURN:b", null, new BigDecimal("2700"), "XAF")).block();
        assertThat(debts.cancelPending("RETURN:b", "RETURN_CANCELLED").block().status())
                .isEqualTo(WalletDebt.Status.CANCELLED);
        assertThat(debts.cancelPending("RETURN:a", "late").block().status()).isEqualTo(WalletDebt.Status.OPEN);
    }

    // ── lifecycle ───────────────────────────────────────────────────────────

    final IEscrowUseCase escrows = mock(IEscrowUseCase.class);
    final ICashCollectionUseCase cash = mock(ICashCollectionUseCase.class);
    final ICompensationUseCase compensations = mock(ICompensationUseCase.class);
    final IDebtUseCase debtPort = mock(IDebtUseCase.class);

    PaymentLifecycleService lifecycle() {
        when(escrows.apply(any(), any(), any())).thenReturn(Mono.empty());
        when(escrows.find(any())).thenReturn(Mono.empty());
        when(cash.collected(any(), any())).thenReturn(Mono.empty());
        when(cash.cancel(any())).thenReturn(Mono.empty());
        return new PaymentLifecycleService(escrows, cash, compensations, debtPort);
    }

    static PaymentIntent intent(UUID delivery, PaymentIntent.Mode mode) {
        return new PaymentIntent(UUID.randomUUID(), TENANT, delivery, UUID.randomUUID(), UUID.randomUUID(), mode,
                new BigDecimal("3000"), "XAF", new EscrowShares(new BigDecimal("3000"), new BigDecimal("150"),
                List.of(), new BigDecimal("2850")), UUID.randomUUID(), null, COURIER, COURIER_WALLET,
                UUID.randomUUID(), null, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("QO-22: a FUND return delivered → the fund pays its courier the courier share (RETURN:<id>) and the PENDING debt opens")
    void fundedReturnDelivered() {
        PaymentLifecycleService l = lifecycle();
        UUID ret = UUID.randomUUID();
        when(escrows.intentOfDelivery(ret)).thenReturn(Mono.just(intent(ret, PaymentIntent.Mode.FUND)));
        when(compensations.payReturnCourier(TENANT, ret, COURIER, new BigDecimal("2850"), "XAF"))
                .thenReturn(Mono.just(new WalletCompensation(UUID.randomUUID(), TENANT, "RETURN:" + ret,
                        UUID.randomUUID(), FUND_WALLET, COURIER, COURIER_WALLET, new BigDecimal("2850"), "XAF",
                        Instant.now())));
        when(debtPort.activate(any(), any())).thenReturn(Mono.empty());

        l.delivered(ret, new BigDecimal("3000")).block();
        verify(debtPort).activate("RETURN:" + ret, new BigDecimal("2850"));

        // a fund failure never fails the consumer: logged for the administrator
        UUID other = UUID.randomUUID();
        when(escrows.intentOfDelivery(other)).thenReturn(Mono.just(intent(other, PaymentIntent.Mode.FUND)));
        when(compensations.payReturnCourier(any(), eq(other), any(), any(), any()))
                .thenReturn(Mono.error(new WalletSettlementException("COMPENSATION_FUND_INSUFFICIENT", 409, "short")));
        l.delivered(other, null).block();
        verify(debtPort, never()).activate(eq("RETURN:" + other), any());
    }

    @Test
    @DisplayName("a FUND return cancelled or failed: its PENDING debt is cancelled; an escrow delivery has no fund effect")
    void fundedReturnAbandoned() {
        PaymentLifecycleService l = lifecycle();
        UUID ret = UUID.randomUUID();
        when(escrows.intentOfDelivery(ret)).thenReturn(Mono.just(intent(ret, PaymentIntent.Mode.FUND)));
        when(debtPort.cancelPending(any(), any())).thenReturn(Mono.empty());
        l.cancelled(ret, "ASSIGNED", "SENDER").block();
        verify(debtPort).cancelPending("RETURN:" + ret, "RETURN_CANCELLED");

        UUID normal = UUID.randomUUID();
        when(escrows.intentOfDelivery(normal)).thenReturn(Mono.just(intent(normal, PaymentIntent.Mode.ESCROW)));
        l.delivered(normal, null).block();
        l.failed(normal, "MAX_ATTEMPTS_REACHED", "RECIPIENT").block();
        verifyNoInteractions(compensations);
        verify(debtPort, never()).cancelPending(eq("RETURN:" + normal), any());
    }

    @Test
    @DisplayName("QO-26: a compensation with a courier at fault is the delivery courier's debt; relay/agency without debtor; sender/platform: none")
    void disputeCompensatedDebts() {
        PaymentLifecycleService l = lifecycle();
        UUID delivery = UUID.randomUUID();
        UUID dispute = UUID.randomUUID();
        when(escrows.intentOfDelivery(delivery)).thenReturn(Mono.just(intent(delivery, PaymentIntent.Mode.ESCROW)));
        when(debtPort.record(any())).thenAnswer(inv -> Mono.just(mock(WalletDebt.class)));

        l.disputeCompensated(TENANT, delivery, dispute, "COURIER", new BigDecimal("15000"), "XAF").block();
        l.disputeCompensated(TENANT, delivery, UUID.randomUUID(), "AGENCY", new BigDecimal("500"), "XAF").block();
        l.disputeCompensated(TENANT, delivery, UUID.randomUUID(), "PLATFORM", new BigDecimal("500"), "XAF").block();
        l.disputeCompensated(TENANT, delivery, UUID.randomUUID(), "SENDER", new BigDecimal("500"), "XAF").block();

        ArgumentCaptor<IDebtUseCase.NewDebt> sent = ArgumentCaptor.forClass(IDebtUseCase.NewDebt.class);
        verify(debtPort, times(2)).record(sent.capture());
        assertThat(sent.getAllValues().get(0).debtorId()).isEqualTo(COURIER);
        assertThat(sent.getAllValues().get(0).reference()).isEqualTo("DISPUTE:" + dispute);
        assertThat(sent.getAllValues().get(0).amount()).isEqualByComparingTo("15000");
        assertThat(sent.getAllValues().get(1).debtorType()).isEqualTo(WalletDebt.DebtorType.AGENCY);
        assertThat(sent.getAllValues().get(1).debtorId()).isNull();
    }
}
