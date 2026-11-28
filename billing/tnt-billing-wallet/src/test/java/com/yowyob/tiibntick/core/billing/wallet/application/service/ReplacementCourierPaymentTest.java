package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ICashCollectionUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ICompensationUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IDebtUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IEscrowUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ICourierLegRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.CourierLeg;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowMirror;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("QO-11 / D-153: at the escrow release, the fund pays the replacement courier and the first courier owes it")
class ReplacementCourierPaymentTest {

    static final UUID TENANT = UUID.randomUUID();
    static final UUID FIRST = UUID.randomUUID();
    static final UUID REPLACEMENT = UUID.randomUUID();

    final IEscrowUseCase escrows = mock(IEscrowUseCase.class);
    final ICashCollectionUseCase cash = mock(ICashCollectionUseCase.class);
    final ICompensationUseCase compensations = mock(ICompensationUseCase.class);
    final IDebtUseCase debts = mock(IDebtUseCase.class);
    final InMemoryLegs legs = new InMemoryLegs();
    PaymentLifecycleService lifecycle;
    UUID delivery;

    static final class InMemoryLegs implements ICourierLegRepository {
        final List<CourierLeg> rows = new ArrayList<>();

        public Mono<Void> insertIfAbsent(CourierLeg leg) {
            if (rows.stream().noneMatch(r -> r.eventId().equals(leg.eventId()))) {
                rows.add(leg);
            }
            return Mono.empty();
        }

        public Flux<CourierLeg> byDelivery(UUID deliveryId) {
            return Flux.fromIterable(rows).filter(r -> r.deliveryId().equals(deliveryId))
                    .sort(Comparator.comparingLong(CourierLeg::aggregateVersion));
        }
    }

    @BeforeEach
    void setUp() {
        delivery = UUID.randomUUID();
        lifecycle = new PaymentLifecycleService(escrows, cash, compensations, debts, legs);
        when(escrows.apply(any(), any(), any())).thenReturn(Mono.empty());
        when(cash.collected(any(), any())).thenReturn(Mono.empty());
        when(escrows.find(delivery)).thenReturn(Mono.just(escrow(EscrowMirror.Status.RELEASED)));
        when(escrows.intentOfDelivery(delivery)).thenReturn(Mono.just(new PaymentIntent(UUID.randomUUID(), TENANT,
                delivery, UUID.randomUUID(), UUID.randomUUID(), PaymentIntent.Mode.ESCROW, new BigDecimal("3000"),
                "XAF", new EscrowShares(new BigDecimal("3000"), new BigDecimal("150"), List.of(),
                new BigDecimal("2850")), UUID.randomUUID(), null, FIRST, UUID.randomUUID(), UUID.randomUUID(), null,
                Instant.now(), Instant.now())));
        when(compensations.payReplacementCourier(any(), any(), anyInt(), any(), any(), any())).thenAnswer(inv ->
                Mono.just(new WalletCompensation(UUID.randomUUID(), TENANT,
                        WalletDebt.replacementReference(inv.getArgument(1), inv.getArgument(2)), UUID.randomUUID(),
                        UUID.randomUUID(), inv.getArgument(3), UUID.randomUUID(), inv.getArgument(4), "XAF",
                        Instant.now())));
        when(debts.record(any())).thenAnswer(inv -> Mono.just(mock(WalletDebt.class)));
    }

    EscrowMirror escrow(EscrowMirror.Status status) {
        return new EscrowMirror(UUID.randomUUID(), TENANT, delivery, EscrowMirror.DELIVERY, UUID.randomUUID(), status,
                new BigDecimal("3000"), "XAF", UUID.randomUUID(), null, Instant.now(), Instant.now());
    }

    CourierLeg handover(String legKm, String remainingKm) {
        return new CourierLeg(null, TENANT, delivery, UUID.randomUUID(), 7, FIRST, REPLACEMENT,
                legKm == null ? null : new BigDecimal(legKm), new BigDecimal(remainingKm), null);
    }

    @Test
    @DisplayName("delivered: the replacement paid 70 % (REPLACEMENT:<id>:1), the first courier owes the same; a replay pays nothing more than the idempotent ports")
    void deliveredPaysTheReplacement() {
        CourierLeg leg = handover("3.000", "7.000");
        lifecycle.courierReplaced(leg).block();
        lifecycle.courierReplaced(leg).block(); // replayed event
        assertThat(legs.rows).hasSize(1);

        lifecycle.delivered(delivery, new BigDecimal("3000")).block();

        verify(compensations).payReplacementCourier(TENANT, delivery, 1, REPLACEMENT, new BigDecimal("1995"), "XAF");
        ArgumentCaptor<IDebtUseCase.NewDebt> debt = ArgumentCaptor.forClass(IDebtUseCase.NewDebt.class);
        verify(debts).record(debt.capture());
        assertThat(debt.getValue().debtorType()).isEqualTo(WalletDebt.DebtorType.COURIER);
        assertThat(debt.getValue().debtorId()).isEqualTo(FIRST);
        assertThat(debt.getValue().reason()).isEqualTo(WalletDebt.Reason.COURIER_REPLACEMENT);
        assertThat(debt.getValue().reference()).isEqualTo("REPLACEMENT:" + delivery + ":1");
        assertThat(debt.getValue().reference()).hasSizeLessThanOrEqualTo(64);
        assertThat(debt.getValue().amount()).isEqualByComparingTo("1995");
    }

    @Test
    @DisplayName("deposited at the destination relay releases too: same payment")
    void destinationRelayPaysTheReplacement() {
        lifecycle.courierReplaced(handover("3.000", "7.000")).block();
        lifecycle.depositedAtRelay(delivery, true).block();
        verify(compensations).payReplacementCourier(TENANT, delivery, 1, REPLACEMENT, new BigDecimal("1995"), "XAF");
    }

    @Test
    @DisplayName("escrow not released (refunded, frozen), no change of courier or undecided split: no payment")
    void nothingToPay() {
        lifecycle.delivered(delivery, null).block(); // no leg
        when(escrows.find(delivery)).thenReturn(Mono.just(escrow(EscrowMirror.Status.REFUNDED)));
        lifecycle.courierReplaced(handover("3.000", "7.000")).block();
        lifecycle.delivered(delivery, null).block();
        when(escrows.find(delivery)).thenReturn(Mono.just(escrow(EscrowMirror.Status.RELEASED)));
        legs.rows.clear();
        lifecycle.courierReplaced(handover(null, "7.000")).block(); // unknown point: the administrator splits
        lifecycle.delivered(delivery, null).block();
        verifyNoInteractions(compensations);
        verify(debts, never()).record(any());
    }

    @Test
    @DisplayName("a refused fund payment is left to the administrator; a Kernel outage (503) fails the record (retried)")
    void failures() {
        lifecycle.courierReplaced(handover("3.000", "7.000")).block();
        when(compensations.payReplacementCourier(any(), any(), anyInt(), any(), any(), any())).thenReturn(Mono.error(
                new WalletSettlementException(WalletSettlementException.FUND_INSUFFICIENT, 409, "short")));
        lifecycle.delivered(delivery, null).block();
        verify(debts, never()).record(any());

        when(compensations.payReplacementCourier(any(), any(), anyInt(), any(), any(), any())).thenReturn(Mono.error(
                new WalletSettlementException(WalletSettlementException.KERNEL_UNAVAILABLE, 503, "down")));
        StepVerifier.create(lifecycle.delivered(delivery, null))
                .expectErrorMatches(e -> e instanceof WalletSettlementException w && w.getHttpStatus() == 503)
                .verify();
    }
}
