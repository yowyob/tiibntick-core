package com.yowyob.tiibntick.core.organization.application.service;

import com.yowyob.tiibntick.core.organization.application.port.in.ManageBranchUseCase;
import com.yowyob.tiibntick.core.organization.application.port.out.BranchRepositoryPort;
import com.yowyob.tiibntick.core.organization.application.port.out.KernelOrganizationPort;
import com.yowyob.tiibntick.core.organization.domain.model.Branch;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import com.yowyob.tiibntick.core.organization.domain.vo.ServiceZone;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service implementing {@link ManageBranchUseCase}.
 *
 * <p>Orchestrates Branch aggregate lifecycle:
 * <ol>
 *   <li>Validates the Kernel organization reference via {@link KernelOrganizationPort}.</li>
 *   <li>Delegates persistence to {@link BranchRepositoryPort}.</li>
 * </ol>
 *
 * <p>No {@code @Service} annotation — Spring wiring is done in
 * {@link com.yowyob.tiibntick.core.organization.config.OrganizationCoreAutoConfiguration}.
 *
 * <h3>Security ( — tnt-roles-core integration)</h3>
 * <p>Write/manage operations require {@code branch:write} or {@code branch:manage};
 * read operations require {@code branch:read}. Enforcement is declarative via
 * {@code @RequirePermission} from {@code tnt-roles-core}'s AOP aspect.
 *
 * @author MANFOUO Braun
 */
public class BranchService implements ManageBranchUseCase {

    private final BranchRepositoryPort branchRepository;
    private final KernelOrganizationPort kernelOrganizationPort;

    /**
     * Constructor injection.
     *
     * @param branchRepository       persistence port for Branch aggregates
     * @param kernelOrganizationPort outbound Kernel integration port
     */
    public BranchService(BranchRepositoryPort branchRepository,
                         KernelOrganizationPort kernelOrganizationPort) {
        this.branchRepository = branchRepository;
        this.kernelOrganizationPort = kernelOrganizationPort;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Validates Kernel organization existence before creating the Branch.
     * Requires permission: {@code branch:write}.
     */
    @Override
    @RequirePermission(resource = "branch", action = "write")
    public Mono<Branch> createBranch(UUID organizationId,
                                     OrganizationId agencyId,
                                     UUID tenantId,
                                     String name,
                                     String address,
                                     ServiceZone serviceZone) {
        return kernelOrganizationPort.existsAndActive(organizationId)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Kernel organization not found or inactive: " + organizationId));
                    }
                    Branch branch = Branch.create(
                            organizationId, agencyId, tenantId, name, address, serviceZone);
                    return branchRepository.save(branch);
                });
    }

    /**
     * {@inheritDoc}
     * Requires permission: {@code branch:read}.
     */
    @Override
    @RequirePermission(resource = "branch", action = "read")
    public Mono<Branch> findBranchById(OrganizationId id) {
        return branchRepository.findById(id);
    }

    /**
     * {@inheritDoc}
     * Requires permission: {@code branch:read}.
     */
    @Override
    @RequirePermission(resource = "branch", action = "read")
    public Flux<Branch> findBranchesByAgency(OrganizationId agencyId) {
        return branchRepository.findByAgencyId(agencyId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Fetches the branch, calls {@link Branch#deactivate()}, and persists.
     * Requires permission: {@code branch:manage}.
     */
    @Override
    @RequirePermission(resource = "branch", action = "manage")
    public Mono<Branch> deactivateBranch(OrganizationId id) {
        return branchRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found: " + id)))
                .flatMap(branch -> {
                    branch.deactivate();
                    return branchRepository.save(branch);
                });
    }
}
