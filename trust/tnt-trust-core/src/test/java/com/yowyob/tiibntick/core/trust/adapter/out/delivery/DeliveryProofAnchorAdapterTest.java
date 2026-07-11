package com.yowyob.tiibntick.core.trust.adapter.out.delivery;

import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryProofAnchorPayload;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordDeliveryProofUseCase;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeliveryProofAnchorAdapter}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryProofAnchorAdapter — DeliveryProofAnchorPort implementation")
class DeliveryProofAnchorAdapterTest {

    @Mock
    private RecordDeliveryProofUseCase recordDeliveryProof;

    @Test
    @DisplayName("should map the delivery-owned payload into a DeliveryProofRecord and record it")
    void shouldMapPayloadAndRecordProof() {
        final DeliveryProofAnchorAdapter adapter = new DeliveryProofAnchorAdapter(recordDeliveryProof);

        final UUID tenantId = UUID.randomUUID();
        final UUID deliveryId = UUID.randomUUID();
        final UUID packageId = UUID.randomUUID();
        final UUID actorId = UUID.randomUUID();
        final DeliveryProofAnchorPayload payload = new DeliveryProofAnchorPayload(
                tenantId, deliveryId, packageId, actorId,
                "a".repeat(64), "b".repeat(64), 3.848, 11.502, Instant.now());

        when(recordDeliveryProof.record(any())).thenReturn(Mono.just("tx-hash-001"));

        StepVerifier.create(adapter.anchor(payload)).verifyComplete();

        final ArgumentCaptor<DeliveryProofRecord> captor = ArgumentCaptor.forClass(DeliveryProofRecord.class);
        verify(recordDeliveryProof).record(captor.capture());
        final DeliveryProofRecord proof = captor.getValue();

        assertThat(proof.getMissionId()).isEqualTo(deliveryId.toString());
        assertThat(proof.getPackageId()).isEqualTo(packageId.toString());
        assertThat(proof.getActorId()).isEqualTo(actorId.toString());
        assertThat(proof.getTenantId()).isEqualTo(tenantId.toString());
        assertThat(proof.getPhotoHash()).isEqualTo("a".repeat(64));
        assertThat(proof.getGpsLat()).isEqualTo(3.848);
        assertThat(proof.getGpsLng()).isEqualTo(11.502);
    }
}
