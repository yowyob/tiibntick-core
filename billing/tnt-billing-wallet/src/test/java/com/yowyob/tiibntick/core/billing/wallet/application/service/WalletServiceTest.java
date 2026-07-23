package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.dto.KernelPaymentOrderDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.ConfirmPaymentCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.CreditWalletCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.InitiatePaymentCommand;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
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
    @Mock private IIdempotencyStore idempotencyStore;
    @Mock private IWalletEventPublisher eventPublisher;
    @Mock private IWalletNotificationPort notificationPort;
    @Mock private IPaymentAnchorPort paymentAnchorPort;
    @Mock private IKernelPaymentGatewayPort kernelPaymentGatewayPort;

    private WalletService walletService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final Currency XAF = Currency.getInstance("XAF");

    @BeforeEach
    void setUp() {
        walletService = new WalletService(
                walletRepository, paymentIntentRepository,
                idempotencyStore,
                eventPublisher, notificationPort, paymentAnchorPort,
                kernelPaymentGatewayPort);
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

    @Test
    @DisplayName("initiatePayment for MTN_MOMO dispatches to the Kernel payment-gateway-controller and records the returned order id")
    void initiatePaymentDispatchesToKernelForMtnMomo() {
        Wallet wallet = Wallet.createNew(USER_ID, TENANT_ID, XAF);
        when(walletRepository.findByUserId(USER_ID, TENANT_ID)).thenReturn(Mono.just(wallet));
        when(idempotencyStore.getResult(anyString())).thenReturn(Mono.empty());
        when(idempotencyStore.tryAcquire(anyString(), any())).thenReturn(Mono.just(true));

        KernelPaymentOrderDto order = new KernelPaymentOrderDto(
                "kernel-order-1", TENANT_ID.toString(), null, null,
                new BigDecimal("5000.00"), "XAF", "MYCOOLPAY", "MOBILE_MONEY",
                "+237600000000", "PENDING", null, null, null, null);
        when(kernelPaymentGatewayPort.initiateOrder(
                eq("MYCOOLPAY"), eq("MOBILE_MONEY"), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(order));

        when(paymentIntentRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(walletRepository.saveTransaction(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(idempotencyStore.storeResult(anyString(), anyString(), any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any(
                com.yowyob.tiibntick.core.billing.wallet.domain.event.PaymentInitiated.class)))
                .thenReturn(Mono.empty());

        InitiatePaymentCommand cmd = new InitiatePaymentCommand(
                USER_ID, TENANT_ID, "INV-001", Money.ofXAF(5000), PaymentChannel.MTN_MOMO,
                "+237600000000", null, "test payment");

        StepVerifier.create(walletService.initiatePayment(cmd))
                .assertNext(intent -> assertThat(intent.getExternalRef()).isEqualTo("kernel-order-1"))
                .verifyComplete();

        verify(kernelPaymentGatewayPort).initiateOrder(
                eq("MYCOOLPAY"), eq("MOBILE_MONEY"), eq(new BigDecimal("5000.00")), eq("XAF"),
                eq("+237600000000"), any(), any(), any());
    }

    @Test
    @DisplayName("initiatePayment for CASH_ON_DELIVERY never calls the Kernel payment-gateway-controller")
    void initiatePaymentForCashOnDeliverySkipsKernelDispatch() {
        Wallet wallet = Wallet.createNew(USER_ID, TENANT_ID, XAF);
        when(walletRepository.findByUserId(USER_ID, TENANT_ID)).thenReturn(Mono.just(wallet));
        when(idempotencyStore.getResult(anyString())).thenReturn(Mono.empty());
        when(idempotencyStore.tryAcquire(anyString(), any())).thenReturn(Mono.just(true));
        when(paymentIntentRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(walletRepository.saveTransaction(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(idempotencyStore.storeResult(anyString(), anyString(), any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any(
                com.yowyob.tiibntick.core.billing.wallet.domain.event.PaymentInitiated.class)))
                .thenReturn(Mono.empty());

        InitiatePaymentCommand cmd = new InitiatePaymentCommand(
                USER_ID, TENANT_ID, "INV-002", Money.ofXAF(2000), PaymentChannel.CASH_ON_DELIVERY,
                null, null, "cash on delivery");

        StepVerifier.create(walletService.initiatePayment(cmd))
                .assertNext(intent -> assertThat(intent.getExternalRef()).isNull())
                .verifyComplete();

        verifyNoInteractions(kernelPaymentGatewayPort);
    }

    @Test
    @DisplayName("reconcilePendingProviderOrders confirms a payment intent whose Kernel order succeeded")
    void reconcilePendingProviderOrdersConfirmsSuccessfulOrder() {
        Wallet wallet = Wallet.createNew(USER_ID, TENANT_ID, XAF);
        PaymentIntent intent = PaymentIntent.builder()
                .id(PaymentIntentId.generate())
                .walletId(wallet.getId())
                .amount(Money.ofXAF(5000))
                .channel(PaymentChannel.MTN_MOMO)
                .status(PaymentIntentStatus.PENDING)
                .idempotencyKey("IDEMP-POLL")
                .externalRef("kernel-order-1")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(paymentIntentRepository.findAllPendingWithProviderReference()).thenReturn(Flux.just(intent));
        KernelPaymentOrderDto order = new KernelPaymentOrderDto(
                "kernel-order-1", null, null, null, null, null, null, null,
                null, "CONFIRMED", "PROV-REF-9", null, null, null);
        when(kernelPaymentGatewayPort.refreshOrder("kernel-order-1")).thenReturn(Mono.just(order));
        when(walletRepository.findById(wallet.getId())).thenReturn(Mono.just(wallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(Mono.just(wallet));
        when(paymentIntentRepository.save(any())).thenReturn(Mono.just(intent));
        when(idempotencyStore.release("IDEMP-POLL")).thenReturn(Mono.empty());
        when(eventPublisher.publish(any(
                com.yowyob.tiibntick.core.billing.wallet.domain.event.PaymentConfirmed.class)))
                .thenReturn(Mono.empty());
        when(notificationPort.sendPaymentConfirmed(any(), any())).thenReturn(Mono.empty());

        walletService.reconcilePendingProviderOrders();

        ArgumentCaptor<PaymentAnchorPayload> captor = ArgumentCaptor.forClass(PaymentAnchorPayload.class);
        verify(paymentAnchorPort).anchor(captor.capture());
        assertThat(captor.getValue().externalRef()).isEqualTo("PROV-REF-9");
    }

    @Test
    @DisplayName("reconcilePendingProviderOrders leaves a still-in-flight order pending (no confirmation, no failure)")
    void reconcilePendingProviderOrdersLeavesInFlightOrderPending() {
        Wallet wallet = Wallet.createNew(USER_ID, TENANT_ID, XAF);
        PaymentIntent intent = PaymentIntent.builder()
                .id(PaymentIntentId.generate())
                .walletId(wallet.getId())
                .amount(Money.ofXAF(5000))
                .channel(PaymentChannel.MTN_MOMO)
                .status(PaymentIntentStatus.PENDING)
                .idempotencyKey("IDEMP-POLL-2")
                .externalRef("kernel-order-2")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(paymentIntentRepository.findAllPendingWithProviderReference()).thenReturn(Flux.just(intent));
        KernelPaymentOrderDto order = new KernelPaymentOrderDto(
                "kernel-order-2", null, null, null, null, null, null, null,
                null, "AWAITING_PROVIDER", null, null, null, null);
        when(kernelPaymentGatewayPort.refreshOrder("kernel-order-2")).thenReturn(Mono.just(order));

        walletService.reconcilePendingProviderOrders();

        verify(paymentIntentRepository, never()).save(any());
        verifyNoInteractions(paymentAnchorPort, notificationPort);
    }

    @Test
    @DisplayName("splitMissionRevenue publishes a WalletSplitExecuted event so tnt-accounting-core "
            + "can post the journal entries (Audit n5 P-01: this used to never be published)")
    void splitMissionRevenuePublishesSplitExecutedEvent() {
        String freelancerOrgId = "org-42";
        String subDelivererId = "sub-7";
        Wallet orgWallet = Wallet.createForOrg(
                com.yowyob.tiibntick.core.billing.wallet.domain.enums.WalletOwnerType.FREELANCER_ORG,
                freelancerOrgId, TENANT_ID, XAF);
        Wallet subWallet = Wallet.createForOrg(
                com.yowyob.tiibntick.core.billing.wallet.domain.enums.WalletOwnerType.FREELANCER_ORG,
                subDelivererId, TENANT_ID, XAF);

        when(walletRepository.findByOwnerId(freelancerOrgId, TENANT_ID)).thenReturn(Mono.just(orgWallet));
        when(walletRepository.findByOwnerId(subDelivererId, TENANT_ID)).thenReturn(Mono.just(subWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publish(any(
                com.yowyob.tiibntick.core.billing.wallet.domain.event.WalletSplitExecuted.class)))
                .thenReturn(Mono.empty());

        com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.SplitMissionRevenueCommand cmd =
                new com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.SplitMissionRevenueCommand(
                        "MISSION-1", new BigDecimal("10000"), freelancerOrgId, TENANT_ID,
                        subDelivererId, 0.05, 0.20);

        StepVerifier.create(walletService.splitMissionRevenue(cmd))
                .expectNextMatches(result -> "COMPLETED".equals(result.status()))
                .verifyComplete();

        ArgumentCaptor<com.yowyob.tiibntick.core.billing.wallet.domain.event.WalletSplitExecuted> captor =
                ArgumentCaptor.forClass(com.yowyob.tiibntick.core.billing.wallet.domain.event.WalletSplitExecuted.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().missionId()).isEqualTo("MISSION-1");
        assertThat(captor.getValue().freelancerOrgId()).isEqualTo(freelancerOrgId);
        assertThat(captor.getValue().subDelivererId()).isEqualTo(subDelivererId);
        assertThat(captor.getValue().tenantId()).isEqualTo(TENANT_ID);
    }
}
