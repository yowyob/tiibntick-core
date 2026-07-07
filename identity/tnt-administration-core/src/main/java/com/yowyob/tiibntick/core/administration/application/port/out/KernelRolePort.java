package com.yowyob.tiibntick.core.administration.application.port.out;

import com.yowyob.tiibntick.core.administration.domain.model.KernelRoleDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for communicating with the Yowyob Kernel role service
 * (RT-comops-roles-core, yow_kernel_db) via the KernelBridge HTTP client.
 *
 * <p>Used during role provisioning to:
 * <ul>
 *   <li>Validate that the Kernel has been notified and the role exists.</li>
 *   <li>Retrieve the Kernel-assigned UUID to store as {@code kernelRoleId} in
 *       {@link com.yowyob.tiibntick.core.administration.domain.model.TntRoleDefinition}.</li>
 * </ul>
 *
 * <p>This port must not be called from the domain layer. Only application services may use it.
 *
 * @author MANFOUO Braun
 */
public interface KernelRolePort {

    /**
     * Finds a Kernel role by its UUID.
     *
     * @param kernelRoleId the UUID of the role in yow_kernel_db
     * @return the Kernel role data, or empty if not found
     */
    Mono<KernelRoleDto> findByRoleId(UUID kernelRoleId);

    /**
     * Finds a Kernel role by its code and tenant UUID.
     *
     * @param code     the role code (e.g., "TNT_DISPATCHER")
     * @param tenantId the tenant UUID
     * @return the Kernel role data, or empty if not found
     */
    Mono<KernelRoleDto> findByCodeAndTenant(String code, UUID tenantId);

    /**
     * Checks whether a role with the given code already exists for a tenant in the Kernel.
     *
     * @param code     the role code
     * @param tenantId the tenant UUID
     * @return true if the role exists, false otherwise
     */
    Mono<Boolean> existsByCodeAndTenant(String code, UUID tenantId);

    /**
     * Lists all roles provisioned for a tenant in the Kernel.
     *
     * @param tenantId the tenant UUID
     * @return flux of Kernel role data for the tenant
     */
    Flux<KernelRoleDto> findAllByTenant(UUID tenantId);
}
