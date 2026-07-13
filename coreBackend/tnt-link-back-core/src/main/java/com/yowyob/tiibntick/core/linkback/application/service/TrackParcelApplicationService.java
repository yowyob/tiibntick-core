package com.yowyob.tiibntick.core.linkback.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.linkback.application.port.in.TrackParcelUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Thin orchestration over {@link DeliveryQueryUseCase} so the Link BFF has a single
 * Link-facing entry point for parcel tracking instead of calling tnt-delivery-core
 * directly (see coreBackend architecture: BFF talks only to its product's Core Backend).
 * No tracking logic is duplicated here.
 *
 * @author Dilane PAFE
 */
@Service
@RequiredArgsConstructor
public class TrackParcelApplicationService implements TrackParcelUseCase {

    private final DeliveryQueryUseCase deliveryQueryUseCase;

    @Override
    public Mono<Delivery> trackByCode(String trackingCode) {
        return deliveryQueryUseCase.findByTrackingCode(trackingCode);
    }
}
