package com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery;

import com.yowyob.tiibntick.core.delivery.application.port.out.RelayOwnershipPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * {@link RelayOwnershipPort} for the Go-Freelancer relays (lot evt-l2, QO-29, D-130): a GOFP relay is independent
 * (no owning organisation); its own storage, when set, applies within the platform bounds.
 */
@Component
@RequiredArgsConstructor
public class GofpRelayOwnershipAdapter implements RelayOwnershipPort {

    private final GofpRelayPointRepository relayPoints;

    @Override
    public Mono<RelayOwnership> findByHubId(UUID hubId) {
        return relayPoints.findByCoreRelayPointId(hubId)
                .map(rp -> new RelayOwnership(null, rp.getStorageDays()));
    }
}
