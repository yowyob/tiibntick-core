package com.yowyob.tiibntick.core.administration.application.port.out;

import com.yowyob.tiibntick.core.administration.domain.model.KernelPermissionDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for communicating with the Yowyob Kernel permission catalog
 * (RT-comops-roles-core, yow_kernel_db) via the KernelBridge HTTP client.
 *
 * <p>Used to resolve {@code kernelPermissionId} for
 * {@link com.yowyob.tiibntick.core.administration.domain.model.TntPermissionEntry} entries.
 * The resolution is best-effort: TNT-exclusive permissions (tnt:blockchain:mine, etc.)
 * will not be found in the Kernel and their kernelPermissionId remains null.
 *
 * <p>This port must not be called from the domain layer. Only application services may use it.
 *
 * @author MANFOUO Braun
 */
public interface KernelPermissionPort {

    /**
     * Finds a Kernel permission by its UUID.
     *
     * @param kernelPermissionId the UUID of the permission in yow_kernel_db
     * @return the Kernel permission data, or empty if not found
     */
    Mono<KernelPermissionDto> findByPermissionId(UUID kernelPermissionId);

    /**
     * Finds a Kernel permission by its code.
     *
     * @param code the permission code (e.g., "delivery:read")
     * @return the Kernel permission data, or empty if the code is TNT-exclusive
     */
    Mono<KernelPermissionDto> findByCode(String code);

    /**
     * Lists all permissions available in the Kernel catalog.
     *
     * @return flux of Kernel permission DTOs
     */
    Flux<KernelPermissionDto> listAll();
}
