package com.yowyob.tiibntick.core.trust.adapter.out.wallet;

import com.yowyob.tiibntick.core.billing.wallet.application.port.out.PaymentAnchorPayload;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordPaymentUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentAnchorAdapter}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentAnchorAdapter — IPaymentAnchorPort implementation")
class PaymentAnchorAdapterTest {

    @Mock
    private RecordPaymentUseCase recordPayment;

    @Test
    @DisplayName("should delegate to RecordPaymentUseCase with the payload's identifiers")
    void shouldDelegateToRecordPaymentUseCase() {
        final PaymentAnchorAdapter adapter = new PaymentAnchorAdapter(recordPayment);

        final UUID tenantId = UUID.randomUUID();
        final UUID paymentIntentId = UUID.randomUUID();
        final UUID walletId = UUID.randomUUID();
        final UUID userId = UUID.randomUUID();
        final PaymentAnchorPayload payload = new PaymentAnchorPayload(
                tenantId, paymentIntentId, walletId, userId, "INV-001",
                new BigDecimal("5000.00"), "XAF", "MTN_MOMO", "FIN-TX-001", Instant.now());

        when(recordPayment.record(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just("tx-hash-001"));

        StepVerifier.create(adapter.anchor(payload)).verifyComplete();

        final ArgumentCaptor<String> paymentIntentIdCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> walletIdCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> tenantIdCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> externalRefCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> amountCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> currencyCaptor = ArgumentCaptor.forClass(String.class);
        verify(recordPayment).record(
                paymentIntentIdCaptor.capture(), walletIdCaptor.capture(), actorIdCaptor.capture(),
                tenantIdCaptor.capture(), channelCaptor.capture(), externalRefCaptor.capture(),
                amountCaptor.capture(), currencyCaptor.capture());

        assertThat(paymentIntentIdCaptor.getValue()).isEqualTo(paymentIntentId.toString());
        assertThat(walletIdCaptor.getValue()).isEqualTo(walletId.toString());
        assertThat(actorIdCaptor.getValue()).isEqualTo(userId.toString());
        assertThat(tenantIdCaptor.getValue()).isEqualTo(tenantId.toString());
        assertThat(channelCaptor.getValue()).isEqualTo("MTN_MOMO");
        assertThat(externalRefCaptor.getValue()).isEqualTo("FIN-TX-001");
        assertThat(amountCaptor.getValue()).isEqualTo("5000.00");
        assertThat(currencyCaptor.getValue()).isEqualTo("XAF");
    }
}
