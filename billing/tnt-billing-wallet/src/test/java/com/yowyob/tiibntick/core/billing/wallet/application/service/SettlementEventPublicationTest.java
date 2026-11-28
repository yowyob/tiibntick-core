package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.RecordSettlementCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ISettlementEventPublisher;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementMethod;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
import com.yowyob.tiibntick.core.billing.wallet.support.InMemoryWalletRepositories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SettlementService — publishes payment.confirmed / commission.calculated on transitions only (R1)")
class SettlementEventPublicationTest {

    /** Records what would be published. */
    static final class Recorder implements ISettlementEventPublisher {
        final List<String> events = new ArrayList<>();
        boolean failing;

        @Override
        public Mono<Void> paymentConfirmed(WalletSettlement s) {
            if (failing) {
                return Mono.error(new IllegalStateException("outbox down"));
            }
            events.add("payment.confirmed:" + s.getAmount());
            return Mono.empty();
        }

        @Override
        public Mono<Void> commissionCalculated(WalletSettlement s) {
            if (failing) {
                throw new IllegalStateException("thrown, not signalled");
            }
            events.add("commission.calculated:" + s.getCommissionAmount());
            return Mono.empty();
        }
    }

    private final UUID tenant = UUID.randomUUID();
    private final UUID delivery = UUID.randomUUID();
    private final Recorder recorder = new Recorder();
    private final SettlementService service =
            new SettlementService(new InMemoryWalletRepositories.Settlements(), recorder);

    private RecordSettlementCommand cmd(SettlementMethod method, SettlementStatus status, String amount, String rate) {
        return new RecordSettlementCommand(tenant, "DELIVERY", delivery, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal(amount), "XAF", method, status, "tx-1", new BigDecimal(rate));
    }

    @Test
    @DisplayName("a wallet transfer recorded PAID publishes the payment and the 5% commission, once")
    void paidPublishesBothOnce() {
        service.record(cmd(SettlementMethod.WALLET_TRANSFER, SettlementStatus.PAID, "5000", "0.05")).block();
        assertThat(recorder.events).containsExactly("payment.confirmed:5000", "commission.calculated:250.00");

        service.record(cmd(SettlementMethod.WALLET_TRANSFER, SettlementStatus.PAID, "5000", "0.05")).block();
        assertThat(recorder.events).as("a retried, unchanged upsert publishes nothing").hasSize(2);
    }

    @Test
    @DisplayName("UNPAID publishes nothing; the later PAID transition publishes both")
    void unpaidThenPaid() {
        service.record(cmd(SettlementMethod.WALLET_TRANSFER, SettlementStatus.UNPAID, "5000", "0.05")).block();
        assertThat(recorder.events).isEmpty();
        service.record(cmd(SettlementMethod.WALLET_TRANSFER, SettlementStatus.PAID, "5000", "0.05")).block();
        assertThat(recorder.events).containsExactly("payment.confirmed:5000", "commission.calculated:250.00");
    }

    @Test
    @DisplayName("cash collected: commission due, but no payment.confirmed (no money moved on the Kernel wallet)")
    void cashPublishesCommissionOnly() {
        service.record(cmd(SettlementMethod.CASH, SettlementStatus.CASH_COLLECTED, "3000", "0.05")).block();
        assertThat(recorder.events).containsExactly("commission.calculated:150.00");
    }

    @Test
    @DisplayName("no commission (rate 0, e.g. market orders): only the payment")
    void noCommission() {
        service.record(cmd(SettlementMethod.EXTERNAL_PROVIDER, SettlementStatus.PAID, "1000", "0")).block();
        assertThat(recorder.events).containsExactly("payment.confirmed:1000");
    }

    @Test
    @DisplayName("a changed DUE amount republishes the commission")
    void changedCommissionRepublished() {
        service.record(cmd(SettlementMethod.CASH, SettlementStatus.CASH_COLLECTED, "3000", "0.05")).block();
        service.record(cmd(SettlementMethod.CASH, SettlementStatus.CASH_COLLECTED, "4000", "0.05")).block();
        assertThat(recorder.events).containsExactly("commission.calculated:150.00", "commission.calculated:200.00");
    }

    @Test
    @DisplayName("lot L6: cash remitted (K4) → the commission turns SETTLED and is published again, once (accounting books it)")
    void remittedCommissionRepublishedSettled() {
        WalletSettlement s = service.record(cmd(SettlementMethod.CASH, SettlementStatus.CASH_COLLECTED, "3000", "0.05"))
                .block();
        service.markCommissionSettled(tenant, s.getId()).block();
        service.markCommissionSettled(tenant, s.getId()).block();
        assertThat(recorder.events).containsExactly("commission.calculated:150.00", "commission.calculated:150.00");
        assertThat(service.find(tenant, "DELIVERY", delivery).block().getCommissionStatus())
                .isEqualTo(com.yowyob.tiibntick.core.billing.wallet.domain.enums.CommissionStatus.SETTLED);
    }

    @Test
    @DisplayName("an event that cannot be enqueued never fails the settlement (money already moved)")
    void publicationFailureDoesNotFailTheSettlement() {
        recorder.failing = true;
        WalletSettlement s = service.record(cmd(SettlementMethod.WALLET_TRANSFER, SettlementStatus.PAID, "5000", "0.05"))
                .block();
        assertThat(s).isNotNull();
        assertThat(s.getStatus()).isEqualTo(SettlementStatus.PAID);
    }
}
