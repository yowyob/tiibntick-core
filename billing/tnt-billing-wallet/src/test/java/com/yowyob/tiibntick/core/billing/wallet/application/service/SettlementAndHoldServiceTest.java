package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.RecordSettlementCommand;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.CommissionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementMethod;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus;
import com.yowyob.tiibntick.core.billing.wallet.support.InMemoryWalletRepositories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementAndHoldServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID DELIVERY = UUID.randomUUID();
    private static final UUID PAYER = UUID.randomUUID();
    private static final UUID PAYEE = UUID.randomUUID();

    private final InMemoryWalletRepositories.Settlements settlementRepo = new InMemoryWalletRepositories.Settlements();
    private final InMemoryWalletRepositories.Holds holdRepo = new InMemoryWalletRepositories.Holds();
    private final SettlementService settlements = new SettlementService(settlementRepo);
    private final WalletHoldService holds = new WalletHoldService(holdRepo, settlementRepo);

    private RecordSettlementCommand cmd(SettlementMethod m, SettlementStatus st, String rate) {
        return new RecordSettlementCommand(TENANT, "DELIVERY", DELIVERY, PAYER, PAYEE,
                new BigDecimal("5000"), "XAF", m, st, "tx-1", new BigDecimal(rate));
    }

    @Test
    @DisplayName("a transfer settlement is PAID with the 5% commission tracked as DUE")
    void transferSettlementTracksCommissionDue() {
        var s = settlements.record(cmd(SettlementMethod.WALLET_TRANSFER, SettlementStatus.PAID, "0.05")).block();
        assertThat(s.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(s.getCommissionAmount()).isEqualByComparingTo("250.00");
        assertThat(s.getCommissionStatus()).isEqualTo(CommissionStatus.DUE);
        assertThat(s.getPaidAt()).isNotNull();
        assertThat(settlements.listCommissionsDue(TENANT).collectList().block()).hasSize(1);
    }

    @Test
    @DisplayName("an UNPAID report carries no commission yet and never downgrades a PAID settlement")
    void unpaidThenPaidThenUnpaid() {
        var unpaid = settlements.record(cmd(SettlementMethod.WALLET_TRANSFER, SettlementStatus.UNPAID, "0.05")).block();
        assertThat(unpaid.getCommissionStatus()).isEqualTo(CommissionStatus.NONE);

        var paid = settlements.record(cmd(SettlementMethod.WALLET_TRANSFER, SettlementStatus.PAID, "0.05")).block();
        assertThat(paid.getId()).isEqualTo(unpaid.getId());
        assertThat(paid.getCommissionStatus()).isEqualTo(CommissionStatus.DUE);

        var again = settlements.record(cmd(SettlementMethod.WALLET_TRANSFER, SettlementStatus.UNPAID, "0.05")).block();
        assertThat(again.getStatus()).isEqualTo(SettlementStatus.PAID);
    }

    @Test
    @DisplayName("cash collected → commission DUE; settling it removes it from the due list")
    void cashCommissionDueThenSettled() {
        var s = settlements.record(cmd(SettlementMethod.CASH, SettlementStatus.CASH_COLLECTED, "0.05")).block();
        assertThat(s.getCommissionStatus()).isEqualTo(CommissionStatus.DUE);
        settlements.markCommissionSettled(TENANT, s.getId()).block();
        assertThat(settlements.listCommissionsDue(TENANT).collectList().block()).isEmpty();
        // a late re-record must not resurrect a settled commission
        var re = settlements.record(cmd(SettlementMethod.CASH, SettlementStatus.CASH_COLLECTED, "0.05")).block();
        assertThat(re.getCommissionStatus()).isEqualTo(CommissionStatus.SETTLED);
    }

    @Test
    @DisplayName("holdSettlement holds the received amount of the PAYEE, is idempotent, and release frees it")
    void holdIsIdempotentAndReleasable() {
        settlements.record(cmd(SettlementMethod.WALLET_TRANSFER, SettlementStatus.PAID, "0.05")).block();

        var h1 = holds.holdSettlement("DELIVERY", DELIVERY, "dispute").block();
        var h2 = holds.holdSettlement("DELIVERY", DELIVERY, "dispute again").block();
        assertThat(h1.getOwnerUserId()).isEqualTo(PAYEE);
        assertThat(h2.getId()).isEqualTo(h1.getId());
        assertThat(holds.activeHeldAmount(PAYEE, null).block()).isEqualByComparingTo("5000");

        assertThat(holds.releaseHolds("DELIVERY", DELIVERY).block()).isEqualTo(1L);
        assertThat(holds.activeHeldAmount(PAYEE, null).block()).isEqualByComparingTo("0");
        assertThat(holds.releaseHolds("DELIVERY", DELIVERY).block()).isEqualTo(0L);
    }

    @Test
    @DisplayName("nothing to hold when the mission was never paid through the Kernel wallet (cash / unpaid / unknown)")
    void noHoldWithoutPaidSettlement() {
        settlements.record(cmd(SettlementMethod.CASH, SettlementStatus.CASH_COLLECTED, "0.05")).block();
        assertThat(holds.holdSettlement("DELIVERY", DELIVERY, "x").block()).isNull();
        assertThat(holds.holdSettlement("DELIVERY", UUID.randomUUID(), "x").block()).isNull();
    }
}
