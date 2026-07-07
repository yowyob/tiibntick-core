package com.yowyob.tiibntick.core.organization.application.port.out;

import com.yowyob.tiibntick.core.organization.domain.model.KernelOrganizationDto;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — Kernel Organization integration.
 *
 * <p>Defines the contract that the application layer uses to communicate with the
 * RT-comops-organization-core Kernel module. The adapter implementation
 * ({@link com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.kernel.KernelOrganizationAdapter})
 * uses a reactive WebClient to call the Kernel REST API.
 *
 * <p>This port is invoked <strong>before</strong> creating or updating any TiiBnTick
 * organizational aggregate (Agency, Branch, HubRelais) to ensure that the referenced
 * Kernel organization exists and is active.
 *
 * <p>Architecture note: this is a secondary port (driven port) in the hexagonal
 * architecture — it points outward from the domain toward the Kernel infrastructure.
 *
 * @author MANFOUO Braun
 */
public interface KernelOrganizationPort {

    /**
     * Fetches the Kernel Organization DTO for the given UUID.
     *
     * <p>Returns an empty {@link Mono} if the organization does not exist in the Kernel.
     *
     * @param organizationId the Kernel organization UUID to look up
     * @return a {@link Mono} emitting the {@link KernelOrganizationDto}, or empty if not found
     */
    Mono<KernelOrganizationDto> findById(UUID organizationId);

    /**
     * Checks whether a Kernel organization with the given UUID exists and is active.
     *
     * <p>This is a lightweight existence check — use it when the full DTO is not needed.
     *
     * @param organizationId the Kernel organization UUID to verify
     * @return a {@link Mono} emitting {@code true} if the organization exists and is active,
     *         {@code false} otherwise
     */
    Mono<Boolean> existsAndActive(UUID organizationId);
}
