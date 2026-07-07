package com.yowyob.tiibntick.core.organization.application.service;

import com.yowyob.tiibntick.core.organization.application.port.in.ManageAgencyUseCase;
import com.yowyob.tiibntick.core.organization.application.port.out.AgencyRepositoryPort;
import com.yowyob.tiibntick.core.organization.application.port.out.KernelOrganizationPort;
import com.yowyob.tiibntick.core.organization.domain.model.Agency;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service implementing {@link ManageAgencyUseCase}.
 *
 * <p>Orchestrates Agency aggregate lifecycle:
 * <ol>
 *   <li>Validates the Kernel organization reference via {@link KernelOrganizationPort}.</li>
 *   <li>Delegates persistence to {@link AgencyRepositoryPort}.</li>
 * </ol>
 *
 * <p>This class is deliberately <strong>not</strong> annotated with {@code @Service} to
 * remain framework-agnostic. Spring wiring is done in
 * {@link com.yowyob.tiibntick.core.organization.config.OrganizationCoreAutoConfiguration}.
 *
 * <h3>Security ( — tnt-roles-core integration)</h3>
 * <p>Write operations require {@code agency:write} or {@code agency:manage};
 * read operations require {@code agency:read}. Enforcement is declarative via
 * {@code @RequirePermission} from {@code tnt-roles-core}'s AOP aspect.
 *
 * @author MANFOUO Braun
 */
public class AgencyService implements ManageAgencyUseCase {

    private final AgencyRepositoryPort agencyRepository;
    private final KernelOrganizationPort kernelOrganizationPort;

    /**
     * Constructor injection — no field injection used to keep the class testable
     * without a Spring context.
     *
     * @param agencyRepository       persistence port for Agency aggregates
     * @param kernelOrganizationPort outbound Kernel integration port
     */
    public AgencyService(AgencyRepositoryPort agencyRepository,
                         KernelOrganizationPort kernelOrganizationPort) {
        this.agencyRepository = agencyRepository;
        this.kernelOrganizationPort = kernelOrganizationPort;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Validates that the referenced Kernel organization exists and is active before
     * creating the Agency. Throws an {@link IllegalArgumentException} wrapped in the
     * reactive pipeline if the Kernel organization is not found or inactive.
     * Requires permission: {@code agency:write}.
     */
    @Override
    @RequirePermission(resource = "agency", action = "write")
    public Mono<Agency> createAgency(UUID organizationId,
                                     UUID tenantId,
                                     String name,
                                     String commerceRegistryNumber,
                                     String primaryCurrency) {
        return kernelOrganizationPort.existsAndActive(organizationId)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Kernel organization not found or inactive: " + organizationId));
                    }
                    Agency agency = Agency.create(
                            organizationId, tenantId, name, commerceRegistryNumber, primaryCurrency);
                    return agencyRepository.save(agency);
                });
    }

    /**
     * {@inheritDoc}
     * Requires permission: {@code agency:read}.
     */
    @Override
    @RequirePermission(resource = "agency", action = "read")
    public Mono<Agency> findAgencyById(OrganizationId id) {
        return agencyRepository.findById(id);
    }

    /**
     * {@inheritDoc}
     * Requires permission: {@code agency:read}.
     */
    @Override
    @RequirePermission(resource = "agency", action = "read")
    public Flux<Agency> findAgenciesByOrganizationId(UUID organizationId) {
        return agencyRepository.findByOrganizationId(organizationId);
    }

    /**
     * {@inheritDoc}
     * Requires permission: {@code agency:read}.
     */
    @Override
    @RequirePermission(resource = "agency", action = "read")
    public Flux<Agency> listAgenciesForTenant(UUID tenantId) {
        return agencyRepository.findAllByTenantId(tenantId);
    }
}
