package com.yowyob.tiibntick.core.agency.org.adapter.out.delivery;

import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRegistryR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRelayHubR2dbcRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.AgencyOrganizationPort;
import com.yowyob.tiibntick.core.delivery.application.port.out.RelayOwnershipPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Agency side of the delivery thresholds (lot evt-l2, spec §5, QO-29):
 * <ul>
 *   <li>{@link AgencyOrganizationPort}: {@code Delivery.agencyId} is the id of the agency registry
 *       ({@code agency_org.agencies}); the thresholds are keyed on its Kernel organisation;</li>
 *   <li>{@link RelayOwnershipPort}: an agency relay hub takes the thresholds of its agency's organisation (its own
 *       {@code retention_delay_hours} is not used by the relay storage watch).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AgencyDeliveryThresholdsAdapter implements AgencyOrganizationPort, RelayOwnershipPort {

    private final AgencyRegistryR2dbcRepository agencies;
    private final AgencyRelayHubR2dbcRepository relayHubs;

    @Override
    public Mono<UUID> kernelOrganizationId(UUID agencyRegistryId) {
        return agencies.findById(agencyRegistryId)
                .flatMap(a -> Mono.justOrEmpty(a.getKernelOrganizationId()));
    }

    @Override
    public Mono<RelayOwnership> findByHubId(UUID hubId) {
        return relayHubs.findByCoreHubId(hubId).next()
                .flatMap(hub -> kernelOrganizationId(hub.getAgencyId())
                        .map(org -> new RelayOwnership(org, null)));
    }
}
