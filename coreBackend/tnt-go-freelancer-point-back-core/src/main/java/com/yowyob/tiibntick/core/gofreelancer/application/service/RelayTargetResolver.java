package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Adds to a delivery the relay point its sender chose, as its public view, so the delivery person
 * knows where to deposit the parcel and the sender sees where it is. A lookup failure leaves the
 * delivery as it is: the relay point is information, never a reason to fail a read.
 */
@Service
@RequiredArgsConstructor
public class RelayTargetResolver {

    private final IDeliveryNeedRepository deliveryNeeds;
    private final GofpRelayPointRepository relayPoints;
    private final RelayPointDirectoryService directory;

    public Mono<DeliveryResponseDTO> withTarget(DeliveryResponseDTO dto) {
        if (dto.getDeliveryNeedId() == null) {
            return Mono.just(dto);
        }
        return deliveryNeeds.findById(dto.getDeliveryNeedId())
                .filter(need -> need.getTargetRelayPointId() != null)
                .flatMap(need -> {
                    dto.setTargetRelayPointId(need.getTargetRelayPointId());
                    return relayPoints.findById(need.getTargetRelayPointId())
                            .switchIfEmpty(relayPoints.findByCoreRelayPointId(need.getTargetRelayPointId()))
                            .flatMap(directory::publicView)
                            .doOnNext(dto::setTargetRelayPoint);
                })
                .thenReturn(dto)
                .onErrorReturn(dto);
    }
}
