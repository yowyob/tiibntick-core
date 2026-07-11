package com.yowyob.tiibntick.core.trust.adapter.out.delivery;

import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryProofAnchorPayload;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryProofAnchorPort;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordDeliveryProofUseCase;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * tnt-trust-core implementation of {@link DeliveryProofAnchorPort} (outbound port
 * owned by tnt-delivery-core).
 *
 * <p>tnt-trust-core depends on tnt-delivery-core (one-directional, no Maven cycle —
 * delivery never depends back on trust) purely to see this port and its payload type;
 * it maps the delivery-owned {@link DeliveryProofAnchorPayload} into its own
 * {@link DeliveryProofRecord} and delegates to {@link RecordDeliveryProofUseCase}.
 *
 * @author MANFOUO Braun
 * @see DeliveryProofAnchorPort
 */
@Component
@RequiredArgsConstructor
public class DeliveryProofAnchorAdapter implements DeliveryProofAnchorPort {

    private final RecordDeliveryProofUseCase recordDeliveryProof;

    @Override
    public Mono<Void> anchor(DeliveryProofAnchorPayload payload) {
        final DeliveryProofRecord proof = new DeliveryProofRecord(
                payload.deliveryId().toString(),
                payload.deliveryId().toString(),
                payload.packageId().toString(),
                payload.actorId().toString(),
                payload.tenantId().toString(),
                payload.photoHash(),
                payload.signatureHash(),
                payload.gpsLat(),
                payload.gpsLng(),
                LocalDateTime.ofInstant(payload.confirmedAt(), ZoneOffset.UTC));
        return recordDeliveryProof.record(proof).then();
    }
}
