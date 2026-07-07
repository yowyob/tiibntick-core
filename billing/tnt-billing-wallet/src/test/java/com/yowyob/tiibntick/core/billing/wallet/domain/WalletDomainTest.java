package com.yowyob.tiibntick.core.billing.wallet.domain;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.TransactionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.WalletStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.InsufficientBalanceException;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletFrozenException;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the Wallet aggregate root.
 * @author MANFOUO Braun
 */
@DisplayName("Wallet Aggregate Domain Tests")
class WalletDomainTest {

    private static final Currency XAF = Currency.getInstance("XAF");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = Wallet.createNew(USER_ID, TENANT_ID, XAF);
    }

    @Nested
    @DisplayName("Wallet creation")
    class Creation {
        @Test
        @DisplayName("should create wallet with zero balance")
        void createWithZeroBalance() {
            assertThat(wallet.getBalance().amount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(wallet.getReservedBalance().amount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
            assertThat(wallet.getCurrency()).isEqualTo(XAF);
        }
    }

    @Nested
    @DisplayName("Credit operations")
    class Credits {
        @Test
        @DisplayName("should increase balance on credit")
        void creditIncreasesBalance() {
            Money amount = Money.ofXAF(5000);
            wallet.credit(amount, "REF-001", "Top-up");
            assertThat(wallet.getBalance()).isEqualTo(Money.ofXAF(5000));
        }

        @Test
        @DisplayName("should emit WalletCredited event")
        void creditEmitsDomainEvent() {
            wallet.credit(Money.ofXAF(1000), "REF-001", "Test credit");
            assertThat(wallet.getPendingCreditEvents()).hasSize(1);
            assertThat(wallet.getPendingCreditEvents().get(0).creditedAmount())
                    .isEqualTo(Money.ofXAF(1000));
        }

        @Test
        @DisplayName("should reject credit on FROZEN wallet")
        void creditRejectedWhenFrozen() {
            wallet.freeze();
            assertThatThrownBy(() -> wallet.credit(Money.ofXAF(500), "REF-001", "Test"))
                    .isInstanceOf(WalletFrozenException.class);
        }
    }

    @Nested
    @DisplayName("Debit operations")
    class Debits {
        @BeforeEach
        void creditFirst() {
            wallet.credit(Money.ofXAF(10000), "SETUP-REF", "Initial balance");
            wallet.getPendingCreditEvents().clear();
        }

        @Test
        @DisplayName("should decrease balance on debit")
        void debitDecreasesBalance() {
            wallet.debit(Money.ofXAF(3000), "INV-001", PaymentChannel.WALLET,
                    "Payment", "KEY-001");
            assertThat(wallet.getBalance()).isEqualTo(Money.ofXAF(7000));
        }

        @Test
        @DisplayName("should throw InsufficientBalanceException when debit exceeds balance")
        void debitFailsWhenInsufficientBalance() {
            assertThatThrownBy(() -> wallet.debit(Money.ofXAF(99999), "INV-001",
                    PaymentChannel.WALLET, "Test", "KEY-002"))
                    .isInstanceOf(InsufficientBalanceException.class);
        }

        @Test
        @DisplayName("should create PENDING transaction for async MoMo payment")
        void createsPendingTransactionForMoMo() {
            WalletTransaction tx = wallet.createPendingDebit(
                    Money.ofXAF(2500), "INV-002", PaymentChannel.MTN_MOMO,
                    "MTN MoMo payment", "INV-002:MTN_MOMO");

            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING);
            assertThat(wallet.getBalance()).isEqualTo(Money.ofXAF(10000)); // not yet deducted
        }
    }

    @Nested
    @DisplayName("Reserve and release")
    class Reservations {
        @BeforeEach
        void creditFirst() {
            wallet.credit(Money.ofXAF(10000), "SETUP", "Initial balance");
            wallet.getPendingCreditEvents().clear();
        }

        @Test
        @DisplayName("should move amount from balance to reservedBalance on reserve")
        void reserveMovesFromBalance() {
            wallet.reserve(Money.ofXAF(3000), "MISSION-001");
            assertThat(wallet.getBalance()).isEqualTo(Money.ofXAF(7000));
            assertThat(wallet.getReservedBalance()).isEqualTo(Money.ofXAF(3000));
        }

        @Test
        @DisplayName("should restore balance on release")
        void releaseRestoresBalance() {
            wallet.reserve(Money.ofXAF(3000), "MISSION-001");
            wallet.releaseReservation(Money.ofXAF(3000), "MISSION-001");
            assertThat(wallet.getBalance()).isEqualTo(Money.ofXAF(10000));
            assertThat(wallet.getReservedBalance()).isEqualTo(Money.ofXAF(0));
        }
    }

    @Nested
    @DisplayName("Freeze and unfreeze")
    class FreezeOps {
        @Test
        @DisplayName("should set status to FROZEN on freeze")
        void freezeSetsStatus() {
            wallet.freeze();
            assertThat(wallet.getStatus()).isEqualTo(WalletStatus.FROZEN);
        }

        @Test
        @DisplayName("should restore ACTIVE status on unfreeze")
        void unfreezeRestoresActive() {
            wallet.freeze();
            wallet.unfreeze();
            assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Money value object")
    class MoneyTests {
        @Test
        @DisplayName("should correctly add two XAF amounts")
        void addMoneyCorrectly() {
            Money a = Money.ofXAF(1000);
            Money b = Money.ofXAF(500);
            assertThat(a.add(b)).isEqualTo(Money.ofXAF(1500));
        }

        @Test
        @DisplayName("should reject addition of different currencies")
        void rejectCurrencyMismatch() {
            Money xaf = Money.ofXAF(1000);
            Money usd = Money.of(BigDecimal.TEN, "USD");
            assertThatThrownBy(() -> xaf.add(usd)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should calculate 5% of 2000 XAF correctly")
        void percentageCalculation() {
            Money base = Money.ofXAF(2000);
            Money fivePercent = base.percentage(new BigDecimal("5"));
            assertThat(fivePercent).isEqualTo(Money.ofXAF(100));
        }
    }
}
