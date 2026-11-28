package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.PayCompensationCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ICompensationEventPublisher;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IKernelSettlementPort;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletCompensationRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Compensation;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.CompensationRequest;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Fund;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletDto;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletCompensation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Lot L5 (spec §4.3): paying a dispute compensation from the Kernel fund. */
class CompensationServiceTest {

    private final IKernelSettlementPort kernel = mock(IKernelSettlementPort.class);
    private final IWalletCompensationRepository mirrors = mock(IWalletCompensationRepository.class);
    private final ICompensationEventPublisher events = mock(ICompensationEventPublisher.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ReactiveTransactionManager> noTx = mock(ObjectProvider.class);
    private CompensationService service;

    private final UUID tenant = UUID.randomUUID();
    private final UUID beneficiary = UUID.randomUUID();
    private final UUID wallet = UUID.randomUUID();
    private final PayCompensationCommand cmd = new PayCompensationCommand(tenant, "d-1", beneficiary,
            new BigDecimal("15000"), "XAF");

    @BeforeEach
    void setUp() {
        service = new CompensationService(kernel, mirrors, events, noTx);
        when(mirrors.findByDisputeId("d-1")).thenReturn(Mono.empty());
        when(mirrors.insert(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(events.compensationPaid(any())).thenReturn(Mono.empty());
        when(kernel.walletOfOwner(beneficiary)).thenReturn(Mono.just(
                new KernelWalletDto(wallet, beneficiary, "USER", "Awa", BigDecimal.ZERO, null, null)));
    }

    @Test
    @DisplayName("pays with reference DISPUTE:<id>, records the mirror and publishes compensation.paid")
    void pays() {
        UUID fundWallet = UUID.randomUUID();
        UUID paid = UUID.randomUUID();
        when(kernel.compensationFund()).thenReturn(Mono.just(new Fund(fundWallet, new BigDecimal("100000"))));
        when(kernel.compensate(any())).thenReturn(Mono.just(new Compensation(paid, "DISPUTE:d-1", fundWallet, wallet,
                new BigDecimal("15000"), Instant.now())));

        WalletCompensation c = service.payDisputeCompensation(cmd).block();

        assertThat(c.kernelCompensationId()).isEqualTo(paid);
        assertThat(c.beneficiaryWalletId()).isEqualTo(wallet);
        assertThat(c.fundWalletId()).isEqualTo(fundWallet);
        ArgumentCaptor<CompensationRequest> sent = ArgumentCaptor.forClass(CompensationRequest.class);
        verify(kernel).compensate(sent.capture());
        assertThat(sent.getValue().reference()).isEqualTo("DISPUTE:d-1");
        assertThat(sent.getValue().beneficiaryWalletId()).isEqualTo(wallet);
        verify(mirrors).insert(any());
        verify(events).compensationPaid(c);
    }

    @Test
    @DisplayName("a dispute already paid returns its mirror; the Kernel is not called")
    void idempotent() {
        WalletCompensation existing = new WalletCompensation(UUID.randomUUID(), tenant, "d-1", UUID.randomUUID(),
                null, beneficiary, wallet, new BigDecimal("15000"), "XAF", Instant.now());
        when(mirrors.findByDisputeId("d-1")).thenReturn(Mono.just(existing));
        assertThat(service.payDisputeCompensation(cmd).block()).isSameAs(existing);
        verifyNoInteractions(kernel, events);
    }

    @Test
    @DisplayName("fund too low: 409 COMPENSATION_FUND_INSUFFICIENT, nothing paid nor recorded")
    void fundInsufficient() {
        when(kernel.compensationFund()).thenReturn(Mono.just(new Fund(UUID.randomUUID(), new BigDecimal("1000"))));
        StepVerifier.create(service.payDisputeCompensation(cmd))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(WalletSettlementException.class);
                    assertThat(((WalletSettlementException) e).getCode()).isEqualTo("COMPENSATION_FUND_INSUFFICIENT");
                    assertThat(((WalletSettlementException) e).getHttpStatus()).isEqualTo(409);
                }).verify();
        verify(kernel, never()).compensate(any());
        verify(mirrors, never()).insert(any());
    }

    @Test
    @DisplayName("no wallet for the beneficiary: 409; Kernel refusal: 409; Kernel down: 503")
    void failures() {
        when(kernel.walletOfOwner(beneficiary)).thenReturn(Mono.empty());
        StepVerifier.create(service.payDisputeCompensation(cmd))
                .expectErrorSatisfies(e -> assertThat(((WalletSettlementException) e).getCode())
                        .isEqualTo("BENEFICIARY_WALLET_MISSING")).verify();

        when(kernel.walletOfOwner(beneficiary)).thenReturn(Mono.just(
                new KernelWalletDto(wallet, beneficiary, "USER", "Awa", BigDecimal.ZERO, null, null)));
        when(kernel.compensationFund()).thenReturn(Mono.just(new Fund(UUID.randomUUID(), new BigDecimal("100000"))));
        when(kernel.compensate(any())).thenReturn(Mono.error(WebClientResponseException.create(
                HttpStatus.UNPROCESSABLE_ENTITY.value(), "x", null, "{\"message\":\"no\"}".getBytes(), null)));
        StepVerifier.create(service.payDisputeCompensation(cmd))
                .expectErrorSatisfies(e -> {
                    assertThat(((WalletSettlementException) e).getCode()).isEqualTo("KERNEL_PAYMENT_REFUSED");
                    assertThat(((WalletSettlementException) e).getHttpStatus()).isEqualTo(409);
                }).verify();

        when(kernel.compensationFund()).thenReturn(Mono.error(new IllegalStateException("connection refused")));
        StepVerifier.create(service.payDisputeCompensation(cmd))
                .expectErrorSatisfies(e -> assertThat(((WalletSettlementException) e).getHttpStatus()).isEqualTo(503))
                .verify();
        verify(mirrors, never()).insert(any());
    }
}
