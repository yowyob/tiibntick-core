package com.yowyob.tiibntick.core.organization.application.port.in;

import com.yowyob.tiibntick.core.organization.domain.model.Branch;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import com.yowyob.tiibntick.core.organization.domain.vo.ServiceZone;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Primary inbound port — Branch management use cases.
 *
 * <p>Defines the contract exposed by the application layer for operations on the
 * {@link Branch} aggregate. Called by adapters (REST controllers, event consumers).
 *
 * @author MANFOUO Braun
 */
public interface ManageBranchUseCase {

    /**
     * Creates a new Branch under a given Agency, validating the Kernel organization
     * reference beforehand.
     *
     * @param organizationId Kernel organization UUID (must be active)
     * @param agencyId       Parent Agency's TiiBnTick internal ID
     * @param tenantId       Multi-tenant key
     * @param name           Branch name
     * @param address        Physical address (may be informal)
     * @param serviceZone    Optional geographic coverage zone
     * @return a {@link Mono} emitting the persisted {@link Branch}
     */
    Mono<Branch> createBranch(UUID organizationId,
                              OrganizationId agencyId,
                              UUID tenantId,
                              String name,
                              String address,
                              ServiceZone serviceZone);

    /**
     * Retrieves a Branch by its TiiBnTick internal ID.
     *
     * @param id the TiiBnTick branch ID
     * @return a {@link Mono} emitting the found Branch, or empty
     */
    Mono<Branch> findBranchById(OrganizationId id);

    /**
     * Returns all Branches belonging to a given Agency.
     *
     * @param agencyId the parent Agency's TiiBnTick internal ID
     * @return a {@link Flux} of Branches
     */
    Flux<Branch> findBranchesByAgency(OrganizationId agencyId);

    /**
     * Deactivates a Branch (e.g., temporary closure).
     *
     * @param id the TiiBnTick branch ID
     * @return a {@link Mono} emitting the updated Branch
     */
    Mono<Branch> deactivateBranch(OrganizationId id);
}
