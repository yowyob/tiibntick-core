package com.yowyob.tiibntick.core.administration.adapter.out.organization;

import com.yowyob.tiibntick.core.administration.application.port.out.AgencyOrganizationPort;
import com.yowyob.tiibntick.core.organization.application.port.out.AgencyRepositoryPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * An organization is a TiiBnTick Agency when organization-core holds an {@code Agency} for that Kernel organization
 * id. The Agency platform registers each agency there with its {@code kernelOrganizationId}
 * ({@code AgencyRegistryService.syncPlatformCore} → {@code POST /api/v1/tenants/{tenantId}/agencies}).
 */
@Component
public class AgencyOrganizationAdapter implements AgencyOrganizationPort {

    private final AgencyRepositoryPort agencies;

    public AgencyOrganizationAdapter(AgencyRepositoryPort agencies) {
        this.agencies = agencies;
    }

    @Override
    public Mono<Boolean> isAgency(UUID organizationId) {
        if (organizationId == null) {
            return Mono.just(false);
        }
        return agencies.findByOrganizationId(organizationId).hasElements();
    }
}
