package com.yowyob.tiibntick.core.trust.adapter.out.billing;

import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.BillingPolicyAnchorPayload;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordBillingPolicyUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BillingPolicyAnchorAdapter}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BillingPolicyAnchorAdapter — BillingPolicyAnchorPort implementation")
class BillingPolicyAnchorAdapterTest {

    @Mock
    private RecordBillingPolicyUseCase recordBillingPolicy;

    @Test
    @DisplayName("should delegate to RecordBillingPolicyUseCase with the payload's identifiers")
    void shouldDelegateToRecordBillingPolicyUseCase() {
        final BillingPolicyAnchorAdapter adapter = new BillingPolicyAnchorAdapter(recordBillingPolicy);

        final UUID tenantId = UUID.randomUUID();
        final UUID policyId = UUID.randomUUID();
        final BillingPolicyAnchorPayload payload = new BillingPolicyAnchorPayload(
                tenantId, policyId, "agency-42", "{\"name\":\"Standard\"}", Instant.now());

        when(recordBillingPolicy.record(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just("tx-hash-001"));

        StepVerifier.create(adapter.anchor(payload)).verifyComplete();

        final ArgumentCaptor<String> agencyIdCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> policyIdCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> tenantIdCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> summaryCaptor = ArgumentCaptor.forClass(String.class);
        verify(recordBillingPolicy).record(
                agencyIdCaptor.capture(), policyIdCaptor.capture(),
                tenantIdCaptor.capture(), summaryCaptor.capture());

        assertThat(agencyIdCaptor.getValue()).isEqualTo("agency-42");
        assertThat(policyIdCaptor.getValue()).isEqualTo(policyId.toString());
        assertThat(tenantIdCaptor.getValue()).isEqualTo(tenantId.toString());
        assertThat(summaryCaptor.getValue()).isEqualTo("{\"name\":\"Standard\"}");
    }
}
