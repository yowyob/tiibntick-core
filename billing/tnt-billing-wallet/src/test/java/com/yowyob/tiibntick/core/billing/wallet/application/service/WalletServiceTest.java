package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.ConfirmPaymentCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.CreditWalletCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.*;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentIntentStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WalletService.
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService Tests")
class WalletServiceTest {

    @Mock private IWalletRepository walletRepository;
    @Mock private IPaymentIntentRepository paymentIntentRepository;
    @Mock private IMoMoPaymentPort moMoPaymentPort;
    @Mock private IIdempotencyStore idempotencyStore;
    @Mock private IWalletEventPublisher eventPublisher;
    @Mock private IWalletNotificationPort notificationPort;
    @Mock private IPaymentAnchorPort paymentAnchorPort;

    private WalletService walletService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final Currency XAF = Currency.getInstance("XAF");

    @BeforeEach
    void setUp() {
        walletService = new WalletService(
                walletRepository, paymentIntentRepository,
                moMoPaymentPort, idempotencyStore,
                eventPublisher, notificationPort, paymentAnchorPort);
        lenient().when(walletRepository.save(any(Wallet.class)))
            .thenReturn(Mono.just(Wallet.createNew(USER_ID, TENANT_ID, XAF)));
        lenient().when(paymentAnchorPort.anchor(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("getOrCreateWallet creates new wallet when not found")
    void createWalletWhenNotFound() {
        //Wallet newWallet = Wallet.createNew(USER_ID, TENANT_ID, XAF);
        when(walletRepository.findByUserId(USER_ID, TENANT_ID)).thenReturn(Mono.empty());
        //when(walletRepository.save(any(Wallet.class))).thenReturn(Mono.just(newWallet));

        StepVerifier.create(walletService.getOrCreateWallet(USER_ID, TENANT_ID))
                .expectNextMatches(w -> w.getUserId().equals(USER_ID))
                .verifyComplete();
    }

    @Test
    @DisplayName("getOrCreateWallet returns existing wallet when found")
    void returnsExistingWallet() {
        Wallet existing = Wallet.createNew(USER_ID, TENANT_ID, XAF);
        when(walletRepository.findByUserId(USER_ID, TENANT_ID)).thenReturn(Mono.just(existing));

        StepVerifier.create(walletService.getOrCreateWallet(USER_ID, TENANT_ID))
                .expectNext(existing)
                .verifyComplete();

        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("creditWallet increases balance and saves transaction")
    void creditWalletIncreasesBalance() {
        Wallet wallet = Wallet.createNew(USER_ID, TENANT_ID, XAF);
        when(walletRepository.findByUserId(USER_ID, TENANT_ID)).thenReturn(Mono.just(wallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(Mono.just(wallet));

        WalletTransaction mockTx = wallet.credit(Money.ofXAF(5000), "REF-001", "Test");
        when(walletRepository.saveTransaction(any())).thenReturn(Mono.just(mockTx));
        when(eventPublisher.publish(any(
                com.yowyob.tiibntick.core.billing.wallet.domain.event.WalletCredited.class)))
                .thenReturn(Mono.empty());

        CreditWalletCommand cmd = new CreditWalletCommand(
                USER_ID, TENANT_ID, Money.ofXAF(5000), "REF-001", "Test credit");

        StepVerifier.create(walletService.creditWallet(cmd))
                .expectNextMatches(tx -> tx.getAmount().equals(Money.ofXAF(5000)))
                .verifyComplete();
    }

    private PaymentIntent samplePendingIntent(Wallet wallet) {
        return PaymentIntent.builder()
                .id(PaymentIntentId.generate())
                .walletId(wallet.getId())
                .amount(Money.ofXAF(5000))
                .channel(PaymentChannel.MTN_MOMO)
                .status(PaymentIntentStatus.PENDING)
                .idempotencyKey("IDEMP-001")
                .externalRef("EXT-REF-001")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("handlePaymentCallback anchors the committed payment on-chain")
    void handlePaymentCallbackAnchorsOnChain() {
        Wallet wallet = Wallet.createNew(USER_ID, TENANT_ID, XAF);
        PaymentIntent intent = samplePendingIntent(wallet);

        when(paymentIntentRepository.findByExternalRef("EXT-REF-001")).thenReturn(Mono.just(intent));
        when(walletRepository.findById(wallet.getId())).thenReturn(Mono.just(wallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(Mono.just(wallet));
        when(paymentIntentRepository.save(any())).thenReturn(Mono.just(intent));
        when(idempotencyStore.release("IDEMP-001")).thenReturn(Mono.empty());
        when(eventPublisher.publish(any(
                com.yowyob.tiibntick.core.billing.wallet.domain.event.PaymentConfirmed.class)))
                .thenReturn(Mono.empty());
        when(notificationPort.sendPaymentConfirmed(any(), any())).thenReturn(Mono.empty());

        ConfirmPaymentCommand cmd = new ConfirmPaymentCommand(
                "EXT-REF-001", "FIN-TX-001", PaymentChannel.MTN_MOMO, "SUCCESSFUL", null);

        StepVerifier.create(walletService.handlePaymentCallback(cmd))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<PaymentAnchorPayload> captor = ArgumentCaptor.forClass(PaymentAnchorPayload.class);
        verify(paymentAnchorPort).anchor(captor.capture());
        PaymentAnchorPayload payload = captor.getValue();

        assertThat(payload.tenantId()).isEqualTo(TENANT_ID);
        assertThat(payload.walletId()).isEqualTo(wallet.getId().value());
        assertThat(payload.paymentIntentId()).isEqualTo(intent.getId().value());
        assertThat(payload.channel()).isEqualTo("MTN_MOMO");
        assertThat(payload.externalRef()).isEqualTo("FIN-TX-001");
    }

    @Test
    @DisplayName("handlePaymentCallback succeeds even when on-chain anchoring fails")
    void handlePaymentCallbackSucceedsWhenAnchoringFails() {
        Wallet wallet = Wallet.createNew(USER_ID, TENANT_ID, XAF);
        PaymentIntent intent = samplePendingIntent(wallet);

        when(paymentIntentRepository.findByExternalRef("EXT-REF-001")).thenReturn(Mono.just(intent));
        when(walletRepository.findById(wallet.getId())).thenReturn(Mono.just(wallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(Mono.just(wallet));
        when(paymentIntentRepository.save(any())).thenReturn(Mono.just(intent));
        when(idempotencyStore.release("IDEMP-001")).thenReturn(Mono.empty());
        when(eventPublisher.publish(any(
                com.yowyob.tiibntick.core.billing.wallet.domain.event.PaymentConfirmed.class)))
                .thenReturn(Mono.empty());
        when(notificationPort.sendPaymentConfirmed(any(), any())).thenReturn(Mono.empty());
        when(paymentAnchorPort.anchor(any())).thenReturn(Mono.error(new RuntimeException("trust unavailable")));

        ConfirmPaymentCommand cmd = new ConfirmPaymentCommand(
                "EXT-REF-001", "FIN-TX-001", PaymentChannel.MTN_MOMO, "SUCCESSFUL", null);

        StepVerifier.create(walletService.handlePaymentCallback(cmd))
                .expectNextCount(1)
                .verifyComplete();
    }
}
